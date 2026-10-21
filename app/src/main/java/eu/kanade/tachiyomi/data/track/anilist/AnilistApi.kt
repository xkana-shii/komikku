package eu.kanade.tachiyomi.data.track.anilist

import android.net.Uri
import androidx.core.net.toUri
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.anilist.dto.ALError
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMangaMetadata
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUser
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.dataOrElse
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mihon.graphql.anilist.AniListAddMangaMutation
import mihon.graphql.anilist.AniListDeleteMangaMutation
import mihon.graphql.anilist.AniListGetCurrentUserQuery
import mihon.graphql.anilist.AniListGetLibMangaQuery
import mihon.graphql.anilist.AniListGetMangaDetailsQuery
import mihon.graphql.anilist.AniListGetMangaRelationsQuery
import mihon.graphql.anilist.AniListSearchMangaQuery
import mihon.graphql.anilist.AniListUpdateMangaMutation
import mihon.graphql.anilist.type.FuzzyDateInput
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.SequelPrequelEntry
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import tachiyomi.domain.track.model.Track as DomainTrack

class AnilistApi(
    val trackerId: Long,
    val client: OkHttpClient,
    interceptor: AnilistInterceptor,
    private val json: Json,
) {

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .rateLimit(permits = 25, period = 1.minutes)
        .build()

    // KMK -->
    private suspend fun Call.awaitALSuccess(): Response {
        val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
        val response = try {
            await()
        } catch (e: java.io.IOException) {
            e.stackTrace = callStack
            throw e
        }
        try {
            response.parseALError()
        } catch (e: Exception) {
            response.close()
            e.stackTrace = callStack
            throw e
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw eu.kanade.tachiyomi.network.HttpException(code).apply { stackTrace = callStack }
        }
        return response
    }

    private fun Response.parseALError() {
        val bodyString = try {
            peekBody(1024 * 1024).string()
        } catch (_: Exception) {
            return
        }
        val errorObj = try {
            json.decodeFromString<ALError>(bodyString)
        } catch (_: Exception) {
            null
        }

        errorObj?.errors?.firstOrNull()?.let {
            val msg = it.message
            if (msg.contains("Invalid token") || it.status == 401) {
                throw Exception("AniList token expired, please login again")
            }
            throw Exception(msg)
        }
    }
    // KMK <--

    private val graphQlClient by lazy {
        ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .okHttpClient(authClient)
            .dispatcher(Dispatchers.IO)
            // required to log the error body in dataOrElse, which also properly closes it
            .httpExposeErrorBody(true)
            .build()
    }

    suspend fun addLibManga(track: Track): Track {
        return graphQlClient
            .mutation(
                AniListAddMangaMutation(
                    manga_id = track.remote_id.toInt(),
                    progress = track.last_chapter_read.toInt(),
                    status = track.toApiStatus(),
                    private = track.private,
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to add manga",
                default = { null },
            ) {
                it.SaveMediaListEntry?.id?.let { libraryId ->
                    track.library_id = libraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to add manga")
    }

    suspend fun updateLibManga(track: Track): Track {
        val libraryId = track.library_id
        requireNotNull(libraryId) { "AniList cannot update track with null library_id" }

        return graphQlClient
            .mutation(
                AniListUpdateMangaMutation(
                    library_id = libraryId.toInt(),
                    progress = track.last_chapter_read.toInt(),
                    status = track.toApiStatus(),
                    private = track.private,
                    score = track.score.toInt(),
                    startedAt = createFuzzyDate(track.started_reading_date),
                    completedAt = createFuzzyDate(track.finished_reading_date),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to update manga",
                default = { null },
            ) {
                it.SaveMediaListEntry?.id?.let { remoteLibraryId ->
                    track.library_id = remoteLibraryId.toLong()
                    track
                }
            }
            ?: throw Exception("Failed to update manga")
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        val libraryId = track.libraryId
        requireNotNull(libraryId) { "AniList cannot delete track with null library_id" }

        graphQlClient
            .mutation(
                AniListDeleteMangaMutation(library_id = libraryId.toInt()),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to delete manga",
                default = { null },
            ) {
                it.DeleteMediaListEntry?.deleted?.let { deleted ->
                    if (deleted) {
                        logcat { "AniList: Deleted manga ${track.libraryId} successfully" }
                    }
                }
            }
            ?: throw Exception("Failed to delete manga")
    }

    suspend fun search(search: String): List<TrackSearch> {
        return graphQlClient
            .query(
                AniListSearchMangaQuery(search = search),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Search failed",
                default = { emptyList() },
            ) {
                it.Page?.media
                    ?.mapNotNull { alManga -> alManga?.toTrackSearch(trackerId) }
                    ?: emptyList()
            }
    }

    suspend fun findLibManga(track: Track, userId: Int): Track? {
        return graphQlClient
            .query(
                AniListGetLibMangaQuery(
                    user_id = userId,
                    manga_id = track.remote_id.toInt(),
                ),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to find manga in library",
                default = { null },
            ) {
                it.Page?.mediaList
                    ?.firstOrNull()
                    ?.toTrack(trackerId)
            }
    }

    suspend fun getCurrentUser(): ALUser {
        return graphQlClient
            .query(
                AniListGetCurrentUserQuery(),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get current user",
                default = { null },
            ) {
                it.Viewer?.toALUser()
            }
            ?: throw Exception("Failed to get AniList user data")
    }

    // KNS -->
    suspend fun getRelatedEntries(mediaId: Long): List<SequelPrequelEntry> {
        return graphQlClient
            .query(AniListGetMangaRelationsQuery(manga_id = mediaId.toInt()))
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get manga relations",
                default = { emptyList() },
            ) { it.toRelationEdges().toRelatedEntries(trackerId) }
    }
    // KNS <--

    suspend fun getMangaDetails(id: Int): TrackSearch? {
        return graphQlClient
            .query(
                AniListGetMangaDetailsQuery(manga_id = id),
            )
            .execute()
            .dataOrElse(
                errorLog = "AniList: Failed to get manga details",
                default = { null },
            ) {
                it.Page?.media
                    ?.firstOrNull()
                    ?.toTrackSearch(trackerId)
            }
    }

    // SY -->
    suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        return withIOContext {
            val query = $$"""
            |query ($mangaId: Int!) {
                |Media (id: $mangaId) {
                    |id
                    |title {
                        |userPreferred
                    |}
                    |coverImage {
                        |large
                    |}
                    |description
                    |staff {
                        |edges {
                            |role
                            |id
                            |node {
                                |name {
                                    |full
                                    |userPreferred
                                    |native
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            |
            """.trimMargin()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("mangaId", track.remoteId)
                }
            }
            with(json) {
                authClient.newCall(
                    POST(
                        API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    // KMK -->
                    .awaitALSuccess()
                    // KMK <--
                    .parseAs<ALMangaMetadata>()
                    .let { metadata ->
                        val media = metadata.data.media
                        TrackMangaMetadata(
                            remoteId = media.id,
                            title = media.title.userPreferred,
                            thumbnailUrl = media.coverImage.large,
                            description = media.description?.htmlDecode()?.ifEmpty { null },
                            authors = media.staff.edges
                                .filter { "Story" in it.role }
                                .mapNotNull { it.node.name() }
                                .joinToString(", ")
                                .ifEmpty { null },
                            artists = media.staff.edges
                                .filter { "Art" in it.role }
                                .mapNotNull { it.node.name() }
                                .joinToString(", ")
                                .ifEmpty { null },
                        )
                    }
            }
        }
    }

    suspend fun searchById(id: String): TrackSearch? {
        return id.toIntOrNull()?.let { getMangaDetails(it) }
    }
    // SY <--

    private fun createFuzzyDate(dateValue: Long): FuzzyDateInput {
        // all absent/null
        if (dateValue == 0L) return FuzzyDateInput()

        val dateTime = Instant.fromEpochMilliseconds(dateValue).toLocalDateTime(TimeZone.currentSystemDefault())
        return FuzzyDateInput(
            year = Optional.present(dateTime.year),
            month = Optional.present(dateTime.month.number),
            day = Optional.present(dateTime.day),
        )
    }

    companion object {
        // Registered under KMK's MAL account
        private const val CLIENT_ID = "16801"
        private const val API_URL = "https://graphql.anilist.co/"
        private const val BASE_URL = "https://anilist.co/api/v2/"
        private const val BASE_MANGA_URL = "https://anilist.co/manga/"

        fun mangaUrl(mediaId: Long): String {
            return BASE_MANGA_URL + mediaId
        }

        fun authUrl(): Uri = "${BASE_URL}oauth/authorize".toUri().buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "token")
            .build()
    }
}
