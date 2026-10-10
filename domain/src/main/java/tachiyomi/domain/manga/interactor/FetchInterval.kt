package tachiyomi.domain.manga.interactor

import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

class FetchInterval(
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun toMangaUpdate(
        manga: Manga,
        dateTime: ZonedDateTime,
        window: Pair<Long, Long>,
    ): MangaUpdate {
        val interval = manga.fetchInterval.takeIf { it < 0 } ?: calculateInterval(
            chapters = getChaptersByMangaId.await(manga.id, applyFilter = true),
            zone = dateTime.zone,
            // KMK -->
            referenceTime = dateTime.toInstant(),
            // KMK <--
        )
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(ZonedDateTime.now())
        } else {
            window
        }
        val nextUpdate = calculateNextUpdate(manga, interval, dateTime, currentWindow)

        return MangaUpdate(id = manga.id, nextUpdate = nextUpdate, fetchInterval = interval)
    }

    fun getWindow(dateTime: ZonedDateTime): Pair<Long, Long> {
        val today = dateTime.toLocalDate().atStartOfDay(dateTime.zone)
        val lowerBound = today.minusDays(GRACE_PERIOD)
        val upperBound = today.plusDays(GRACE_PERIOD)
        return Pair(lowerBound.toEpochSecond() * 1000, upperBound.toEpochSecond() * 1000 - 1)
    }

    // KMK -->
    internal fun calculateInterval(
        chapters: List<Chapter>,
        zone: ZoneId,
        referenceTime: Instant = Instant.now(),
    ): Int {
        val chapterWindow = if (chapters.size <= 8) 6 else 12
        val uploadDates = releaseDays(chapters, Chapter::dateUpload, zone, referenceTime, chapterWindow)
        val fetchDates = releaseDays(chapters, Chapter::dateFetch, zone, referenceTime, chapterWindow)

        // Source dates describe the actual release cadence. Fetch dates are only a fallback
        // when sources do not provide enough valid upload dates.
        val gaps = when {
            uploadDates.size >= MINIMUM_RELEASE_DAYS -> dayGaps(uploadDates)
            fetchDates.size >= MINIMUM_RELEASE_DAYS -> dayGaps(fetchDates)
            else -> return DEFAULT_INTERVAL
        }
        if (gaps.size < MINIMUM_GAPS) return DEFAULT_INTERVAL

        val median = gaps.sorted()[gaps.size / 2]
        val filteredGaps = gaps.filter { it <= median * OUTLIER_MULTIPLIER }
        if (filteredGaps.isEmpty()) return median.coerceIn(1, MAX_AUTOMATIC_INTERVAL.toLong()).toInt()

        // A repeated cadence is stronger evidence than a noisy median. Ties prefer the most
        // recent occurrence, then the shorter gap, so results are deterministic.
        val recurringGap = filteredGaps
            .withIndex()
            .groupBy { it.value }
            .map { (gap, occurrences) ->
                RepeatedGap(gap, occurrences.size, occurrences.minOf { it.index })
            }
            .sortedWith(
                compareByDescending<RepeatedGap> { it.occurrences }
                    .thenBy { it.firstIndex }
                    .thenBy { it.gap },
            )
            .firstOrNull()

        val interval = if (recurringGap?.occurrences ?: 0 >= 2) {
            recurringGap!!.gap
        } else {
            val recentGap = filteredGaps.first()
            // A single unusually quick chapter should not replace the otherwise stable median.
            if (recentGap * 2 < median) median else recentGap
        }

        return interval.coerceIn(1, MAX_AUTOMATIC_INTERVAL.toLong()).toInt()
    }

    private fun releaseDays(
        chapters: List<Chapter>,
        timestamp: (Chapter) -> Long,
        zone: ZoneId,
        referenceTime: Instant,
        window: Int,
    ) = chapters.asSequence()
        .map(timestamp)
        .filter { it in 1..referenceTime.toEpochMilli() }
        .sortedDescending()
        .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        .distinct()
        .take(window)
        .toList()

    /** Day gaps between consecutive distinct release days, newest first. */
    private fun dayGaps(days: List<LocalDate>): List<Long> =
        days.windowed(2).map { ChronoUnit.DAYS.between(it[1], it[0]) }

    private data class RepeatedGap(
        val gap: Long,
        val occurrences: Int,
        val firstIndex: Int,
    )
    // KMK <--

    private fun calculateNextUpdate(
        manga: Manga,
        interval: Int,
        dateTime: ZonedDateTime,
        window: Pair<Long, Long>,
    ): Long {
        if (manga.nextUpdate in window.first.rangeTo(window.second + 1)) {
            return manga.nextUpdate
        }

        val latestDate = ZonedDateTime.ofInstant(
            if (manga.lastUpdate > 0) Instant.ofEpochMilli(manga.lastUpdate) else Instant.now(),
            dateTime.zone,
        )
            .toLocalDate()
            .atStartOfDay()
        val timeSinceLatest = ChronoUnit.DAYS.between(latestDate, dateTime).toInt()
        val cycle = timeSinceLatest.floorDiv(
            interval.absoluteValue.takeIf { interval < 0 }
                ?: increaseInterval(interval, timeSinceLatest, increaseWhenOver = 10),
        )
        return latestDate.plusDays((cycle + 1) * interval.absoluteValue.toLong()).toEpochSecond(dateTime.offset) * 1000
    }

    private fun increaseInterval(delta: Int, timeSinceLatest: Int, increaseWhenOver: Int): Int {
        // KMK -->
        if (delta >= MAX_AUTOMATIC_INTERVAL) return MAX_AUTOMATIC_INTERVAL
        // KMK <--

        // double delta again if missed more than 9 check in new delta
        val cycle = timeSinceLatest.floorDiv(delta) + 1
        return if (cycle > increaseWhenOver) {
            increaseInterval(delta * 2, timeSinceLatest, increaseWhenOver)
        } else {
            delta
        }
    }

    companion object {
        // KMK -->
        const val MAX_INTERVAL = 14

        private const val DEFAULT_INTERVAL = 7
        private const val MAX_AUTOMATIC_INTERVAL = 28
        private const val MINIMUM_RELEASE_DAYS = 4
        private const val MINIMUM_GAPS = 3
        private const val OUTLIER_MULTIPLIER = 3
        // KMK <--

        private const val GRACE_PERIOD = 1L

        // KMK -->
        const val MANUAL_DISABLE = 99999 // 274 years in future
        // KMK <--
    }
}
