package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Inject
class FetchInterval(
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    /**
     * Works out the entry's fetch interval and the next day to check it for new chapters.
     *
     * @param foundRelease whether the check that led here found new chapters.
     */
    suspend fun withFetchInterval(
        manga: Manga,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: ClosedRange<Instant>?,
        foundRelease: Boolean = false,
    ): MangaUpdate {
        val today = dateTime.date
        val currentWindow = window ?: getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        val schedule = if (manga.fetchInterval < 0) {
            // The check that found a release is the entry's last update
            val lastUpdate = if (foundRelease) today else manga.lastUpdate?.toLocalDateTime(timeZone)?.date
            ReleaseSchedule.fixed(-manga.fetchInterval, lastUpdate ?: today)
        } else {
            val chapters = getChaptersByMangaId.await(manga.id, applyFilter = true)
            ReleaseSchedule.estimate(chapters, timeZone, today)
        }

        val expected = manga.nextUpdate
        val expectedDay = expected?.toLocalDateTime(timeZone)?.date
        // A release on or after the expected day means the next one is counted from it instead
        val isAwaitingRelease = expected != null &&
            expectedDay != null &&
            expected in currentWindow.start..currentWindow.endInclusive + 1.milliseconds &&
            (schedule.lastRelease == null || schedule.lastRelease < expectedDay)
        val nextUpdate = when {
            foundRelease -> schedule.nextCheckAfterRelease(today).atStartOfDayIn(timeZone)
            isAwaitingRelease -> expected
            else -> schedule.nextCheck(today).atStartOfDayIn(timeZone)
        }

        return MangaUpdate(manga.id) {
            this.nextUpdate = nextUpdate
            fetchInterval = if (manga.fetchInterval < 0) manga.fetchInterval else schedule.interval
        }
    }

    fun getWindow(localDateTime: LocalDate, timeZone: TimeZone): ClosedRange<Instant> {
        val today = localDateTime.atStartOfDayIn(timeZone)
        return (today - GRACE_PERIOD.days)..(today + GRACE_PERIOD.days)
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L

        // KMK -->
        const val MANUAL_DISABLE = 99999 // 274 years in future
        // KMK <--
    }
}

/** How often an entry releases, and so when to check it for the next release. */
internal data class ReleaseSchedule(
    /** Days between releases, which is what the fetch interval shows. */
    val interval: Int,
    /** The most recent release day, which the next ones are counted from. */
    val lastRelease: LocalDate?,
    val pattern: ReleasePattern,
    /** Days past the expected day a release can still come, from how late recent ones were. */
    val lateness: Int = 0,
    /** Whether checks spread out once releases stop coming, which a schedule set by the user doesn't. */
    val backsOff: Boolean = true,
    /** Lengths in days of earlier breaks in releases, which a break like a season's end is expected to last again. */
    val pastBreaks: List<Int> = emptyList(),
) {

    /** The next day to check for a release, as of [today]. Today counts, so a due release keeps being checked for. */
    fun nextCheck(today: LocalDate): LocalDate {
        val anchor = lastRelease ?: today
        val daysSinceRelease = anchor.daysUntil(today)
        val isOnBreak = backsOff && daysSinceRelease > interval * HIATUS_CYCLES
        // Around when earlier breaks ended, this one likely ends too, so the schedule is followed again
        if (isOnBreak && pastBreaks.none { daysSinceRelease in it - interval..it + interval }) {
            // Past a few missed releases the schedule no longer holds, so checks spread out until it resumes
            val backedOff = backOff(daysSinceRelease)
            val next = anchor.plus(DatePeriod(days = ceilDiv(daysSinceRelease, backedOff) * backedOff))
            val breakEnd = pastBreaks.map {
                anchor.plus(DatePeriod(days = it - interval))
            }.filter { it > today }.minOrNull()
            return minOf(next, breakEnd ?: next)
        }

        val due = pattern.nextAfter(pattern.nearest(anchor))
        if (today in due..due.plus(DatePeriod(days = lateness))) return today
        return generateSequence(due, pattern::nextAfter).first { it >= today }.cappedFrom(today)
    }

    /**
     * The next day to check after a check [today] found a release.
     *
     * The release is dated by its newest chapter, since a check can turn up one that came out on an earlier day. It
     * stands in for the expected day nearest to it, so an early, late or extra release leaves the schedule in place.
     */
    fun nextCheckAfterRelease(today: LocalDate): LocalDate {
        val release = lastRelease?.coerceAtMost(today) ?: today
        // What was found is out already, so checking again today would only find it again
        val next = pattern.nextAfter(pattern.nearest(release))
        return generateSequence(next, pattern::nextAfter).first { it > today }.cappedFrom(today)
    }

    // Months and batches can be further apart than a check should ever wait
    private fun LocalDate.cappedFrom(today: LocalDate): LocalDate {
        return coerceAtMost(today.plus(DatePeriod(days = FetchInterval.MAX_INTERVAL)))
    }

    private fun backOff(daysSinceRelease: Int): Int {
        var backedOff = interval
        while (backedOff < FetchInterval.MAX_INTERVAL && daysSinceRelease > backedOff * HIATUS_CYCLES) {
            backedOff *= 2
        }
        return backedOff.coerceAtMost(FetchInterval.MAX_INTERVAL)
    }

    private fun ceilDiv(a: Int, b: Int) = -Math.floorDiv(-a, b)

    companion object {
        private const val DEFAULT_INTERVAL = 7
        private const val HIATUS_CYCLES = 4
        private const val MIN_RELEASES = 3
        private const val FEW_CHAPTERS = 8
        private const val FEW_CHAPTERS_RELEASES = 3
        private const val RECENT_RELEASES = 10
        private const val PHASE_RELEASES = 5

        /** A schedule the user set to every [interval] days, counted from [lastRelease]. */
        fun fixed(interval: Int, lastRelease: LocalDate): ReleaseSchedule {
            val pattern = ReleasePattern.EveryDays(interval, onSchedule = lastRelease)
            return ReleaseSchedule(interval, lastRelease, pattern, backsOff = false)
        }

        /** Estimates the schedule from the recent release days of [chapters], by upload date or else fetch date. */
        fun estimate(chapters: List<Chapter>, zone: TimeZone, today: LocalDate): ReleaseSchedule {
            val recentReleases = if (chapters.size <= FEW_CHAPTERS) FEW_CHAPTERS_RELEASES else RECENT_RELEASES
            // Sources that date chapters ahead of their release would otherwise pull the schedule into the future
            val latestPlausibleDay = today.plus(DatePeriod(days = 1))
            fun releaseDays(dates: Sequence<Instant>): List<LocalDate> {
                return dates
                    .map { it.toLocalDateTime(zone).date }
                    .filter { it <= latestPlausibleDay }
                    .distinct()
                    .sortedDescending()
                    .toList()
            }

            val uploadDays = releaseDays(chapters.asSequence().mapNotNull { it.dateUpload })
            val fetchDays = releaseDays(chapters.asSequence().map { it.dateFetch })
            val history = uploadDays.takeIf { it.size >= MIN_RELEASES }
                ?: fetchDays.takeIf { it.size >= MIN_RELEASES }
                ?: run {
                    val lastRelease = uploadDays.firstOrNull() ?: fetchDays.firstOrNull()
                    val pattern = ReleasePattern.EveryDays(DEFAULT_INTERVAL, onSchedule = lastRelease ?: today)
                    return ReleaseSchedule(DEFAULT_INTERVAL, lastRelease, pattern)
                }
            val days = history.take(recentReleases)

            val gaps = days.zipWithNext { newer, older -> older.daysUntil(newer) }.sorted()
            val medianGap = gaps[(gaps.size - 1) / 2]
            val detected = ReleasePattern.detect(days, history, medianGap)
            // Within a batch releases are a day apart, but it's the batches that come every so often
            val interval = ((detected as? ReleasePattern.Batches)?.every ?: medianGap)
                .coerceIn(1, FetchInterval.MAX_INTERVAL)
            return ReleaseSchedule(
                interval = interval,
                lastRelease = days.first(),
                pattern = detected
                    ?: ReleasePattern.EveryDays(
                        interval,
                        onSchedule = medianPhase(days.take(PHASE_RELEASES), interval),
                    ),
                lateness = (gaps.last() - interval).coerceIn(0, interval / 2),
                pastBreaks = history
                    .zipWithNext { newer, older -> older.daysUntil(newer) }
                    .filter { it > interval * HIATUS_CYCLES },
            )
        }

        /**
         * The release in [days], newest first, that the others land closest to in step with [interval], so one early
         * or late release doesn't shift the schedule. Ties go to the newest, which follows a schedule that moved.
         */
        private fun medianPhase(days: List<LocalDate>, interval: Int): LocalDate {
            fun offset(
                from: LocalDate,
                to: LocalDate,
            ) = from.daysUntil(to).mod(interval).let { minOf(it, interval - it) }
            val phase = days.minBy { candidate -> days.sumOf { offset(candidate, it) } }
            val onPhase = days.count { offset(phase, it) == 0 }
            return if (onPhase * 5 >= days.size * 3) phase else days.first()
        }
    }
}

/** The days an entry is expected to release on. */
internal sealed interface ReleasePattern {

    /** The first expected release day after [date]. */
    fun nextAfter(date: LocalDate): LocalDate

    /** The last expected release day before [date]. */
    fun previousBefore(date: LocalDate): LocalDate

    /** The expected release day closest to [date], which a release on [date] stands in for. */
    fun nearest(date: LocalDate): LocalDate {
        val before = previousBefore(date.plus(DatePeriod(days = 1)))
        val after = nextAfter(date.minus(DatePeriod(days = 1)))
        // A release halfway between is taken as late, so the next check comes sooner
        return if (before.daysUntil(date) <= date.daysUntil(after)) before else after
    }

    /** Releases every [days] days, in step with [onSchedule]. */
    data class EveryDays(val days: Int, val onSchedule: LocalDate) : ReleasePattern {
        override fun nextAfter(date: LocalDate): LocalDate {
            return date.plus(DatePeriod(days = days - onSchedule.daysUntil(date).mod(days)))
        }

        override fun previousBefore(date: LocalDate): LocalDate {
            return date.minus(DatePeriod(days = (onSchedule.daysUntil(date) - 1).mod(days) + 1))
        }
    }

    data class Weekdays(val days: Set<DayOfWeek>) : ReleasePattern {
        override fun nextAfter(date: LocalDate): LocalDate {
            return generateSequence(date.plus(DatePeriod(days = 1))) { it.plus(DatePeriod(days = 1)) }
                .first { it.dayOfWeek in days }
        }

        override fun previousBefore(date: LocalDate): LocalDate {
            return generateSequence(date.minus(DatePeriod(days = 1))) { it.minus(DatePeriod(days = 1)) }
                .first { it.dayOfWeek in days }
        }

        // Release days are a day or two apart, so a release between them is more likely an extra than an early one
        override fun nearest(date: LocalDate) = date
    }

    /** Releases for [length] days in a row, every [every] days in step with [onSchedule]. */
    data class Batches(val every: Int, val onSchedule: LocalDate, val length: Int) : ReleasePattern {
        override fun nextAfter(date: LocalDate): LocalDate {
            val offset = onSchedule.daysUntil(date).mod(every)
            return date.plus(DatePeriod(days = if (offset + 1 < length) 1 else every - offset))
        }

        override fun previousBefore(date: LocalDate): LocalDate {
            val dayBefore = date.minus(DatePeriod(days = 1))
            val offset = onSchedule.daysUntil(dayBefore).mod(every)
            return dayBefore.minus(DatePeriod(days = (offset - length + 1).coerceAtLeast(0)))
        }
    }

    /** A pattern that repeats every month. */
    sealed interface Monthly : ReleasePattern {
        /** The release days in the month starting on [firstOfMonth]. */
        fun daysIn(firstOfMonth: LocalDate): List<LocalDate>

        override fun nextAfter(date: LocalDate): LocalDate {
            return generateSequence(date.firstOfMonth()) { it.plus(DatePeriod(months = 1)) }
                .flatMap { daysIn(it).sorted() }
                .first { it > date }
        }

        override fun previousBefore(date: LocalDate): LocalDate {
            return generateSequence(date.firstOfMonth()) { it.minus(DatePeriod(months = 1)) }
                .flatMap { daysIn(it).sortedDescending() }
                .first { it < date }
        }
    }

    /** Releases on [days] of every month, or a month's last day for those past it. */
    data class DaysOfMonth(val days: Set<Int>) : Monthly {
        override fun daysIn(firstOfMonth: LocalDate): List<LocalDate> {
            val lastDay = firstOfMonth.lastOfMonth().day
            return days.map { firstOfMonth.plus(DatePeriod(days = it.coerceAtMost(lastDay) - 1)) }.distinct()
        }
    }

    /** Releases on [dayOfWeek] of the [week]th week of every month, or its last week for [LAST_WEEK]. */
    data class WeekdayOfMonth(val week: Int, val dayOfWeek: DayOfWeek) : Monthly {
        override fun daysIn(firstOfMonth: LocalDate): List<LocalDate> {
            if (week == LAST_WEEK) {
                val lastOfMonth = firstOfMonth.lastOfMonth()
                val daysBack = (lastOfMonth.dayOfWeek.isoDayNumber - dayOfWeek.isoDayNumber).mod(7)
                return listOf(lastOfMonth.minus(DatePeriod(days = daysBack)))
            }
            val daysAhead = (dayOfWeek.isoDayNumber - firstOfMonth.dayOfWeek.isoDayNumber).mod(7)
            return listOf(firstOfMonth.plus(DatePeriod(days = daysAhead + (week - 1) * 7)))
        }
    }

    companion object {
        const val LAST_WEEK = -1

        private const val MIN_RELEASES = 4
        private const val MAX_WEEKDAYS = 3
        private const val COVERAGE = 0.8
        private const val DAY_OF_MONTH_TOLERANCE = 3
        private val MONTHLY_GAPS = 26..35
        private val TWICE_MONTHLY_GAPS = 12..18

        private const val BATCH_HISTORY = 40
        private const val BATCH_GAP = 2
        private const val BATCHES = 6
        private const val MIN_BATCHES = 3

        /**
         * Finds a calendar pattern a plain day interval can't follow in [days], newest first: a few fixed weekdays,
         * days or a weekday of the month, or releases in batches, which can take longer than [days] to show up in
         * [history]. They all need a few releases to be told apart from chance.
         */
        fun detect(days: List<LocalDate>, history: List<LocalDate>, medianGap: Int): ReleasePattern? {
            if (days.size < MIN_RELEASES) return null
            // Batches come first, since their days can look like a few weekdays or a monthly schedule
            return batches(history) ?: when (medianGap) {
                in MONTHLY_GAPS -> monthly(days)
                in TWICE_MONTHLY_GAPS -> twiceMonthly(days)
                else -> weekdays(days, medianGap)
            }
        }

        private fun monthly(days: List<LocalDate>): ReleasePattern? {
            val daysOfMonth = days.map { it.day }
            return when {
                daysOfMonth.max() - daysOfMonth.min() <= DAY_OF_MONTH_TOLERANCE -> DaysOfMonth(
                    setOf(daysOfMonth.median()),
                )
                // The last day of the month moves between the 28th and the 31st
                daysOfMonth.all { it >= 28 } -> DaysOfMonth(setOf(31))
                else -> weekdayOfMonth(days)
            }
        }

        private fun weekdayOfMonth(days: List<LocalDate>): ReleasePattern? {
            val dayOfWeek = days.mostCommon { it.dayOfWeek } ?: return null
            // A fifth week isn't in every month, so those days only count as the last week
            val week = days.mostCommon { (it.day - 1) / 7 + 1 }?.takeIf { it < 5 }
            if (week != null) return WeekdayOfMonth(week, dayOfWeek)
            val isLastWeek = days.count { it.day + 7 > it.lastOfMonth().day } >= days.size * COVERAGE
            return if (isLastWeek) WeekdayOfMonth(LAST_WEEK, dayOfWeek) else null
        }

        private fun twiceMonthly(days: List<LocalDate>): ReleasePattern? {
            val daysOfMonth = days.map { it.day }.sorted()
            val split = (1..<daysOfMonth.size).maxBy { daysOfMonth[it] - daysOfMonth[it - 1] }
            val halves = listOf(daysOfMonth.subList(0, split), daysOfMonth.subList(split, daysOfMonth.size))
            if (halves.any { it.size < 2 || it.last() - it.first() > DAY_OF_MONTH_TOLERANCE }) return null
            return DaysOfMonth(halves.map { it.median() }.toSet())
        }

        private fun weekdays(days: List<LocalDate>, medianGap: Int): ReleasePattern? {
            val byFrequency = days.groupingBy { it.dayOfWeek }.eachCount().entries.sortedByDescending { it.value }
            val weekdays = mutableSetOf<DayOfWeek>()
            var covered = 0
            for ((dayOfWeek, count) in byFrequency) {
                if (covered >= days.size * COVERAGE) break
                weekdays += dayOfWeek
                covered += count
            }
            // One weekday is a weekly interval, and an irregular schedule spreads over most of the week
            if (weekdays.size !in 2..MAX_WEEKDAYS || covered < days.size * COVERAGE) return null
            // Releasing on several weekdays means more than one release a week
            if (medianGap > 7 / weekdays.size + 1) return null
            return Weekdays(weekdays)
        }

        /** Runs of releases a day or two apart, starting at a steady interval and mostly longer than a day. */
        private fun batches(history: List<LocalDate>): ReleasePattern? {
            val runs = mutableListOf<MutableList<LocalDate>>()
            for (day in history.take(BATCH_HISTORY).asReversed()) {
                val run = runs.lastOrNull()
                if (run != null && run.last().daysUntil(day) <= BATCH_GAP) run += day else runs += mutableListOf(day)
            }
            val recent = runs.takeLast(BATCHES)
            if (recent.size < MIN_BATCHES || recent.count { it.size > 1 } * 2 < recent.size) return null

            val gaps = recent.zipWithNext { older, newer -> older.first().daysUntil(newer.first()) }
            val every = gaps.median()
            val length = recent.map { it.first().daysUntil(it.last()) + 1 }.median()
            if (every < length * 2 || gaps.any { abs(it - every) > every / 4 }) return null
            return Batches(every, recent.last().first(), length)
        }

        private fun List<Int>.median() = sorted()[(size - 1) / 2]

        /** The most common key of [selector], if at least [COVERAGE] of the days share it. */
        private fun <T> List<LocalDate>.mostCommon(selector: (LocalDate) -> T): T? {
            val (key, count) = groupingBy(selector).eachCount().maxBy { it.value }
            return key.takeIf { count >= size * COVERAGE }
        }
    }
}

private fun LocalDate.firstOfMonth() = LocalDate(year, month, 1)

private fun LocalDate.lastOfMonth() = firstOfMonth().plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
