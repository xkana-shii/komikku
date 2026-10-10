package tachiyomi.domain.history.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import java.util.Date

// KMK -->
@Execution(ExecutionMode.CONCURRENT)
class GetNextChaptersTest {

    private val getChaptersByMangaId: GetChaptersByMangaId = mockk()
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId = mockk()
    private val getManga: GetManga = mockk()
    private val historyRepository: HistoryRepository = mockk()

    private val interactor = GetNextChapters(
        getChaptersByMangaId,
        getMergedChaptersByMangaId,
        getManga,
        historyRepository,
    )

    private fun chapter(id: Long, mangaId: Long, read: Boolean, number: Double) = Chapter.create().copy(
        id = id,
        mangaId = mangaId,
        read = read,
        chapterNumber = number,
    )

    private fun history(mangaId: Long, chapterId: Long) = HistoryWithRelations(
        id = mangaId,
        chapterId = chapterId,
        mangaId = mangaId,
        ogTitle = "Manga $mangaId",
        chapterNumber = 1.0,
        read = true,
        lastPageRead = 5,
        totalCountCalculated = 2,
        readCountCalculated = 1,
        readAt = Date(0),
        readDuration = 0,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = 1,
            isMangaFavorite = true,
            ogUrl = null,
            lastModified = 0,
        ),
    )

    private fun givenManga(id: Long) {
        coEvery { getManga.await(id) } returns Manga.create().copy(id = id, source = 1)
    }

    @Test
    fun `returns the unread chapter from the newest history entry`() = runTest {
        val newest = chapter(2L, 10L, read = false, number = 2.0)
        givenManga(10L)
        coEvery { getChaptersByMangaId.await(10L, applyFilter = true) } returns
            listOf(chapter(1L, 10L, read = true, number = 1.0), newest)
        coEvery { historyRepository.getRecentHistory(25L) } returns listOf(history(10L, 1L))

        interactor.awaitFirstReadable() shouldBe newest
    }

    @Test
    fun `skips a completed series and resumes an older one`() = runTest {
        val older = chapter(4L, 20L, read = false, number = 2.0)
        givenManga(10L)
        givenManga(20L)
        coEvery { getChaptersByMangaId.await(10L, applyFilter = true) } returns
            listOf(chapter(1L, 10L, read = true, number = 1.0))
        coEvery { getChaptersByMangaId.await(20L, applyFilter = true) } returns
            listOf(chapter(3L, 20L, read = true, number = 1.0), older)
        coEvery { historyRepository.getRecentHistory(25L) } returns
            listOf(history(10L, 1L), history(20L, 3L))

        interactor.awaitFirstReadable() shouldBe older
    }

    @Test
    fun `skips read chapters after an older history entry`() = runTest {
        val unread = chapter(4L, 20L, read = false, number = 3.0)
        givenManga(10L)
        givenManga(20L)
        coEvery { getChaptersByMangaId.await(10L, applyFilter = true) } returns
            listOf(chapter(1L, 10L, read = true, number = 1.0))
        coEvery { getChaptersByMangaId.await(20L, applyFilter = true) } returns
            listOf(
                chapter(2L, 20L, read = true, number = 1.0),
                chapter(3L, 20L, read = true, number = 2.0),
                unread,
            )
        coEvery { historyRepository.getRecentHistory(25L) } returns
            listOf(history(10L, 1L), history(20L, 2L))

        interactor.awaitFirstReadable() shouldBe unread
    }

    @Test
    fun `checks each manga only once when History has duplicates`() = runTest {
        val older = chapter(5L, 20L, read = false, number = 2.0)
        givenManga(10L)
        givenManga(20L)
        coEvery { getChaptersByMangaId.await(10L, applyFilter = true) } returns
            listOf(chapter(1L, 10L, read = true, number = 1.0))
        coEvery { getChaptersByMangaId.await(20L, applyFilter = true) } returns
            listOf(chapter(4L, 20L, read = true, number = 1.0), older)
        coEvery { historyRepository.getRecentHistory(25L) } returns
            listOf(history(10L, 1L), history(10L, 1L), history(20L, 4L))

        interactor.awaitFirstReadable() shouldBe older
        coVerify(exactly = 1) { getChaptersByMangaId.await(10L, applyFilter = true) }
    }

    @Test
    fun `returns null when every recent series is complete`() = runTest {
        givenManga(10L)
        givenManga(20L)
        coEvery { getChaptersByMangaId.await(any(), applyFilter = true) } returns
            listOf(chapter(1L, 10L, read = true, number = 1.0))
        coEvery { historyRepository.getRecentHistory(25L) } returns
            listOf(history(10L, 1L), history(20L, 1L))

        interactor.awaitFirstReadable() shouldBe null
    }

    @Test
    fun `uses the requested bounded scan limit`() = runTest {
        coEvery { historyRepository.getRecentHistory(1L) } returns emptyList()

        interactor.awaitFirstReadable(scanLimit = 1L) shouldBe null
    }

    @Test
    fun `uses existing chapter filters while finding a continuation`() = runTest {
        val unread = chapter(2L, 10L, read = false, number = 2.0)
        givenManga(10L)
        coEvery { getChaptersByMangaId.await(10L, applyFilter = true) } returns
            listOf(chapter(1L, 10L, read = true, number = 1.0), unread)
        coEvery { historyRepository.getRecentHistory(25L) } returns listOf(history(10L, 1L))

        interactor.awaitFirstReadable() shouldBe unread
        coVerify { getChaptersByMangaId.await(10L, applyFilter = true) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfo() {
            Injekt.addSingletonFactory {
                GetCustomMangaInfo(
                    object : CustomMangaRepository {
                        override fun get(mangaId: Long): CustomMangaInfo? = null

                        override fun set(mangaInfo: CustomMangaInfo) = Unit
                    },
                )
            }
        }
    }
}
// KMK <--
