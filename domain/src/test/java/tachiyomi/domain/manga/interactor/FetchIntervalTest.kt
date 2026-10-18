package tachiyomi.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class FetchIntervalTest {

    private val base = Instant.parse("2020-01-01T00:00:00Z")
    private val today = LocalDate(2020, 12, 1)
    private val zone = TimeZone.UTC

    private val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
    private val fetchInterval = FetchInterval(getChaptersByMangaId)

    private fun chapterAt(offset: Duration): Chapter {
        val timestamp = base + offset
        return Chapter.create().copy(
            dateFetch = timestamp,
            dateUpload = timestamp,
        )
    }

    private fun estimate(
        chapters: List<Chapter>,
        timeZone: TimeZone = zone,
        date: LocalDate = today,
    ): ReleaseSchedule {
        return ReleaseSchedule.estimate(chapters, timeZone, date)
    }

    @Test
    fun `returns default interval when dates are insufficient`() {
        val chapters = List(20) { chapterAt(1.days) }

        estimate(chapters).interval shouldBe 7
    }

    @Test
    fun `returns default interval for an empty chapter list`() {
        estimate(emptyList()).interval shouldBe 7
    }

    @Test
    fun `estimates daily releases`() {
        val chapters = (1..20).map {
            chapterAt(it.days)
        }

        estimate(chapters).interval shouldBe 1
    }

    @Test
    fun `estimates two day releases`() {
        val chapters = (1..20).map {
            chapterAt((it * 2).days)
        }

        estimate(chapters).interval shouldBe 2
    }

    @Test
    fun `estimates weekly releases`() {
        val chapters = (1..12).map {
            chapterAt((it * 7).days)
        }

        estimate(chapters).interval shouldBe 7
    }

    @Test
    fun `duplicate release dates do not imply daily releases`() {
        val chapters = (1..10).map {
            chapterAt(7.days)
        }

        estimate(chapters).interval shouldBe 7
    }

    @Test
    fun `fallback uses fetch dates when source upload dates are missing`() {
        val chapters = (1..8).map {
            chapterAt((it * 7).days).copy(
                dateUpload = null,
            )
        }

        estimate(chapters).interval shouldBe 7
    }

    @Test
    fun `source upload dates take precedence over fetch dates`() {
        val chapters = (1..8).map { index ->
            chapterAt((index * 7).days).copy(
                dateFetch = base + index.days,
            )
        }

        estimate(chapters).interval shouldBe 7
    }

    @Test
    fun `future source upload dates fall back to valid fetch dates`() {
        val chapters = (1..8).map { index ->
            chapterAt((index * 2).days).copy(
                dateUpload = base + (500 + index * 2).days,
            )
        }

        estimate(chapters).interval shouldBe 2
    }

    @Test
    fun `hiatus outlier does not replace the normal weekly cadence`() {
        val chapters = listOf(
            0,
            7,
            14,
            21,
            74,
            81,
            88,
        ).map {
            chapterAt(it.days)
        }

        estimate(chapters).interval shouldBe 7
    }

    @Test
    fun `isolated early releases do not necessarily change the weekly cadence`() {
        val chapters = listOf(
            0, 7, 14, 21, 28, 29, 36, 43, 50,
        ).map {
            chapterAt(it.days)
        }

        estimate(chapters).interval shouldBe 7
    }

    @Test
    fun `recent sustained daily releases override older weekly history`() {
        val oldChapters = (1..5).map {
            chapterAt((it * 7).days)
        }

        val newChapters = (1..10).map {
            chapterAt((35 + it).days)
        }

        estimate(oldChapters + newChapters).interval shouldBe 1
    }

    @Test
    fun `large estimated intervals are capped`() {
        val chapters = (1..4).map {
            chapterAt((it * 35).days)
        }

        estimate(chapters).interval shouldBe FetchInterval.MAX_INTERVAL
    }

    @Test
    fun `weekly release cadence is consistent across time zones`() {
        val chapters = (1..8).map {
            chapterAt((it * 7).days)
        }

        estimate(chapters, TimeZone.UTC).interval shouldBe 7
        estimate(
            chapters,
            TimeZone.of("America/New_York"),
        ).interval shouldBe 7
    }

    @Test
    fun `manual interval is preserved by the interactor`() = runTest {
        val dateTime = LocalDateTime(2020, 12, 1, 12, 0)
        val window = fetchInterval.getWindow(dateTime.date, zone)

        val manga = Manga.create().copy(
            id = 1L,
            fetchInterval = -3,
            lastUpdate = base,
        )

        val update = fetchInterval.withFetchInterval(
            manga = manga,
            dateTime = dateTime,
            timeZone = zone,
            window = window,
        )

        update.fetchInterval shouldBe -3
    }

    @Test
    fun `disabled automatic fetching remains disabled`() = runTest {
        val dateTime = LocalDateTime(2020, 12, 1, 12, 0)
        val window = fetchInterval.getWindow(dateTime.date, zone)

        val manga = Manga.create().copy(
            id = 1L,
            fetchInterval = -FetchInterval.MANUAL_DISABLE,
            lastUpdate = base,
        )

        val update = fetchInterval.withFetchInterval(
            manga = manga,
            dateTime = dateTime,
            timeZone = zone,
            window = window,
        )

        update.fetchInterval shouldBe -FetchInterval.MANUAL_DISABLE
    }
}
