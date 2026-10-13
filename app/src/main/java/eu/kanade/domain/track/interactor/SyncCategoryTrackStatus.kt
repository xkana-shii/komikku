package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.category.interactor.SyncCategoriesWithTrackStatus
import tachiyomi.domain.track.interactor.GetTracks

// KMK -->
/** Resolves a deterministic local tracker status before updating explicitly bound categories. */
class SyncCategoryTrackStatus(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val trackPreferences: TrackPreferences,
    private val syncCategoriesWithTrackStatus: SyncCategoriesWithTrackStatus,
) {
    suspend fun await(mangaId: Long) {
        val tracks = getTracks.await(mangaId)
        val preferred = trackPreferences.resolvePreferredTracker(mangaId, tracks.map { it.trackerId }.toSet())
        val ordered = tracks.sortedWith(
            compareByDescending<tachiyomi.domain.track.model.Track> { it.trackerId == preferred }
                .thenBy { it.trackerId },
        )
        val status = ordered.firstNotNullOfOrNull { track ->
            TrackStatus.parseTrackerStatus(trackerManager, track.trackerId, track.status)?.int
        }
        syncCategoriesWithTrackStatus.await(mangaId, status)
    }
}
// KMK <--
