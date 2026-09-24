package eu.kanade.domain.track.interactor

import android.app.Application
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.util.system.toast
import exh.md.utils.FollowStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.service.TrackerProgressSync
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RefreshTracks(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val preferences: TrackPreferences = Injekt.get(),
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     * Auto sync controls both service reconciliation and importing progress into local chapters.
     *
     * @return Failed updates.
     */
    suspend fun await(mangaId: Long): List<Pair<Tracker?, Throwable>> {
        return supervisorScope {
            val tracks = getTracks.await(mangaId).filter { trackerManager.get(it.trackerId)?.isLoggedIn == true }
            val preferred = preferences.resolvePreferredTracker(mangaId, tracks.map { it.trackerId }.toSet())
            val refreshed = tracks.map { track ->
                async {
                    val service = trackerManager.get(track.trackerId)!!
                    try {
                        val updated = service.refresh(track.toDbTrack()).toDomainTrack()!!
                        insertTrack.await(updated)
                        Triple(service, updated, null as Throwable?)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Triple(service, null, e)
                    }
                }
            }.awaitAll()
            val errors = refreshed.mapNotNull { (service, _, error) -> error?.let { service to it } }.toMutableList()
            val successful = refreshed.mapNotNull { (service, track, _) -> track?.let { service to it } }
            val eligible = successful.filterNot { (service, track) ->
                service is MdList && track.status == FollowStatus.UNFOLLOWED.long
            }
            val sync = preferences.autoSyncProgressFromTrackers().get()
            // Never propagate a stale preferred value when its refresh failed.
            val preferredRefreshFailed = preferred != null && errors.any { it.first.id == preferred }
            val rereading = refreshed.any { (service, track, _) ->
                isRereading(service, track ?: tracks.first { it.trackerId == service.id })
            }
            val canSync = !preferredRefreshFailed && !rereading && eligible.size > 1
            val target = TrackerProgressSync.progress(eligible.map { it.second }, preferred)
            for ((service, track) in eligible) {
                try {
                    var current = track
                    if (sync && canSync && target > track.lastChapterRead) {
                        // Automatic reconciliation only advances progress, preserving reread/reset workflows.
                        current = service.update(track.copy(lastChapterRead = target).toDbTrack()).toDomainTrack()!!
                        insertTrack.await(current)
                    }
                    val shouldSyncLocal = preferred == null || service.id == preferred
                    val progress = if (sync && shouldSyncLocal && !rereading) {
                        syncChapterProgressWithTrack.sync(mangaId, current, service, propagateErrors = true)
                    } else {
                        null
                    }
                    progress?.let {
                        val context = Injekt.get<Application>()
                        withUIContext { context.toast(context.stringResource(KMR.strings.sync_progress_from_trackers_up_to_chapter, it)) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors += service to e
                }
            }
            errors
        }
    }
    // KMK --> Explicit +/- controls apply to every eligible service. Refresh first so
    // cached progress cannot overwrite a newer remote value; keep failures per service.
    suspend fun adjustProgress(mangaId: Long, delta: Int): List<Pair<Tracker?, Throwable>> = supervisorScope {
        require(delta == -1 || delta == 1)
        val tracks = getTracks.await(mangaId).filter { trackerManager.get(it.trackerId)?.isLoggedIn == true }
        val refreshed = trackerBatch(tracks.map { trackerManager.get(it.trackerId)!! to it }, insertTrack) { refresh() }

        val errors = refreshed.mapNotNull { (service, _, error) -> error?.let { service to it } }.toMutableList()
        val eligible = refreshed.mapNotNull { (service, track, _) ->
            track?.takeUnless { service is MdList && it.status == FollowStatus.UNFOLLOWED.long }?.let { service to it }
        }
        // Rereading progress is a separate pass; never advance it from completed trackers.
        val targets = eligible.groupBy { (service, track) -> isRereading(service, track) }.values.flatMap { group ->
            val target = (TrackerProgressSync.maxProgress(group.map { it.second }).toInt() + delta).coerceAtLeast(0).toDouble()
            group.map { (service, track) -> service to track.copy(lastChapterRead = target) }
        }
        errors += trackerBatch(targets, insertTrack) { update(binding, true) }
            .flatMap { result -> result.failures.map { result.service to it } }

        errors
    }

    private fun isRereading(service: Tracker, track: Track): Boolean =
        !(service is MdList && track.status == FollowStatus.UNFOLLOWED.long) &&
            service.getRereadingStatus() != service.getReadingStatus() && track.status == service.getRereadingStatus()
    // KMK <--
}
