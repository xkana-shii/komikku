package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.withIOContext
import okhttp3.OkHttpClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import tachiyomi.domain.track.model.Track as DomainTrack

class MangaBakaApi(private val client: OkHttpClient) {

    suspend fun addLibManga(track: Track): Track {
        return withIOContext {
            // Construct request to add manga to library
            track
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            // Construct request to update manga in library
            track
        }
    }

    suspend fun deleteLibManga(track: DomainTrack) {
        withIOContext {
            // Construct request to delete manga from library
        }
    }

    suspend fun search(query: String): List<TrackSearch> {
        return withIOContext {
            // Construct search request
            emptyList()
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withIOContext {
            // Construct request to find library entry
            null
        }
    }

    suspend fun getLibManga(track: Track): Track {
        return findLibManga(track) ?: throw Exception("Could not find manga")
    }

    suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        return withIOContext {
            // Construct request to fetch manga metadata
            TrackMangaMetadata()
        }
    }
}
