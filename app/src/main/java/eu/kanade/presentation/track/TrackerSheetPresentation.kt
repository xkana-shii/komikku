package eu.kanade.presentation.track

import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.lang.toJavaLocalDate
import tachiyomi.domain.track.service.TrackerProgressSync
import tachiyomi.i18n.MR
import java.time.format.DateTimeFormatter

// KNS --> the previous personal fork's unified tracker card: what it shows and what its icons do
/** Enhanced trackers bind by matching the source, so their entry cannot be searched for. */
internal val TrackItem.canChangeEntry: Boolean get() = tracker !is EnhancedTracker

internal enum class UnifiedTrackerIconAction {
    OPEN,
    SEARCH,
    COPY_LINK,
    SET_PREFERRED,
    NONE,
}

/** A tap binds an untracked tracker, opens a tracked one, and in edit mode changes its entry. */
internal fun TrackItem.unifiedIconClickAction(editMode: Boolean): UnifiedTrackerIconAction = when {
    track == null -> UnifiedTrackerIconAction.SEARCH
    !editMode -> UnifiedTrackerIconAction.OPEN
    canChangeEntry -> UnifiedTrackerIconAction.SEARCH
    else -> UnifiedTrackerIconAction.NONE
}

/** A long press copies a tracked tracker's link, and in edit mode makes it the preferred tracker. */
internal fun TrackItem.unifiedIconLongPressAction(editMode: Boolean): UnifiedTrackerIconAction = when {
    track == null -> UnifiedTrackerIconAction.NONE
    editMode -> UnifiedTrackerIconAction.SET_PREFERRED
    else -> UnifiedTrackerIconAction.COPY_LINK
}

/**
 * The values of the unified card: those of the preferred tracker (else the one furthest ahead), which
 * unified edits start from, plus which trackers differ from it or failed their last unified edit.
 */
internal class TrackerSheetPresentation(
    items: List<TrackItem>,
    preferredId: Long?,
    dateFormat: DateTimeFormatter,
    failedTrackerIds: Set<Long>,
) {
    val bound = items.filter { it.track != null }
    private val tracks = bound.mapNotNull { it.track }
    private val resolved = TrackerProgressSync.resolvePreferredTrack(tracks, preferredId)
    val primary = bound.find { it.track == resolved }

    /** The tracker marked as preferred; none while no preferred tracker applies to this entry. */
    val preferredTrackerId = preferredId?.takeIf { id -> bound.any { it.tracker.id == id } }
    val supportsScore = primary?.tracker?.getScoreList()?.isNotEmpty() == true
    val supportsReadingDates = primary?.tracker?.supportsReadingDates == true
    val status = primary?.let { it.tracker.getStatus(it.track!!.status) } ?: MR.strings.reading
    val score = primary?.takeIf { supportsScore }?.let { item ->
        item.track!!.takeIf { it.score != 0.0 }?.let(item.tracker::displayScore)?.takeIf(String::isNotBlank)
    }

    /** Scores without their own star (10-point and 100-point ones) get the star icon. */
    val appendScoreStar = score != null && '★' !in score
    val startDate = primary?.track?.startDate?.takeIf { supportsReadingDates }
        ?.let { dateFormat.format(it.toJavaLocalDate()) }
    val finishDate = primary?.track?.finishDate?.takeIf { supportsReadingDates }
        ?.let { dateFormat.format(it.toJavaLocalDate()) }
    val chapters = primary?.track?.let { track ->
        val read = track.lastChapterRead.toInt()
        if (track.totalChapters > 0) "$read/${track.totalChapters}" else "$read"
    } ?: "0"
    val mismatchedIds = TrackerProgressSync.mismatchedIds(tracks, preferredId)
    val errorIds = bound.map { it.tracker.id }.toSet() intersect failedTrackerIds
}
// KNS <--
