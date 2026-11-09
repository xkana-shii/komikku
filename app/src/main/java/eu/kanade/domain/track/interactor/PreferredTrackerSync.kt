package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import kotlin.math.abs
import kotlin.time.Instant

// KNS -->
/**
 * Keeps every other linked tracker of a manga equal to the preferred tracker (the manga's own choice, else the
 * priority tracker) for the fields trackers share: status, chapters read, score and reading dates. The preferred
 * tracker is only ever read; a destination is written only when a field really differs, from its freshly refreshed
 * remote record, so everything else on it (private flag, notes, list data) is kept.
 *
 * It runs from [UnifiedTrackerEdits.sync], which serialises it with the unified editor, never from preference or
 * database emissions, and its own writes are saved straight to the database without going through any entry
 * point that syncs again, so a propagated update can never start another round.
 */
@Inject
class PreferredTrackerSync(
    private val getTracks: GetTracks,
    private val manager: TrackerManager,
    private val upsertTrack: UpsertTrack,
    private val trackPreferences: TrackPreferences,
    private val delayedTrackingStore: DelayedTrackingStore,
) {

    data class Outcome(
        val preferredTrackerId: Long,
        /** Destinations that were written. */
        val written: Set<Long>,
        /** Destinations that already matched, or have none of the fields to match. */
        val unchanged: Set<Long>,
        val failures: List<Pair<Tracker, Throwable>>,
    ) {
        /** Written and already matching trackers count as synchronised, which clears their earlier error. */
        fun toResult() = UpdateTracks.Result(
            updatedTrackerIds = written + unchanged,
            failedTrackerIds = failures.mapTo(mutableSetOf()) { it.first.id },
            skippedTrackerIds = emptySet(),
            failures = failures,
        )
    }

    /**
     * @param unreliableTrackerIds trackers whose record cannot be trusted right now (their last update or refresh
     * failed). When the preferred tracker is one of them nothing is propagated, so a failed edit is not undone with
     * the stale values the preferred tracker still has.
     * @return null when there is nothing to synchronise: fewer than two linked trackers, no preferred tracker
     * among them, or an unreliable preferred tracker.
     */
    suspend fun run(mangaId: Long, unreliableTrackerIds: Set<Long> = emptySet()): Outcome? {
        val bindings = getTracks.await(mangaId)
        val entries = bindings.mapNotNull { binding ->
            manager.get(binding.trackerId)?.takeIf { it.isLoggedIn }?.let { it to binding }
        }.distinctBy { it.first.id }
        if (entries.size < 2) return null

        val preferredId = trackPreferences.resolvePreferredTracker(mangaId, entries.mapTo(mutableSetOf()) { it.first.id })
            ?: return null
        if (preferredId in unreliableTrackerIds) return null
        val (source, sourceTrack) = entries.first { it.first.id == preferredId }

        // An update that could not reach a tracker yet is queued: until it has, a lower value there is not a
        // deliberate change, so progress is never lowered.
        val allowProgressDecrease = !delayedTrackingStore.hasPending(bindings.map { it.id })

        val results = trackerBatch(entries.filter { it.first.id != preferredId }, upsertTrack::awaitOrThrow) {
            // Compare with the saved records first: a manga that is already in step costs no request at all
            if (PreferredTrackerReconciler.reconcile(source, sourceTrack, service, binding, allowProgressDecrease) == null) {
                return@trackerBatch
            }
            val current = refresh()
            val edited = PreferredTrackerReconciler.reconcile(source, sourceTrack, service, current, allowProgressDecrease)
                ?: return@trackerBatch
            update(edited, didReadChapter = false)
        }
        return Outcome(
            preferredTrackerId = preferredId,
            written = results.filter { it.failures.isEmpty() && it.track != null }.mapTo(mutableSetOf()) { it.service.id },
            unchanged = results.filter { it.failures.isEmpty() && it.track == null }.mapTo(mutableSetOf()) { it.service.id },
            failures = results.flatMap { result -> result.failures.map { result.service to it } },
        )
    }
}

/** Which fields of a destination differ from the preferred tracker's record, and what they become. */
internal object PreferredTrackerReconciler {

    private const val PROGRESS_TOLERANCE = 0.01
    private const val SCORE_TOLERANCE = 1e-6

    /**
     * The [destination] record changed to match [sourceTrack] in every field it supports, or null when it already
     * matches. Fields are independent: an unsupported or untranslatable one is left as it is.
     */
    fun reconcile(
        source: Tracker,
        sourceTrack: Track,
        destination: Tracker,
        destinationTrack: Track,
        allowProgressDecrease: Boolean,
    ): Track? {
        var edited = destinationTrack

        // Status: the destination's equivalent status; none (or an untranslatable one) keeps its own
        UpdateTracks.equivalentStatus(source, sourceTrack.status, destination)
            ?.takeIf { it != edited.status }
            ?.let { edited = edited.copy(status = it) }

        // Chapters read: the preferred tracker's value, but lowered only when allowed
        val progress = sourceTrack.lastChapterRead
        if (progress.isFinite() && progress >= 0 && abs(edited.lastChapterRead - progress) > PROGRESS_TOLERANCE) {
            if (progress > edited.lastChapterRead || allowProgressDecrease) {
                edited = edited.copy(lastChapterRead = progress)
            }
        }

        // Score: through the ten-point scale to the closest score the destination offers. An unscored preferred
        // entry carries no score to copy (0 is "none" on most services and cannot be told from "unknown").
        if (sourceTrack.score != 0.0 && destination.getScoreList().isNotEmpty() && source.getScoreList().isNotEmpty()) {
            val normalized = source.get10PointScore(sourceTrack)
            val closest = destination.getScoreList().indices.map(destination::indexToScore).minByOrNull { candidate ->
                abs(destination.get10PointScore(edited.copy(score = candidate)) - normalized)
            }
            if (
                closest != null &&
                abs(destination.get10PointScore(edited.copy(score = closest)) - destination.get10PointScore(edited)) >
                SCORE_TOLERANCE
            ) {
                edited = edited.copy(score = closest)
            }
        }

        // Dates: the same day, so a service that stores another time of day does not differ forever. An absent
        // date on the preferred tracker is a removal, as it reads reading dates itself.
        if (source.supportsReadingDates && destination.supportsReadingDates) {
            val start = if (sameDay(sourceTrack.startDate, edited.startDate)) edited.startDate else sourceTrack.startDate
            val finish = if (sameDay(sourceTrack.finishDate, edited.finishDate)) edited.finishDate else sourceTrack.finishDate
            val ordered = start == null || finish == null || start <= finish
            if (ordered && (start != edited.startDate || finish != edited.finishDate)) {
                edited = edited.copy(startDate = start, finishDate = finish)
            }
        }

        return edited.takeIf { it != destinationTrack }
    }

    internal fun sameDay(a: Instant?, b: Instant?, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        if (a == null || b == null) return a == b
        return a.toLocalDateTime(zone).date == b.toLocalDateTime(zone).date
    }
}
// KNS <--
