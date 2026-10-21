package eu.kanade.domain.track.interactor

import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import exh.md.utils.FollowStatus
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.i18n.MR
import kotlin.math.abs
import kotlin.time.Instant

// KNS -->
/**
 * Applies one change to every tracker a manga is linked with: the unified tracker editor's write path.
 *
 * Each service starts from its refreshed remote state and gets the change in its own terms: a status is
 * translated to the equivalent status of that service (skipped when it has none), a score is converted
 * through the ten-point scale to the closest score the service offers, dates are skipped for services
 * without reading dates. Services run concurrently, and one failing service is reported without undoing
 * the others (see [Result]).
 */
@Inject
class UpdateTracks(
    private val getTracks: GetTracks,
    private val manager: TrackerManager,
    private val upsertTrack: UpsertTrack,
    private val deleteTrack: DeleteTrack,
) {
    class InvalidDate : IllegalArgumentException()

    sealed interface Change {
        data class Progress(val chapter: Int) : Change

        /** [selection] is an entry of the score list of [sourceId], the tracker the score was picked on. */
        data class Score(val sourceId: Long, val selection: String) : Change

        /** [status] is a status of [sourceId]. */
        data class Status(val sourceId: Long, val status: Long) : Change

        /** Epoch milliseconds; 0 removes the date. */
        data class Date(val start: Boolean, val millis: Long) : Change
    }

    data class Result(
        val updatedTrackerIds: Set<Long>,
        val failedTrackerIds: Set<Long>,
        val skippedTrackerIds: Set<Long>,
        val failures: List<Pair<Tracker, Throwable>>,
    )

    suspend fun await(mangaId: Long, change: Change): Result {
        val bindings = getTracks.await(mangaId)
        val entries = bindings.mapNotNull { binding ->
            manager.get(binding.trackerId)?.takeIf { it.isLoggedIn }?.let { it to binding }
        }
        val results = trackerBatch(entries, upsertTrack::awaitOrThrow) {
            // Unsupported changes skip the service before any network request.
            if (change is Change.Score && service.getScoreList().isEmpty()) return@trackerBatch skip()
            if (change is Change.Date && !service.supportsReadingDates) return@trackerBatch skip()
            if (change is Change.Progress && service is MdList && binding.status == FollowStatus.UNFOLLOWED.long) {
                return@trackerBatch skip()
            }
            val current = refresh()
            val edited = when (change) {
                is Change.Progress -> {
                    require(change.chapter >= 0)
                    current.copy(lastChapterRead = change.chapter.toDouble())
                }
                is Change.Score -> {
                    val source = requireNotNull(manager.get(change.sourceId))
                    val sourceBinding = requireNotNull(bindings.find { it.trackerId == source.id })
                    val index = source.getScoreList().indexOf(change.selection)
                    require(index >= 0)
                    val normalized = source.get10PointScore(sourceBinding.copy(score = source.indexToScore(index)))
                    val score = service.getScoreList().indices.map(service::indexToScore).minBy { candidate ->
                        abs(service.get10PointScore(current.copy(score = candidate)) - normalized)
                    }
                    current.copy(score = score)
                }
                is Change.Status -> {
                    val source = requireNotNull(manager.get(change.sourceId))
                    val status = equivalentStatus(source, change.status, service) ?: return@trackerBatch skip()
                    val completed = status == service.getCompletionStatus() && current.totalChapters > 0
                    current.copy(
                        status = status,
                        lastChapterRead = if (completed) current.totalChapters.toDouble() else current.lastChapterRead,
                    )
                }
                is Change.Date -> {
                    require(change.millis >= 0)
                    val date = change.millis.takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds)
                    val start = current.startDate
                    val finish = current.finishDate
                    if (change.start) {
                        if (date != null && finish != null && date > finish) throw InvalidDate()
                        current.copy(startDate = date)
                    } else {
                        if (date != null && start != null && date < start) throw InvalidDate()
                        current.copy(finishDate = date)
                    }
                }
            }
            update(edited, didReadChapter = change is Change.Progress)
        }
        return Result(
            updatedTrackerIds = results.filter { it.failures.isEmpty() && !it.skipped }
                .mapTo(mutableSetOf()) { it.service.id },
            failedTrackerIds = results.filter { it.failures.isNotEmpty() }.mapTo(mutableSetOf()) { it.service.id },
            skippedTrackerIds = results.filter { it.failures.isEmpty() && it.skipped }
                .mapTo(mutableSetOf()) { it.service.id },
            failures = results.flatMap { result -> result.failures.map { result.service to it } },
        )
    }

    /** Unlinks [trackerIds] locally, and also on the services when [remotely]; failures are reported per service. */
    suspend fun remove(mangaId: Long, trackerIds: Set<Long>, remotely: Boolean): List<Pair<Tracker?, Throwable>> {
        val failures = mutableListOf<Pair<Tracker?, Throwable>>()
        for (track in getTracks.await(mangaId).filter { it.trackerId in trackerIds }) {
            val service = manager.get(track.trackerId)
            if (remotely) {
                try {
                    when (service) {
                        is DeletableTracker -> service.delete(track)
                        is MdList -> service.update(track.copy(status = FollowStatus.UNFOLLOWED.long).toDbTrack())
                        else -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += service to e
                }
            }
            deleteTrack.await(mangaId, track.trackerId)
        }
        return failures
    }

    private fun equivalentStatus(source: Tracker, status: Long, target: Tracker): Long? {
        if (source.id == target.id) return status
        val label = statusLabel(source.getStatus(status)) ?: return null
        return target.getStatusList().firstOrNull { statusLabel(target.getStatus(it)) == label }
    }

    // MangaUpdates names its lists after the reading states other trackers use.
    private fun statusLabel(label: StringResource?): StringResource? = when (label) {
        MR.strings.reading_list -> MR.strings.reading
        MR.strings.complete_list -> MR.strings.completed
        MR.strings.wish_list -> MR.strings.plan_to_read
        MR.strings.unfinished_list -> MR.strings.dropped
        MR.strings.on_hold_list, MR.strings.paused -> MR.strings.on_hold
        else -> label
    }
}
// KNS <--
