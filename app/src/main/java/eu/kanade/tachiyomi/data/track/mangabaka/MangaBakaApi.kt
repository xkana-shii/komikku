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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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

    private data class TrackerUrlInfo(val service: String, val id: String)

    private fun extractTrackerUrlInfo(query: String): TrackerUrlInfo? {
        val trimmed = query.trim()
        return when {
            trimmed.contains("anilist.co/manga/") -> {
                val rest = trimmed.substringAfter("anilist.co/manga/")
                val id = rest.takeWhile { it.isDigit() }
                if (id.isNotEmpty()) TrackerUrlInfo("anilist", id) else null
            }
            trimmed.contains("www.mangaupdates.com/series/") -> {
                val rest = trimmed.substringAfter("www.mangaupdates.com/series/")
                val id = rest.takeWhile { it.isLetterOrDigit() }
                if (id.isNotEmpty()) TrackerUrlInfo("manga-updates", id) else null
            }
            trimmed.contains("myanimelist.net/manga/") -> {
                val rest = trimmed.substringAfter("myanimelist.net/manga/")
                val id = rest.takeWhile { it.isDigit() }
                if (id.isNotEmpty()) TrackerUrlInfo("my-anime-list", id) else null
            }
            else -> null
        }
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
        val trackerInfo = extractTrackerUrlInfo(query)
        val response = if (trackerInfo != null) {
            val url = "$API_BASE_URL/v1/source/${trackerInfo.service}/${trackerInfo.id}?with_series=true"
            client.newCall(GET(url)).awaitSuccess()
        } else {
            val ratings = listOf("safe", "suggestive", "erotica", "pornographic")
            val ratingsParams = ratings.joinToString("&") { "content_rating=$it" }
            val url = "$API_BASE_URL/v1/series/search?q=$query&$ratingsParams"
            client.newCall(GET(url)).awaitSuccess()
        }
        val bodyString = response.body.string()
        try {
            return if (trackerInfo != null) {
                val root = json.parseToJsonElement(bodyString)
                val seriesArr = root.jsonObject["data"]
                    ?.jsonObject?.get("series")
                    ?: throw Exception("No series data found for tracker id")
                json.decodeFromJsonElement(ListSerializer(MBRecord.serializer()), seriesArr)
            } else {
                val searchResponse = json.decodeFromString(MBSearchResponse.serializer(), bodyString)
                searchResponse.data
            }
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
