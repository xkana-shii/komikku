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

    suspend fun testLibraryAuth(): Boolean {
        return try {
            authClient.newCall(
                GET("$API_BASE_URL/v1/my/library?limit=1&page=1"),
            ).awaitSuccess()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getLibraryEntryWithSeries(remoteId: Long): MBListItem? {
        return try {
            val response = authClient.newCall(
                GET("$API_BASE_URL/v1/my/library?q=mb:$remoteId"),
            ).awaitSuccess()
            val bodyString = response.body.string()
            val libraryResponse = json.decodeFromString(MBLibrarySearchResponse.serializer(), bodyString)
            libraryResponse.data.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getSeriesListItem(remoteId: Long): MBListItem? {
        return try {
            val response = authClient.newCall(GET("$API_BASE_URL/v1/my/library?q=mb:$remoteId")).awaitSuccess()
            val bodyString = response.body.string()
            val wrapper = json.decodeFromString(MBLibrarySearchResponse.serializer(), bodyString)
            wrapper.data.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateSeriesEntryPatch(track: Track, numberOfRereads: Int?): Boolean {
        return try {
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
            authClient.newCall(
                PATCH(
                    url = "$API_BASE_URL/v1/my/library/${track.remote_id}",
                    headers = Headers.headersOf(),
                    body = body.toRequestBody(CONTENT_TYPE),
                ),
            ).awaitSuccess()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteSeriesEntry(remoteId: Long): Boolean {
        return try {
            authClient.newCall(DELETE("$API_BASE_URL/v1/my/library/$remoteId")).awaitSuccess()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun search(query: String): List<MBRecord> {
        return try {
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
            if (extractTrackerUrlInfo(query) != null) {
                val root = json.parseToJsonElement(bodyString)
                val seriesArr = root.jsonObject["data"]
                    ?.jsonObject?.get("series")
                    ?: return emptyList()
                json.decodeFromJsonElement(ListSerializer(MBRecord.serializer()), seriesArr)
            } else {
                val searchResponse = json.decodeFromString(MBSearchResponse.serializer(), bodyString)
                searchResponse.data
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getSeries(remoteId: Long): MBRecord? {
        return try {
            val response = client.newCall(GET("$API_BASE_URL/v1/series/$remoteId")).awaitSuccess()
            val bodyString = response.body.string()
            val recordResponse = json.decodeFromString(MBSeriesResponse.serializer(), bodyString)
            recordResponse.data
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val API_BASE_URL = "https://api.mangabaka.dev"
        private val CONTENT_TYPE = "application/json".toMediaType()
    }
}
