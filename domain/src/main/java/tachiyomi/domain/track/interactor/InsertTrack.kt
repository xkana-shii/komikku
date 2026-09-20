package tachiyomi.domain.track.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

class InsertTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(track: Track) {
        try {
            trackRepository.insert(track)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    // KMK --> Explicit batch operations must report persistence failures to their caller.
    suspend fun awaitOrThrow(track: Track) {
        trackRepository.insert(track)
    }
    // KMK <--

    suspend fun awaitAll(tracks: List<Track>) {
        try {
            trackRepository.insertAll(tracks)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
