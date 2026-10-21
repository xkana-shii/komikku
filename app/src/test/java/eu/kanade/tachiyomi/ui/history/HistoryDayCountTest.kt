package eu.kanade.tachiyomi.ui.history

import eu.kanade.presentation.history.HistoryUiModel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// KNS -->
class HistoryDayCountTest {

    @Test
    fun `each day header counts different manga, not chapters`() {
        val day = Instant.parse("2026-10-20T12:00:00Z")
        val state = HistoryViewModel.State(
            list = listOf(
                history(chapterId = 1, mangaId = 1, readAt = day),
                history(chapterId = 2, mangaId = 1, readAt = day - 1.minutes),
                history(chapterId = 3, mangaId = 1, readAt = day - 2.minutes),
                history(chapterId = 4, mangaId = 2, readAt = day - 3.minutes),
                history(chapterId = 5, mangaId = 1, readAt = day - 3.days),
            ),
        )

        state.getUiModel().filterIsInstance<HistoryUiModel.Header>().map { it.mangaCount } shouldBe listOf(2, 1)
    }

    private fun history(chapterId: Long, mangaId: Long, readAt: Instant) = HistoryWithRelations(
        id = chapterId,
        chapterId = chapterId,
        mangaId = mangaId,
        ogTitle = "Manga $mangaId",
        chapterNumber = chapterId.toDouble(),
        read = true,
        lastPageRead = 0,
        totalCountCalculated = 0,
        readCountCalculated = 0,
        readAt = readAt,
        readDuration = 0,
        coverData = MangaCover(mangaId, 0, true, null, null),
    )
}
// KNS <--
