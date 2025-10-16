package eu.kanade.tachiyomi.data.track.myanimelist

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListItem
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListItemStatus
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListItemStatusWrapper
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALManga
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaMetadata
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALSearchResult
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALUser
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALUserSearchResult
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.text.ifEmpty
import tachiyomi.domain.track.model.Track as DomainTrack

class MyAnimeListApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: MyAnimeListInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun getAccessToken(authCode: String): MALOAuth {
        return withIOContext {
            val formBody: RequestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", authCode)
                .add("code_verifier", codeVerifier)
                .add("grant_type", "authorization_code")
                .build()
            with(json) {
                client.newCall(POST("$BASE_OAUTH_URL/token", body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): String {
        return withIOContext {
            val request = Request.Builder()
                .url("$BASE_API_URL/users/@me")
                .get()
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<MALUser>()
                    .name
            }
        }
    }

    suspend fun search(query: String): List<TrackSearch> {
        return withIOContext {
            val url = "$BASE_API_URL/manga".toUri().buildUpon()
                // MAL API throws a 400 when the query is over 64 characters...
                .appendQueryParameter("q", query.take(64))
                .appendQueryParameter("nsfw", "true")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALSearchResult>()
                    .data
                    .map { async { getMangaDetails(it.node.id) } }
                    .awaitAll()
                    .filter { !it.publishing_type.contains("novel") }
            }
        }
    }

    suspend fun getMangaDetails(id: Int): TrackSearch {
        return withIOContext {
            val url = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(id.toString())
                .appendQueryParameter(
                    "fields",
                    "id,title,synopsis,num_chapters,mean,main_picture,status,media_type,start_date,authors{first_name,last_name,role}",
                )
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALManga>()
                    .let {
                        TrackSearch.create(trackId).apply {
                            remote_id = it.id
                            title = it.title
                            summary = it.synopsis
                            total_chapters = it.numChapters
                            score = it.mean
                            cover_url = (it.covers?.large ?: it.covers?.medium).orEmpty()
                            tracking_url = "https://myanimelist.net/manga/$remote_id"
                            publishing_status = it.status.replace("_", " ")
                            publishing_type = it.mediaType.replace("_", " ")
                            start_date = it.startDate ?: ""
                            authors = it.authors
                                .filter { "Story" in it.role }
                                .map { "${it.node.firstName} ${it.node.lastName}".trim() }
                            artists = it.authors
                                .filter { "Art" in it.role }
                                .map { "${it.node.firstName} ${it.node.lastName}".trim() }
                        }
                    }
            }
        }
    }

    suspend fun updateItem(track: Track): Track {
        return withIOContext {
            // Fetch current list status to determine if reread count should be incremented
            val previousStatus = getCurrentListStatus(track.remote_id)

            val targetStatus = track.toMyAnimeListStatus() ?: "reading"
            val isTargetCompleted = targetStatus == "completed"
            val wasRereading = previousStatus?.isRereading == true

            val formBodyBuilder = FormBody.Builder()
                .add("status", targetStatus)
                .add("score", track.score.toString())
                .add("num_chapters_read", track.last_chapter_read.toInt().toString())
            // If changing status from rereading -> completed, increment MAL's num_times_reread
            val initialIsRereading = (track.status == MyAnimeList.REREADING)
            var finalIsRereading = initialIsRereading
            if (isTargetCompleted && wasRereading) {
                val nextRereadCount = (previousStatus?.numTimesReread ?: 0) + 1
                formBodyBuilder.add("num_times_reread", nextRereadCount.toString())
                // Ensure rereading flag is false when completed
                finalIsRereading = false
            }
            convertToIsoDate(track.started_reading_date)?.let {
                formBodyBuilder.add("start_date", it)
            }
            convertToIsoDate(track.finished_reading_date)?.let {
                formBodyBuilder.add("finish_date", it)
            }
            // Add is_rereading only once after finalizing its value
            formBodyBuilder.add("is_rereading", finalIsRereading.toString())

            val request = Request.Builder()
                .url(mangaUrl(track.remote_id).toString())
                .put(formBodyBuilder.build())
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<MALListItemStatus>()
                    .let { parseMangaItem(it, track) }
            }
        }
    }

    suspend fun deleteItem(track: DomainTrack) {
        withIOContext {
            authClient
                .newCall(DELETE(mangaUrl(track.remoteId).toString()))
                .awaitSuccess()
        }
    }

    suspend fun findListItem(track: Track): Track? {
        return withIOContext {
            val uri = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(track.remote_id.toString())
                .appendQueryParameter(
                    "fields",
                    "num_volumes,num_chapters,my_list_status{start_date,finish_date,is_rereading,num_times_reread}",
                )
                .build()
            with(json) {
                authClient.newCall(GET(uri.toString()))
                    .awaitSuccess()
                    .parseAs<MALListItem>()
                    .let { item ->
                        track.total_chapters = item.numChapters
                        item.myListStatus?.let { parseMangaItem(it, track) }
                    }
            }
        }
    }

    suspend fun findListItems(query: String, offset: Int = 0): List<TrackSearch> {
        return withIOContext {
            val myListSearchResult = getListPage(offset)

            val matches = myListSearchResult.data
                .filter { it.node.title.contains(query, ignoreCase = true) }
                .map { async { getMangaDetails(it.node.id) } }
                .awaitAll()

            // Check next page if there's more
            if (!myListSearchResult.paging.next.isNullOrBlank()) {
                matches + findListItems(query, offset + LIST_PAGINATION_AMOUNT)
            } else {
                matches
            }
        }
    }

    suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        return withIOContext {
            val url = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(track.remoteId.toString())
                .appendQueryParameter(
                    "fields",
                    "id,title,synopsis,main_picture,authors{first_name,last_name}",
                )
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALMangaMetadata>()
                    .let {
                        TrackMangaMetadata(
                            remoteId = it.id,
                            title = it.title,
                            thumbnailUrl = it.covers.large?.ifEmpty { null } ?: it.covers.medium,
                            description = it.synopsis,
                            authors = it.authors
                                .filter { "Story" in it.role }
                                .joinToString(separator = ", ") { "${it.node.firstName} ${it.node.lastName}".trim() }
                                .ifEmpty { null },
                            artists = it.authors
                                .filter { "Art" in it.role }
                                .joinToString(separator = ", ") { "${it.node.firstName} ${it.node.lastName}".trim() }
                                .ifEmpty { null },
                        )
                    }
            }
        }
    }

    suspend fun getPaginatedMangaList(page: Int, statusId: Long): List<TrackMangaMetadata> {
        return withIOContext {
            val urlBuilder = "$BASE_API_URL/users/@me/mangalist".toUri().buildUpon()
                .appendQueryParameter("status", "${statusId.toMyAnimeListStatus()}")
                .appendQueryParameter("fields", "list_status")
                .appendQueryParameter("limit", 50.toString())
                .appendQueryParameter("offset", ((page - 1) * 50).toString())

            val request = Request.Builder().url(urlBuilder.build().toString()).get().build()
            with(json) {
                val data = authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<MALUserSearchResult>()
                    .data
                data.mapNotNull {
                    if (statusId == MyAnimeList.REREADING && !it.listStatus!!.isRereading) {
                        null
                    } else {
                        TrackMangaMetadata(
                            remoteId = it.node.id.toLong(),
                            title = it.node.title,
                            thumbnailUrl = it.node.covers?.large,
                        )
                    }
                }
            }
        }
    }

    private suspend fun getListPage(offset: Int): MALUserSearchResult {
        return withIOContext {
            val urlBuilder = "$BASE_API_URL/users/@me/mangalist".toUri().buildUpon()
                .appendQueryParameter("fields", "list_status{start_date,finish_date}")
                .appendQueryParameter("limit", LIST_PAGINATION_AMOUNT.toString())
            if (offset > 0) {
                urlBuilder.appendQueryParameter("offset", offset.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build().toString())
                .get()
                .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun parseMangaItem(listStatus: MALListItemStatus, track: Track): Track {
        return track.apply {
            val isRereading = listStatus.isRereading
            status = if (isRereading) MyAnimeList.REREADING else getStatus(listStatus.status)
            last_chapter_read = listStatus.numChaptersRead
            score = listStatus.score.toDouble()
            listStatus.startDate?.let { started_reading_date = parseDate(it) }
            listStatus.finishDate?.let { finished_reading_date = parseDate(it) }
        }
    }

    private suspend fun getCurrentListStatus(remoteId: Long): MALListItemStatus? {
        return withIOContext {
            val uri = "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(remoteId.toString())
                .appendQueryParameter("fields", "my_list_status{is_rereading,num_times_reread}")
                .build()
            with(json) {
                val wrapper = authClient.newCall(GET(uri.toString()))
                    .awaitSuccess()
                    .parseAs<MALListItemStatusWrapper>()
                wrapper.myListStatus
            }
        }
    }

    private fun parseDate(isoDate: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time ?: 0L
    }

    private fun convertToIsoDate(epochTime: Long): String? {
        if (epochTime == 0L) {
            return ""
        }
        return try {
            val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            outputDf.format(epochTime)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        // Registered under KMK's MAL account
        private const val CLIENT_ID = "2be14959235191ece14eebdc2eea0466"

        private const val BASE_OAUTH_URL = "https://myanimelist.net/v1/oauth2"
        private const val BASE_API_URL = "https://api.myanimelist.net/v2"

        private const val LIST_PAGINATION_AMOUNT = 250

        private var codeVerifier: String = ""

        fun authUrl(): Uri = "$BASE_OAUTH_URL/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("code_challenge", getPkceChallengeCode())
            .appendQueryParameter("response_type", "code")
            .build()

        fun mangaUrl(id: Long): Uri = "$BASE_API_URL/manga".toUri().buildUpon()
            .appendPath(id.toString())
            .appendPath("my_list_status")
            .build()

        fun refreshTokenRequest(oauth: MALOAuth): Request {
            val formBody: RequestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("refresh_token", oauth.refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            // Add the Authorization header manually as this particular
            // request is called by the interceptor itself so it doesn't reach
            // the part where the token is added automatically.
            val headers = Headers.Builder()
                .add("Authorization", "Bearer ${oauth.accessToken}")
                .build()

            return POST("$BASE_OAUTH_URL/token", body = formBody, headers = headers)
        }

        private fun getPkceChallengeCode(): String {
            codeVerifier = PkceUtil.generateCodeVerifier()
            return codeVerifier
        }
    }
}
