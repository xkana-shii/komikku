package eu.kanade.tachiyomi.data.track.shikimori

import android.net.Uri
import androidx.compose.ui.util.fastAny
import androidx.core.net.toUri
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMLibraryIdResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMMetadata
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUser
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mihon.graphql.shikimori.ShikimoriGetCurrentUserQuery
import mihon.graphql.shikimori.ShikimoriGetLibMangaQuery
import mihon.graphql.shikimori.ShikimoriGetMangaDetailsQuery
import mihon.graphql.shikimori.ShikimoriSearchMangaQuery
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class ShikimoriApi(
    private val trackerId: Long,
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl("$API_URL/graphql")
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            // required to log the error body in dataOrElse, which also properly closes it
            .httpExposeErrorBody(true)
            .build()
    }

    suspend fun addLibManga(track: Track, userId: String): Track {
        return withContext(Dispatchers.IO) {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Manga")
                        put("chapters", track.last_chapter_read.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMLibraryIdResponse>()
                    .let {
                        // save id of the entry for possible future delete request
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                putJsonObject("user_rate") {
                    put("chapters", track.last_chapter_read.toInt())
                    put("score", track.score.toInt())
                    put("status", track.toShikimoriStatus())
                }
            }

            with(json) {
                authClient.newCall(
                    PUT(
                        "$API_URL/v2/user_rates/${track.library_id}",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<SMLibraryIdResponse>()
                    .let {
                        track.library_id = it.id
                    }
                track
            }
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        withContext(Dispatchers.IO) {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess().close()
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return graphQlClient
            .query(
                ShikimoriSearchMangaQuery(search = search),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Search failed",
                default = { emptyList() },
            ) {
                it.mangas.map { manga ->
                    manga.toTrackSearch(trackerId)
                }
            }
    }

    suspend fun getMangaDetails(id: Int): TrackSearch? {
        return graphQlClient
            .query(
                ShikimoriGetMangaDetailsQuery(query = "$id"),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to get manga details",
                default = { null },
            ) {
                it.mangas
                    .firstOrNull()
                    ?.toTrackSearch(trackerId)
            }
    }

    suspend fun findLibManga(track: Track): Track? {
        return graphQlClient
            .query(
                ShikimoriGetLibMangaQuery(
                    remote_id = track.remote_id.toString(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to find manga in library",
                default = { null },
            ) {
                val mangaResult = it.mangas.firstOrNull()

                // Shikimori has no user list query that allows query by ID, so we go via the "mangas" query & include
                // userRate data which will be null if the title is not in the user's list.
                // If it was removed on Shikimori and is still linked in the app, notify user via returning null here
                // which throws an exception at the Shikimori.refresh call
                if (mangaResult?.userRate == null) {
                    null
                } else {
                    mangaResult.toTrack(trackerId)
                }
            }
    }

    suspend fun getCurrentUser(): SMUser {
        return graphQlClient
            .query(
                ShikimoriGetCurrentUserQuery(),
            )
            .execute()
            .dataOrElse(
                errorLog = "Shikimori: Failed to get current user",
                default = { null },
            ) {
                it.currentUser?.let { currentUser ->
                    SMUser(id = currentUser.id, nickname = currentUser.nickname)
                }
            }
            ?: throw Exception("Failed to get Shikimori user data")
    }

    suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        return withIOContext {
            val query = """
                |query(${'$'}ids: String!) {
                    |mangas(ids: ${'$'}ids) {
                        |id
                        |name
                        |description
                        |poster {
                            |originalUrl
                        |}
                        |personRoles {
                            |person {
                                |name
                            |}
                            |rolesEn
                        |}
                    |}
                |}
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("ids", "${track.remoteId}")
                }
            }
            with(json) {
                authClient.newCall(
                    // KUN -->
                    // SY upstream hardcodes https://shikimori.one/api/graphql here; the .one host
                    // 301-redirects POSTs to GET and 404s. Use the configured .io API base instead.
                    POST(
                        "$API_URL/graphql",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                    // KUN <--
                )
                    .awaitSuccess()
                    .parseAs<SMMetadata>()
                    .let {
                        if (it.data.mangas.isEmpty()) throw Exception("Could not get metadata from Shikimori")
                        val manga = it.data.mangas[0]
                        TrackMangaMetadata(
                            remoteId = manga.id.toLong(),
                            title = manga.name,
                            thumbnailUrl = manga.poster.originalUrl,
                            description = manga.description,
                            authors = manga.personRoles
                                .filter { it.rolesEn.fastAny { "Story" in it } }
                                .joinToString(", ") { it.person.name }
                                .ifEmpty { null },
                            artists = manga.personRoles
                                .filter { it.rolesEn.fastAny { "Art" in it } }
                                .joinToString(", ") { it.person.name }
                                .ifEmpty { null },
                        )
                    }
            }
        }
    }

    suspend fun accessToken(code: String): SMOAuth {
        return withContext(Dispatchers.IO) {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        OAUTH_URL,
        body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URL)
            .build(),
    )

    companion object {
        private const val BASE_URL = "https://shikimori.io"
        private const val API_URL = "$BASE_URL/api"
        private const val OAUTH_URL = "$BASE_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"

        private const val REDIRECT_URL = "komikku://shikimori-auth"

        private const val CLIENT_ID = "hst3O_VlZ0HrrXcnZAd6m-SCIc1_mv5cQV-3nQCdJ0E"
        private const val CLIENT_SECRET = "n1Pn9AIKvUhDzjehfPt_5nk7xbr4wSnas6djggw_AjE"

        fun authUrl(): Uri = LOGIN_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .appendQueryParameter("response_type", "code")
            .build()

        fun refreshTokenRequest(token: String) = POST(
            OAUTH_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", token)
                .build(),
        )
    }
}
