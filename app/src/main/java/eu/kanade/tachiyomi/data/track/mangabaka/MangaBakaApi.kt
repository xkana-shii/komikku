package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MBLibrarySearchResponse
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MBListItem
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MBListItemRequest
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MBRecord
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MBSearchResponse
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MBSeriesResponse
import eu.kanade.tachiyomi.data.track.mangabaka.dto.formatDate
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PATCH
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

class MangaBakaApi(
    interceptor: MangaBakaInterceptor,
    private val client: OkHttpClient,
) {
    private val json: Json by injectLazy()
    private val authClient: OkHttpClient by lazy {
        client.newBuilder()
            .addInterceptor(interceptor)
            .build()
    }

    private fun trackerUrlToQuery(query: String): String {
        val trimmed = query.trim()
        val urlPrefixes = mapOf(
            "anilist.co/manga/" to "al:",
            "www.mangaupdates.com/series/" to "mu:",
            "myanimelist.net/manga/" to "mal:",
        )
        for ((prefix, qPrefix) in urlPrefixes) {
            val idx = trimmed.indexOf(prefix)
            if (idx != -1) {
                val rest = trimmed.substring(idx + prefix.length)
                val id = rest.split('/')[0]
                return qPrefix + id
            }
        }
        return query
    }

    suspend fun testLibraryAuth() {
        try {
            authClient.newCall(
                GET("$API_BASE_URL/v1/my/library?limit=1&page=1"),
            ).awaitSuccess()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getLibraryEntryWithSeries(remoteId: Long): MBListItem {
        val response = authClient.newCall(
            GET("$API_BASE_URL/v1/my/library?q=mb:$remoteId"),
        ).awaitSuccess()
        val bodyString = response.body.string()
        try {
            val libraryResponse = json.decodeFromString(MBLibrarySearchResponse.serializer(), bodyString)
            val item = libraryResponse.data.firstOrNull()

            return item ?: throw Exception("No library entry for series_id $remoteId")
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getSeriesListItem(remoteId: Long): MBListItem {
        val response = authClient.newCall(GET("$API_BASE_URL/v1/my/library?q=mb:$remoteId")).awaitSuccess()
        val bodyString = response.body.string()
        try {
            val wrapper = json.decodeFromString(MBLibrarySearchResponse.serializer(), bodyString)
            val item = wrapper.data.firstOrNull()

            return item ?: throw Exception("No item found in library for remoteId $remoteId")
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun addSeriesEntry(track: Track, hasReadChapters: Boolean) {
        val normalizedScore = (track.score * 10).coerceIn(0.0, 100.0)
        val entry = MBListItemRequest(
            state = if (hasReadChapters) "reading" else "plan_to_read",
            progress_chapter = if (hasReadChapters) track.last_chapter_read.toInt() else 0,
            rating = normalizedScore,
            is_private = track.private,
            start_date = if (hasReadChapters) formatDate(System.currentTimeMillis()) else null,
            finish_date = if (track.status == MangaBaka.COMPLETED) formatDate(System.currentTimeMillis()) else null,
        )
        val body = json.encodeToString(MBListItemRequest.serializer(), entry)
        try {
            authClient.newCall(
                POST(
                    url = "$API_BASE_URL/v1/my/library/${track.remote_id}",
                    headers = Headers.headersOf(),
                    body = body.toRequestBody(CONTENT_TYPE),
                ),
            ).awaitSuccess()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun updateSeriesEntryPatch(track: Track, numberOfRereads: Int?) {
        val normalizedScore = (track.score * 10).coerceIn(0.0, 100.0)
        val entry = MBListItemRequest(
            state = when (track.status) {
                MangaBaka.READING -> "reading"
                MangaBaka.COMPLETED -> "completed"
                MangaBaka.PAUSED -> "paused"
                MangaBaka.DROPPED -> "dropped"
                MangaBaka.PLAN_TO_READ -> "plan_to_read"
                MangaBaka.REREADING -> "rereading"
                else -> "plan_to_read"
            },
            progress_chapter = track.last_chapter_read.toInt(),
            rating = normalizedScore,
            is_private = track.private,
            number_of_rereads = numberOfRereads,
            start_date = if (track.started_reading_date > 0) formatDate(track.started_reading_date) else null,
            finish_date = if (track.status == MangaBaka.COMPLETED && track.finished_reading_date > 0) formatDate(track.finished_reading_date) else null,
        )
        val body = json.encodeToString(MBListItemRequest.serializer(), entry)
        try {
            authClient.newCall(
                PATCH(
                    url = "$API_BASE_URL/v1/my/library/${track.remote_id}",
                    headers = Headers.headersOf(),
                    body = body.toRequestBody(CONTENT_TYPE),
                ),
            ).awaitSuccess()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun deleteSeriesEntry(remoteId: Long) {
        try {
            authClient.newCall(DELETE("$API_BASE_URL/v1/my/library/$remoteId")).awaitSuccess()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun search(query: String): List<MBRecord> {
        val actualQuery = trackerUrlToQuery(query)
        val url = "$API_BASE_URL/v1/series/search?q=$actualQuery&content_rating=safe%2Csuggestive%2Cerotica%2Cpornographic"
        val response = client.newCall(GET(url)).awaitSuccess()
        val bodyString = response.body.string()
        try {
            val searchResponse = json.decodeFromString(MBSearchResponse.serializer(), bodyString)
            return searchResponse.data
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getSeries(remoteId: Long): MBRecord {
        val response = client.newCall(GET("$API_BASE_URL/v1/series/$remoteId")).awaitSuccess()
        val bodyString = response.body.string()
        try {
            val recordResponse = json.decodeFromString(MBSeriesResponse.serializer(), bodyString)
            return recordResponse.data
        } catch (e: Exception) {
            throw e
        }
    }

    companion object {
        private const val API_BASE_URL = "https://api.mangabaka.dev"
        private val CONTENT_TYPE = "application/json".toMediaType()
    }
}
