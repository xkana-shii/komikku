package eu.kanade.domain.track.interactor

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.DelayedTrackingUpdateWorker
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import exh.md.utils.FollowStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import kotlin.math.max

@Inject
class TrackChapter(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val upsertTrack: UpsertTrack,
    private val delayedTrackingStore: DelayedTrackingStore,
) {

    /**
     * Updates chapter progress on all eligible logged-in trackers.
     *
     * Each tracker is updated independently. A failed tracker is
     * queued without preventing successful siblings from updating.
     *
     * This operation does not mark local chapters as read.
     */
    suspend fun await(
        context: Context,
        mangaId: Long,
        chapterNumber: Double,
        setupJobOnFailure: Boolean = true,
    ) {
        // Invalid chapter values must never be written remotely.
        if (!chapterNumber.isFinite() || chapterNumber < 0.0) {
            return
        }

        coroutineScope {
            val tracks = getTracks.await(mangaId)
            if (tracks.isEmpty()) return@coroutineScope

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)

                if (
                    service == null ||
                    !service.isLoggedIn ||
                    chapterNumber <= track.lastChapterRead ||
                    (
                        service is MdList &&
                            track.status == FollowStatus.UNFOLLOWED.long
                        )
                ) {
                    return@mapNotNull null
                }

                async {
                    try {
                        val refreshed = service
                            .refresh(track.toDbTrack())
                            .toDomainTrack(idRequired = false)
                            ?: throw IllegalStateException(
                                "Tracker returned an invalid binding",
                            )

                        // KMK -->
                        // Never roll back progress that is already ahead
                        // on the remote service.
                        val newProgress = max(
                            refreshed.lastChapterRead,
                            chapterNumber,
                        )

                        val update = refreshed.copy(
                            id = track.id,
                            mangaId = track.mangaId,
                            trackerId = track.trackerId,
                            lastChapterRead = newProgress,
                        )

                        // Persist the actual returned remote state:
                        // dates, status, and score may have changed.
                        val returned = service.update(
                            update.toDbTrack(),
                            true,
                        )

                        val persisted = returned
                            .toDomainTrack(idRequired = false)
                            ?.copy(
                                id = track.id,
                                mangaId = track.mangaId,
                                trackerId = track.trackerId,
                            )
                            ?: update

                        upsertTrack.await(persisted)
                        // KMK <--

                        delayedTrackingStore.remove(track.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        delayedTrackingStore.add(
                            track.id,
                            chapterNumber,
                        )

                        if (setupJobOnFailure) {
                            DelayedTrackingUpdateWorker.setupTask(context)
                        }

                        logcat(LogPriority.WARN, e) {
                            "Failed to update tracker ${track.trackerId} " +
                                "for manga $mangaId"
                        }
                    }
                }
            }.awaitAll()
        }
    }
}
