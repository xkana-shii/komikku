package eu.kanade.domain.track.interactor

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import exh.md.utils.FollowStatus
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.MR
import kotlin.math.abs

// KMK --> Shared edits use each service's capabilities and converters, never a preferred-only write.
class UpdateTracks(
    private val getTracks: GetTracks,
    private val manager: TrackerManager,
    private val insert: InsertTrack,
    private val delete: DeleteTrack,
) {
    class InvalidDate : IllegalArgumentException()

    sealed interface Change {
        data class Progress(val chapter: Int) : Change
        data class Score(val sourceId: Long, val selection: String) : Change
        data class Status(val sourceId: Long, val status: Long) : Change
        data class Date(val start: Boolean, val millis: Long) : Change
    }

    suspend fun await(mangaId: Long, change: Change): List<Pair<Tracker?, Throwable>> {
        val bindings = getTracks.await(mangaId)
        val entries = bindings.mapNotNull { binding ->
            manager.get(binding.trackerId)?.takeIf { it.isLoggedIn }?.let { it to binding }
        }
        return trackerBatch(entries, insert) {
            // Check support before networking; unsupported services retain their binding unchanged.
            if (change is Change.Score && service.getScoreList().isEmpty()) return@trackerBatch
            if (change is Change.Date && !service.supportsReadingDates) return@trackerBatch
            val current = refresh()
            val edited = when (change) {
                is Change.Progress -> {
                    require(change.chapter >= 0)
                    if (service is MdList && current.status == FollowStatus.UNFOLLOWED.long) return@trackerBatch
                    current.copy(lastChapterRead = change.chapter.toDouble())
                }
                is Change.Score -> {
                    val source = requireNotNull(manager.get(change.sourceId))
                    val sourceBinding = requireNotNull(bindings.find { it.trackerId == source.id })
                    val index = source.getScoreList().indexOf(change.selection)
                    require(index >= 0)
                    val normalized = source.get10PointScore(sourceBinding.copy(score = source.indexToScore(index)))
                    // Compare the tracker's own supported values on its existing ten-point scale.
                    val score = service.getScoreList().indices.map(service::indexToScore).minBy { candidate ->
                        abs(service.get10PointScore(current.copy(score = candidate)) - normalized)
                    }
                    current.copy(score = score)
                }
                is Change.Status -> {
                    val source = requireNotNull(manager.get(change.sourceId))
                    val status = equivalentStatus(source, change.status, service) ?: return@trackerBatch
                    current.copy(status = status, lastChapterRead = if (status == service.getCompletionStatus() && current.totalChapters > 0) current.totalChapters.toDouble() else current.lastChapterRead)
                }
                is Change.Date -> {
                    require(change.millis >= 0)
                    if (change.start) {
                        if (change.millis != 0L && current.finishDate != 0L && change.millis > current.finishDate) throw InvalidDate()
                        current.copy(startDate = change.millis)
                    } else {
                        if (change.millis != 0L && current.startDate != 0L && change.millis < current.startDate) throw InvalidDate()
                        current.copy(finishDate = change.millis)
                    }
                }
            }
            update(edited, change is Change.Progress)
        }.flatMap { result -> result.failures.map { result.service to it } }
    }

    suspend fun remove(mangaId: Long, selected: Set<Long>, remotely: Boolean): List<Pair<Tracker?, Throwable>> {
        val failures = mutableListOf<Pair<Tracker?, Throwable>>()
        for (track in getTracks.await(mangaId).filter { it.trackerId in selected }) {
            val service = manager.get(track.trackerId)
            try {
                if (remotely) {
                    when (service) {
                        is DeletableTracker -> service.delete(track)
                        is MdList -> service.update(track.copy(status = FollowStatus.UNFOLLOWED.long).toDbTrack())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Match existing behavior: report remote failures independently of local unlinking.
                failures += service to e
            }
            try {
                delete.awaitOrThrow(mangaId, track.trackerId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += service to e
            }
        }
        return failures
    }

    private fun equivalentStatus(source: Tracker, status: Long, target: Tracker): Long? {
        if (source.id == target.id) return status
        val label = statusLabel(source.getStatus(status)) ?: return null
        return target.getStatusList().firstOrNull { statusLabel(target.getStatus(it)) == label }
    }

    // MangaUpdates uses list names for the same reading states used by other trackers.
    private fun statusLabel(label: StringResource?): StringResource? = when (label) {
        MR.strings.reading_list -> MR.strings.reading
        MR.strings.complete_list -> MR.strings.completed
        MR.strings.wish_list -> MR.strings.plan_to_read
        MR.strings.unfinished_list -> MR.strings.dropped
        MR.strings.on_hold_list, MR.strings.paused -> MR.strings.on_hold
        else -> label
    }
}
// KMK <--
