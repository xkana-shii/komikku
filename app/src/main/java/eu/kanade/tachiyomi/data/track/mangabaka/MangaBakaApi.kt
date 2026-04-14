package eu.kanade.tachiyomi.data.track.mangabaka

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItemResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaListEntry
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaListResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaSearchResult
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaUserProfileResponse
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import eu.kanade.tachiyomi.util.lang.htmlDecode
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.math.RoundingMode
import java.util.Collections
import java.util.Locale
import tachiyomi.domain.track.model.Track as DomainTrack

class MangaBakaApi(
    private val trackId: Long,
    baseClient: OkHttpClient,
    interceptor: MangaBakaInterceptor,
) {

    private val json: Json by injectLazy()

    private val client = baseClient.newBuilder().addInterceptor {
        it.request().newBuilder()
            .header(
                "User-Agent",
                buildString {
                    append("${MR.strings.app_name}/v${BuildConfig.VERSION_NAME} ")
                    append("(${BuildConfig.APPLICATION_ID} ${BuildConfig.COMMIT_SHA}) ")
                    append("(Android) (https://github.com/xkana-shii/komikku)")
                },
            )
            .build()
            .let(it::proceed)
    }.build()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track, knownSeriesData: MangaBakaItem? = null, numberOfRereads: Int = 0): Track {
        return withIOContext {
            val seriesData = knownSeriesData ?: fetchSeriesData(track.remote_id)
            val resolvedId = seriesData.mergedWith ?: seriesData.id
            track.remote_id = resolvedId
            val body = buildJsonObject {
                put("is_private", track.private)
                put("state", track.toApiStatus())
                if (track.last_chapter_read > 0.0) {
                    put("progress_chapter", track.last_chapter_read)
                }
                if (track.score > 0) {
                    put("rating", track.score.toInt().coerceIn(0, 100))
                }
                if (track.started_reading_date > 0) {
                    put("start_date", track.started_reading_date.toLocalDate().toString())
                }
                if (track.finished_reading_date > 0) {
                    put("finish_date", track.finished_reading_date.toLocalDate().toString())
                }
                if (numberOfRereads > 0) {
                    put("number_of_rereads", numberOfRereads)
                }
            }
                .toString()
                .toRequestBody()

            authClient
                .newCall(POST("$LIBRARY_API_URL/$resolvedId", body = body, headers = headersOf("Content-Type", APP_JSON)))
                .awaitSuccess()
            libraryCache.remove(resolvedId)
            seriesCache.remove(resolvedId)

            // only returns 201 with the body { "status": 201, "data": true }, so no library ID for us
            track.title = seriesData.title
            track.total_chapters = seriesData.totalChapters?.toLongOrNull() ?: 0
            track
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        withIOContext {
            val resolvedId = resolveId(track.remoteId)
            authClient
                .newCall(DELETE("$LIBRARY_API_URL/$resolvedId"))
                .awaitSuccess()
            libraryCache.remove(resolvedId)
            seriesCache.remove(resolvedId)
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withIOContext {
            try {
                val originalId = track.remote_id

                val (userData, seriesResult) = coroutineScope {
                    async { fetchLibraryEntry(originalId) } to async { runCatching { fetchSeriesData(originalId) } }
                }.let { (a, b) -> a.await() to b.await() }

                val seriesData = seriesResult.getOrElse { e ->
                    if (e is HttpException && e.code == 404) fetchSeriesData(resolveId(originalId)) else throw e
                }

                val resolvedId = seriesData.mergedWith ?: seriesData.id

                if (userData == null) return@withIOContext null

                val finalEntry = if (resolvedId != originalId) {
                    val resolvedEntry = fetchLibraryEntry(resolvedId)
                    val mergedEntry = userData.mergeWithResolved(resolvedEntry)
                    val mergedTrack = mergedEntry.toTrack(resolvedId, seriesData)
                    if (resolvedEntry == null) {
                        addLibManga(mergedTrack, knownSeriesData = seriesData, numberOfRereads = mergedEntry.numberOfRereads ?: 0)
                    } else {
                        updateLibManga(mergedTrack, knownSeriesData = seriesData, knownEntry = mergedEntry)
                    }
                    mergedEntry
                } else {
                    userData
                }

                finalEntry.toTrack(resolvedId, seriesData)
            } catch (e: HttpException) {
                if (e.code == 404) null else throw e
            }
        }
    }

    suspend fun updateLibManga(track: Track, knownSeriesData: MangaBakaItem? = null, knownEntry: MangaBakaListEntry? = null): Track {
        return withIOContext {
            val originalId = track.remote_id

            val (seriesData, entry) = if (knownSeriesData != null && knownEntry != null) {
                knownSeriesData to knownEntry
            } else {
                coroutineScope {
                    val seriesDeferred = if (knownSeriesData != null) null else async { fetchSeriesData(originalId) }
                    val entryDeferred = if (knownEntry != null) null else async { runCatching { fetchLibraryEntry(originalId) }.getOrNull() }
                    (seriesDeferred?.await() ?: knownSeriesData!!) to (entryDeferred?.await() ?: knownEntry)
                }
            }

            track.remote_id = seriesData.mergedWith ?: seriesData.id

            val nextRereads = if (track.toApiStatus() == "completed" && entry?.state == "rereading") {
                (entry.numberOfRereads ?: 0) + 1
            } else {
                entry?.numberOfRereads
            }

            val body = buildJsonObject {
                put("state", track.toApiStatus())
                put("is_private", track.private)
                if (track.last_chapter_read > 0.0) {
                    put("progress_chapter", track.last_chapter_read)
                } else {
                    put("progress_chapter", null)
                }
                if (track.score > 0) {
                    put("rating", track.score.toInt().coerceIn(0, 100))
                } else {
                    put("rating", null)
                }
                if (track.started_reading_date > 0) {
                    put("start_date", track.started_reading_date.toLocalDate().toString())
                } else {
                    put("start_date", null)
                }
                if (track.finished_reading_date > 0) {
                    put("finish_date", track.finished_reading_date.toLocalDate().toString())
                } else {
                    put("finish_date", null)
                }
                if (nextRereads != null) {
                    put("number_of_rereads", nextRereads)
                }
            }
                .toString()
                .toRequestBody()

            authClient
                .newCall(PUT("$LIBRARY_API_URL/${track.remote_id}", body = body, headers = headersOf("Content-Type", APP_JSON)))
                .awaitSuccess()

            libraryCache.remove(track.remote_id)
            seriesCache.remove(track.remote_id)

            track.title = seriesData.title
            track.total_chapters = seriesData.totalChapters?.toLongOrNull() ?: 0
            track
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val url = "$API_BASE_URL/v1/series/search".toUri().buildUpon()
                .appendQueryParameter("q", search)
                .appendQueryParameter("type_not", "novel")
                .build()
            with(json) {
                client.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MangaBakaSearchResult>()
                    .data
                    .filter { it.state != "merged" }
                    .map { parseSearchItem(it) }
            }
        }
    }

    private fun parseSearchItem(item: MangaBakaItem): TrackSearch {
        return TrackSearch.create(trackId).apply {
            remote_id = item.mergedWith ?: item.id
            title = item.title
            summary = item.description.orEmpty().htmlDecode().trim()
            score = item.rating?.toBigDecimal()?.setScale(2, RoundingMode.HALF_UP)?.toDouble() ?: -1.0
            cover_url = item.cover.x350.x3.orEmpty()
            tracking_url = "$BASE_URL/${item.mergedWith ?: item.id}"
            total_chapters = item.totalChapters?.toLongOrNull() ?: 0
            start_date = item.published.startDate.orEmpty()
            publishing_status = item.status
            publishing_type = item.type.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
            }
            authors = item.authors.orEmpty()
            artists = item.artists.orEmpty()
        }
    }

    suspend fun getScoreStepSize(): Int {
        return withIOContext {
            with(json) {
                authClient.newCall(GET("$API_BASE_URL/v1/my/profile"))
                    .awaitSuccess()
                    .parseAs<MangaBakaUserProfileResponse>()
                    .data
                    .ratingSteps
            }
        }
    }

    suspend fun getAccessToken(code: String): MangaBakaOAuth {
        return withIOContext {
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("code_challenge_method", "S256")
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .add("scope", SCOPES)
                .build()

            with(json) {
                client.newCall(POST("${OAUTH_URL}/token", body = formBody))
                    .awaitSuccess().parseAs()
            }
        }
    }

    private data class CacheEntry<T>(val value: T, val cachedAt: Long = System.currentTimeMillis()) {
        fun isExpired() = System.currentTimeMillis() - cachedAt > CACHE_TTL_MS
    }

    private fun <K, V> lruCache(maxSize: Int): MutableMap<K, V> =
        Collections.synchronizedMap(
            object : LinkedHashMap<K, V>(maxSize + 1, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<K, V>) = size > maxSize
            },
        )

    private val seriesCache = lruCache<Long, CacheEntry<MangaBakaItem>>(MAX_CACHE_SIZE)
    private val libraryCache = lruCache<Long, CacheEntry<MangaBakaListEntry?>>(MAX_CACHE_SIZE)

    suspend fun fetchSeriesData(seriesId: Long): MangaBakaItem {
        return withIOContext {
            seriesCache[seriesId]?.takeUnless { it.isExpired() }?.value ?: run {
                val data = with(json) {
                    authClient.newCall(GET("$API_BASE_URL/v1/series/$seriesId"))
                        .awaitSuccess()
                        .parseAs<MangaBakaItemResult>()
                        .data
                }
                seriesCache[seriesId] = CacheEntry(data)
                data
            }
        }
    }

    private suspend fun fetchLibraryEntry(seriesId: Long): MangaBakaListEntry? {
        return withIOContext {
            libraryCache[seriesId]?.takeUnless { it.isExpired() }?.value ?: run {
                val entry = with(json) {
                    try {
                        authClient.newCall(GET("$LIBRARY_API_URL/$seriesId"))
                            .awaitSuccess()
                            .parseAs<MangaBakaListResult>()
                            .data
                    } catch (e: HttpException) {
                        if (e.code == 404) null else throw e
                    }
                }
                libraryCache[seriesId] = CacheEntry(entry)
                entry
            }
        }
    }

    suspend fun resolveId(seriesId: Long): Long {
        val item = fetchSeriesData(seriesId)
        return item.mergedWith ?: item.id
    }

    suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        return withIOContext {
            fetchSeriesData(track.remoteId).let {
                TrackMangaMetadata(
                    remoteId = it.mergedWith ?: it.id,
                    title = it.title,
                    thumbnailUrl = it.cover.raw.url,
                    description = it.description.orEmpty().htmlDecode().trim().ifEmpty { null },
                    authors = it.authors?.joinToString(", ")?.ifEmpty { null },
                    artists = it.artists?.joinToString(", ")?.ifEmpty { null },
                )
            }
        }
    }

    companion object {
        private const val CLIENT_ID = "wOWYtfnAMjnornECeqIclcxOdUayYGqA"

        internal const val BASE_URL = "https://mangabaka.org"
        private const val API_BASE_URL = "https://api.mangabaka.dev"
        private const val LIBRARY_API_URL = "$API_BASE_URL/v1/my/library"
        private const val OAUTH_URL = "$BASE_URL/auth/oauth2"
        private const val SCOPES = "library.read library.write offline_access openid"

        private const val REDIRECT_URI = "komikku://mangabaka-auth"

        private const val APP_JSON = "application/json"

        private const val CACHE_TTL_MS = 20_000L
        private const val MAX_CACHE_SIZE = 100

        private var codeVerifier: String = ""

        fun authUrl(): Uri = "$OAUTH_URL/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("code_challenge", getPkceS256ChallengeCode())
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .build()

        fun refreshTokenRequest(token: String) = POST(
            "$OAUTH_URL/token",
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("refresh_token", token)
                .add("redirect_uri", REDIRECT_URI)
                .build(),
        )

        private fun getPkceS256ChallengeCode(): String {
            // MangaBaka requires an actually conformant PKCE process, unlike MAL
            // 1. create verifier
            // 2. create challenge from verifier (S256 hash -> base64 URL encode)
            // 3. send challenge to /authorize
            // 4. send verifier for access tokens to /token
            val codes = PkceUtil.generateS256Codes()
            codeVerifier = codes.codeVerifier
            return codes.codeChallenge
        }
    }
}
