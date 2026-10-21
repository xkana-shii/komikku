package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.category.interactor.SyncCategoriesWithTrackStatus
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.service.TrackPersistenceListener

// KNS -->
/**
 * Smart Categories, driven by persisted tracker state: after a track is written ([UpsertTrack]
 * notifies this), the manga's unified tracker status is resolved and its status-bound categories are
 * updated within the branches it belongs to (`SyncCategoriesWithTrackStatus`).
 *
 * The status comes from the stored tracks, never from UI state, and a manga without any track with a
 * recognised status is left alone instead of being given an invented one. Updates are serialized so
 * overlapping tracker writes cannot interleave category changes.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SyncCategoryTrackStatus(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val syncCategoriesWithTrackStatus: SyncCategoriesWithTrackStatus,
) : TrackPersistenceListener {

    private val mutex = Mutex()

    override suspend fun onTracksPersisted(mangaIds: Set<Long>) {
        mangaIds.forEach { await(it) }
    }

    suspend fun await(mangaId: Long) = mutex.withLock {
        val status = resolveStatus(getTracks.await(mangaId)) ?: return@withLock
        syncCategoriesWithTrackStatus.await(mangaId, status)
    }

    /** Deterministic: trackers in id order, the first one whose status maps to a unified status wins. */
    private fun resolveStatus(tracks: List<Track>): Int? {
        return tracks.sortedBy(Track::trackerId).firstNotNullOfOrNull { track ->
            TrackStatus.parseTrackerStatus(trackerManager, track.trackerId, track.status)?.int
        }
    }
}
// KNS <--
