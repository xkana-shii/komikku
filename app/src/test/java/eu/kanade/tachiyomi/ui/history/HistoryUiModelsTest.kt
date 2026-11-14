package eu.kanade.tachiyomi.ui.history

import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import java.util.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class HistoryUiModelsTest {

    private lateinit var defaultTimeZone: TimeZone

    // toLocalDate() reads the system zone, so the day boundaries below are only stable in UTC
    @BeforeEach
    fun pinUtc() {
        defaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @AfterEach
    fun restoreTimeZone() {
        TimeZone.setDefault(defaultTimeZone)
    }

    private val day1 = Instant.fromEpochMilliseconds(1_704_888_000_000) // 2024-01-10T12:00:00Z
    private val day2 = Instant.fromEpochMilliseconds(1_704_715_200_000) // 2024-01-08T12:00:00Z

    @Test
    fun `multiple entries on the same day share one header`() {
        val first = history(id = 3, mangaId = 1, chapterId = 13, readAt = day1 + 1.hours)
        val second = history(id = 2, mangaId = 1, chapterId = 12, readAt = day1 + 30.minutes)
        val third = history(id = 1, mangaId = 1, chapterId = 11, readAt = day1)

        val models = listOf(first, second, third).toHistoryUiModels()

        models shouldContainExactly listOf(
            HistoryUiModel.Header(day1.toLocalDate()),
            HistoryUiModel.Item(first),
            HistoryUiModel.Item(second),
            HistoryUiModel.Item(third),
        )
    }

    @Test
    fun `each day gets its own header`() {
        val models = listOf(
            history(id = 2, mangaId = 1, chapterId = 12, readAt = day1),
            history(id = 1, mangaId = 1, chapterId = 11, readAt = day2),
        ).toHistoryUiModels()

        models shouldContainExactly listOf(
            HistoryUiModel.Header(day1.toLocalDate()),
            HistoryUiModel.Item(history(id = 2, mangaId = 1, chapterId = 12, readAt = day1)),
            HistoryUiModel.Header(day2.toLocalDate()),
            HistoryUiModel.Item(history(id = 1, mangaId = 1, chapterId = 11, readAt = day2)),
        )
    }

    @Test
    fun `entries without a read date are dropped`() {
        val models = listOf(
            history(id = 3, mangaId = 1, chapterId = 13, readAt = null),
            history(id = 2, mangaId = 1, chapterId = 12, readAt = day1),
            history(id = 1, mangaId = 1, chapterId = 11, readAt = day1),
        ).toHistoryUiModels()

        models.count { it is HistoryUiModel.Header } shouldBe 1
        models.itemIds() shouldContainExactly listOf(2L, 1L)
    }

    @Test
    fun `several chapters of one manga keep the query order`() {
        val models = listOf(
            history(id = 5, mangaId = 2, chapterId = 21, readAt = day1),
            history(id = 4, mangaId = 1, chapterId = 13, readAt = day1 - 1.hours),
            history(id = 3, mangaId = 1, chapterId = 12, readAt = day2),
            history(id = 2, mangaId = 2, chapterId = 20, readAt = day2 - 1.hours),
            history(id = 1, mangaId = 1, chapterId = 11, readAt = day2 - 2.hours),
        ).toHistoryUiModels()

        models.itemIds() shouldContainExactly listOf(5L, 4L, 3L, 2L, 1L)
        models.filterIsInstance<HistoryUiModel.Header>().map { it.date } shouldContainExactly
            listOf(day1.toLocalDate(), day2.toLocalDate())
    }

    private fun List<HistoryUiModel>.itemIds(): List<Long> =
        filterIsInstance<HistoryUiModel.Item>().map { it.item.id }

    private fun history(id: Long, mangaId: Long, chapterId: Long, readAt: Instant?): HistoryWithRelations =
        HistoryWithRelations(
            id = id,
            chapterId = chapterId,
            mangaId = mangaId,
            ogTitle = "Manga $mangaId",
            chapterNumber = 1.0,
            read = true,
            lastPageRead = 0,
            totalCountCalculated = 1,
            readCountCalculated = 1,
            readAt = readAt,
            readDuration = 0,
            coverData = MangaCover(
                mangaId = mangaId,
                sourceId = 1,
                isMangaFavorite = false,
                ogUrl = null,
                lastModified = null,
            ),
        )
}
