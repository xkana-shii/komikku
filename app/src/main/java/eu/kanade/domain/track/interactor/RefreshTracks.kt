
package eu.kanade.domain.track.interactor

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.util.system.toast
import exh.md.utils.FollowStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.kmk.KMR
import kotlin.math.max

@Inject
class RefreshTracks(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val upsertTrack: UpsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val context: Context,
) {

    // KMK -->
    // Successive progress adjustments must read the previous write.
    // They cannot race each other even when individual trackers
    // are updated concurrently.
    private val adjustmentMutex = Mutex()
    // KMK <--

    /**
     * Refresh bound tracks and optionally synchronize all trackers
     * with the local chapter list.
     *
     * Returns one pair for each failed service.
     */
    suspend fun await(
        mangaId: Long,
        enhancedTrackersOnly: Boolean = true,
    ): List<Pair<Tracker?, Throwable>> = supervisorScope {
        getTracks.await(mangaId)
            .mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                if (service?.isLoggedIn != true) {
                    null
                } else {
                    track to service
                }
            }
            .map { (track, service) ->
                async(Dispatchers.IO) {
                    try {
                        val refreshed = service.refresh(
                            track.toDbTrack(),
                        ).toDomainTrack(idRequired = false)
                            ?: throw IllegalStateException(
                                "Tracker returned an invalid track",
                            )

                        val updated = refreshed.copy(
                            id = track.id,
                            mangaId = track.mangaId,
                            trackerId = track.trackerId,
                        )

                        upsertTrack.await(updated)

                        val chapter = if (enhancedTrackersOnly) {
                            syncChapterProgressWithTrack.await(
                                mangaId,
                                updated,
                                service,
                            )
                        } else {
                            syncChapterProgressWithTrack.sync(
                                mangaId,
                                updated,
                                service,
                            )
                        }

                        chapter?.let {
                            withUIContext {
                                context.toast(
                                    context.stringResource(
                                        KMR.strings.sync_progress_from_trackers_up_to_chapter,
                                        it,
                                    ),
                                )
                            }
                        }

                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        service to e
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    /**
     * Update all eligible tracker progress values by one or more
     * chapters while preserving concurrent writes across services.
     *
     * A fresh remote read is performed before choosing the target.
     * Failed refreshes cannot supply stale progress.
     */
    suspend fun adjustProgress(
        mangaId: Long,
        delta: Int,
    ): List<Pair<Tracker?, Throwable>> = adjustmentMutex.withLock {
        if (delta == 0) return@withLock emptyList()

        supervisorScope {
            val bindings = getTracks.await(mangaId)
                .mapNotNull { track ->
                    val service = trackerManager.get(track.trackerId)
                    when {
                        service?.isLoggedIn != true -> null

                        service is MdList &&
                            track.status == FollowStatus.UNFOLLOWED.long -> null

                        else -> track to service
                    }
                }

            if (bindings.isEmpty()) {
                return@supervisorScope emptyList()
            }

            val refreshed = bindings.map { (track, service) ->
                async(Dispatchers.IO) {
                    try {
                        val remote = service.refresh(
                            track.toDbTrack(),
                        ).toDomainTrack(idRequired = false)
                            ?: throw IllegalStateException(
                                "Tracker returned an invalid track",
                            )

                        RefreshResult.Success(
                            track = remote.copy(
                                id = track.id,
                                mangaId = track.mangaId,
                                trackerId = track.trackerId,
                            ),
                            service = service,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        RefreshResult.Failure(service, e)
                    }
                }
            }.awaitAll()

            val failures = refreshed
                .filterIsInstance<RefreshResult.Failure>()
                .map { it.service to it.error }

            val successful = refreshed
                .filterIsInstance<RefreshResult.Success>()

            if (successful.isEmpty()) {
                return@supervisorScope failures
            }

            // Rereading progress is tracked independently from
            // completed bindings. Do not use completed progress
            // as the base for a new rereading pass.
            val rereading = successful.filter {
                it.service.getRereadingStatus() == it.track.status
            }

            val candidates = if (rereading.isNotEmpty()) {
                successful.filter {
                    it.track.status != it.service.getCompletionStatus()
                }
            } else {
                successful
            }

            if (candidates.isEmpty()) {
                return@supervisorScope failures
            }

            val previousProgress = if (rereading.isNotEmpty()) {
                rereading.maxOf { it.track.lastChapterRead }
            } else {
                candidates.maxOf { it.track.lastChapterRead }
            }

            val targetProgress = max(
                0.0,
                previousProgress + delta.toDouble(),
            )

            val writeFailures = candidates.map { result ->
                async(Dispatchers.IO) {
                    try {
                        val candidate = result.track.copy(
                            lastChapterRead = targetProgress,
                        )

                        val returned = result.service.update(
                            candidate.toDbTrack(),
                            true,
                        )

                        val persisted = returned
                            .toDomainTrack(idRequired = false)
                            ?.copy(
                                id = candidate.id,
                                mangaId = candidate.mangaId,
                                trackerId = candidate.trackerId,
                            )
                            ?: candidate

                        upsertTrack.await(persisted)
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        result.service to e
                    }
                }
            }
                .awaitAll()
                .filterNotNull()

            failures + writeFailures
        }
    }

    private sealed interface RefreshResult {

        data class Success(
            val track: Track,
            val service: Tracker,
        ) : RefreshResult

        data class Failure(
            val service: Tracker,
            val error: Throwable,
        ) : RefreshResult
    }
}
