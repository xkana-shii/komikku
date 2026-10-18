package tachiyomi.domain.track.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.model.Track

interface TrackRepository {

    suspend fun getTrackById(id: Long): Track?

    // SY -->
    suspend fun getTracks(): List<Track>

    suspend fun getTracksByMangaIds(mangaIds: List<Long>): List<Track>
    // SY <--

    suspend fun getTracksByMangaId(mangaId: Long): List<Track>

    fun getTracksAsFlow(): Flow<List<Track>>

    fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<Track>>

    suspend fun delete(mangaId: Long, trackerId: Long)

    // KUN -->
    /** Deletes every track of [trackerId] regardless of manga (used by the MangaDex cleanup migration). */
    suspend fun deleteByTrackerId(trackerId: Long)
    // KUN <--

    suspend fun upsert(track: Track)

    suspend fun upsertAll(tracks: List<Track>)
}
