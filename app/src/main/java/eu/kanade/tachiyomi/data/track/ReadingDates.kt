package eu.kanade.tachiyomi.data.track

// KNS -->
/**
 * Reading dates set automatically by the trackers: they only fill a missing date (0 = none), so a
 * reread never replaces the dates of the first reading. Dates the user picks are written directly.
 */
object ReadingDates {
    /** The start date after reading starts (chapter 1 read) at [now]. */
    fun started(current: Long, now: Long): Long = if (current == 0L) now else current

    /** The finish date after the entry is completed at [now]. */
    fun finished(current: Long, now: Long): Long = if (current == 0L) now else current
}
// KNS <--
