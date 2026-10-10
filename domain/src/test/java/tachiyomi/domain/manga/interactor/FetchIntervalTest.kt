package tachiyomi.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

@Execution(ExecutionMode.CONCURRENT)
class FetchIntervalTest {

    private val testTime = ZonedDateTime.parse("2020-01-01T00:00:00Z")
    private val testZoneId = ZoneOffset.UTC
    private var chapter = Chapter.create().copy(
        dateFetch = testTime.toEpochSecond() * 1000,
        dateUpload = testTime.toEpochSecond() * 1000,
    )

    // KMK -->
    private val getChaptersByMangaId: GetChaptersByMangaId = mockk()
    private val fetchInterval = FetchInterval(getChaptersByMangaId)
    // KMK <--

    @Test
    fun `returns default interval of 7 days when not enough distinct days`() {
        val chaptersWithUploadDate = (1..50).map {
            chapterWithTime(chapter, 1.days)
        }
        fetchInterval.calculateInterval(chaptersWithUploadDate, testZoneId) shouldBe 7

        val chaptersWithoutUploadDate = chaptersWithUploadDate.map {
            it.copy(dateUpload = 0L)
        }
        fetchInterval.calculateInterval(chaptersWithoutUploadDate, testZoneId) shouldBe 7
    }

    @Test
    fun `returns interval based on more recent chapters`() {
        val oldChapters = (1..5).map {
            chapterWithTime(chapter, (it * 7).days) // Would have interval of 7 days
        }
        val newChapters = (1..10).map {
            chapterWithTime(chapter, oldChapters.lastUploadDate() + it.days)
        }

        val chapters = oldChapters + newChapters

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 1
    }

    @Test
    fun `returns interval based on smaller subset of recent chapters if very few chapters`() {
        val oldChapters = (1..3).map {
            chapterWithTime(chapter, (it * 7).days)
        }
        // Significant gap between chapters
        val newChapters = (1..3).map {
            chapterWithTime(chapter, oldChapters.lastUploadDate() + 365.days + (it * 7).days)
        }

        val chapters = oldChapters + newChapters

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple chapters in 1 day`() {
        val chapters = (1..10).map {
            chapterWithTime(chapter, 10.hours)
        }
        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `returns interval of 7 days when multiple chapters in 2 days`() {
        val chapters = (1..2).map {
            chapterWithTime(chapter, 1.days)
        } + (1..5).map {
            chapterWithTime(chapter, 2.days)
        }
        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `returns interval of 1 day when chapters are released every 1 day`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, it.days)
        }
        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 1
    }

    @Test
    fun `returns interval of 1 day when delta is less than 1 day`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (15 * it).hours)
        }
        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when chapters are released every 2 days`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (2 * it).days)
        }
        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 2
    }

    @Test
    fun `returns interval with floored value when interval is decimal`() {
        val chaptersWithUploadDate = (1..5).map {
            chapterWithTime(chapter, (25 * it).hours)
        }
        fetchInterval.calculateInterval(chaptersWithUploadDate, testZoneId) shouldBe 1

        val chaptersWithoutUploadDate = chaptersWithUploadDate.map {
            it.copy(dateUpload = 0L)
        }
        fetchInterval.calculateInterval(chaptersWithoutUploadDate, testZoneId) shouldBe 1
    }

    @Test
    fun `returns interval of 2 days when chapters are released just below every 2 days`() {
        val chapters = (1..20).map {
            chapterWithTime(chapter, (43 * it).hours)
        }
        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 2
    }

    // KMK -->
    @Test
    fun `ignores hiatus outliers when estimating cadence`() {
        val chapters = listOf(0, 7, 14, 21, 74, 81, 88).map {
            chapterWithTime(chapter, it.days)
        }

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `prefers the recurring cadence over one-off quick releases`() {
        val chapters = listOf(0, 7, 14, 21, 28, 29, 36, 43, 50).map {
            chapterWithTime(chapter, it.days)
        }

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `does not let an isolated fast release replace the median cadence`() {
        val chapters = listOf(0, 9, 16, 17).map { chapterWithTime(chapter, it.days) }

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `trusts a sustained recent cadence when older history is noisy`() {
        val oldChapters = (1..3).map { chapterWithTime(chapter, (it * 7).days) }
        val newChapters = (1..8).map {
            chapterWithTime(chapter, oldChapters.lastUploadDate() + (it * 2).days)
        }

        fetchInterval.calculateInterval(oldChapters + newChapters, testZoneId) shouldBe 2
    }

    @Test
    fun `falls back to valid fetch dates when source upload dates are in the future`() {
        val referenceTime = testTime.plus(90.days.toJavaDuration())
        val chapters = (1..4).map {
            chapterWithTime(chapter, (it * 2).days).copy(
                dateUpload = referenceTime.plus((it * 2).days.toJavaDuration()).toInstant().toEpochMilli(),
            )
        }

        fetchInterval.calculateInterval(chapters, testZoneId, referenceTime.toInstant()) shouldBe 2
    }

    @Test
    fun `prefers source upload dates over fetch dates`() {
        val chapters = (1..4).map {
            chapterWithTime(chapter, (it * 7).days).copy(
                dateFetch = testTime.plus(it.days.toJavaDuration()).toInstant().toEpochMilli(),
            )
        }

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `ignores invalid timestamps`() {
        val validChapters = (1..4).map { chapterWithTime(chapter, (it * 7).days) }
        val invalidChapters = listOf(
            chapter.copy(dateUpload = 0L, dateFetch = 0L),
            chapter.copy(dateUpload = -1L, dateFetch = -1L),
        )

        fetchInterval.calculateInterval(validChapters + invalidChapters, testZoneId) shouldBe 7
    }

    @Test
    fun `does not predict daily releases from a catch-up batch`() {
        val batchTime = chapterWithTime(chapter, 7.days).dateFetch
        val chapters = List(12) { chapter.copy(dateUpload = 0L, dateFetch = batchTime) }

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `uses the most recent cadence to break a recurring-gap tie`() {
        val chapters = listOf(0, 2, 4, 11, 18).map { chapterWithTime(chapter, it.days) }

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 7
    }

    @Test
    fun `caps long automatic intervals at 28 days`() {
        val chapters = (1..4).map { chapterWithTime(chapter, (it * 35).days) }

        fetchInterval.calculateInterval(chapters, testZoneId) shouldBe 28
    }

    @Test
    fun `uses local release days consistently across timezones`() {
        val chapters = (1..4).map { chapterWithTime(chapter, (it * 7).days) }

        fetchInterval.calculateInterval(chapters, ZoneOffset.UTC) shouldBe 7
        fetchInterval.calculateInterval(chapters, ZoneOffset.ofHours(-5)) shouldBe 7
    }

    @Test
    fun `preserves manual and disabled fetch intervals`() = runTest {
        val window = fetchInterval.getWindow(testTime)
        val manga = Manga.create().copy(
            id = 1L,
            lastUpdate = testTime.toInstant().toEpochMilli(),
        )

        fetchInterval.toMangaUpdate(manga.copy(fetchInterval = -3), testTime, window).fetchInterval shouldBe -3
        fetchInterval.toMangaUpdate(manga.copy(fetchInterval = -FetchInterval.MANUAL_DISABLE), testTime, window)
            .fetchInterval shouldBe -FetchInterval.MANUAL_DISABLE
    }
    // KMK <--

    private fun chapterWithTime(chapter: Chapter, duration: Duration): Chapter {
        val newTime = testTime.plus(duration.toJavaDuration()).toEpochSecond() * 1000
        return chapter.copy(dateFetch = newTime, dateUpload = newTime)
    }

    private fun List<Chapter>.lastUploadDate() =
        (last().dateUpload - testTime.toInstant().toEpochMilli()).toDuration(DurationUnit.MILLISECONDS)
}
