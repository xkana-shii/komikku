package eu.kanade.domain.track.interactor

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.i18n.kmk.KMR

@Inject
class RefreshTracks(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val upsertTrack: UpsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val context: Context,
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     * Also sync chapter progress with the [EnhancedTracker] or all trackers based on [enhancedTrackersOnly].
     *
     * @return Failed updates.
     */
    suspend fun await(
        mangaId: Long,
        // KMK -->
        enhancedTrackersOnly: Boolean = true,
        // KMK <--
    ): List<Pair<Tracker?, Throwable>> {
        return supervisorScope {
            return@supervisorScope getTracks.await(mangaId)
                .map { it to trackerManager.get(it.trackerId) }
                .filter { (_, service) -> service?.isLoggedIn == true }
                .map { (track, service) ->
                    async(Dispatchers.IO) {
                        return@async try {
                            val updatedTrack = service!!.refresh(track.toDbTrack()).toDomainTrack()!!
                            upsertTrack.await(updatedTrack)
                            // KMK -->
                            if (!enhancedTrackersOnly) {
                                syncChapterProgressWithTrack.sync(mangaId, updatedTrack, service)
                            } else {
                                // KMK <--
                                syncChapterProgressWithTrack.await(mangaId, updatedTrack, service)
                            }
                                // KMK -->
                                ?.let {
                                    withUIContext {
                                        context.toast(context.stringResource(KMR.strings.sync_progress_from_trackers_up_to_chapter, it))
                                    }
                                }
                            // KMK <--
                            null
                        } catch (e: Throwable) {
                            service to e
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }
}
