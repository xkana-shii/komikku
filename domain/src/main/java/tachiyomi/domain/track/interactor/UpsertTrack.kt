package tachiyomi.domain.track.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.track.service.TrackPersistenceListener

@Inject
class UpsertTrack(
    private val trackRepository: TrackRepository,
    // KNS -->
    private val persistenceListener: TrackPersistenceListener,
    // KNS <--
) {

    suspend fun await(track: Track) {
        try {
            trackRepository.upsert(track)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            // KNS -->
            return
            // KNS <--
        }
        // KNS -->
        notifyPersisted(setOf(track.mangaId))
        // KNS <--
    }

    suspend fun awaitAll(tracks: List<Track>) {
        try {
            trackRepository.upsertAll(tracks)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            // KNS -->
            return
            // KNS <--
        }
        // KNS -->
        notifyPersisted(tracks.mapTo(mutableSetOf()) { it.mangaId })
        // KNS <--
    }

    // KNS -->
    /** Like [await], but a failed save is thrown so the caller can report it for that tracker. */
    suspend fun awaitOrThrow(track: Track) {
        trackRepository.upsert(track)
        notifyPersisted(setOf(track.mangaId))
    }

    /** Automation must never make the tracker write itself look failed. */
    private suspend fun notifyPersisted(mangaIds: Set<Long>) {
        if (mangaIds.isEmpty()) return
        try {
            persistenceListener.onTracksPersisted(mangaIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Tracker persistence listener failed" }
        }
    }
    // KNS <--
}
