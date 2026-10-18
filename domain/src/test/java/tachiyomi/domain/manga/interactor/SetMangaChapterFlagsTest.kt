package tachiyomi.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaChapterFlagsTest {

    private val repository = mockk<MangaRepository>()
    private val subject = SetMangaChapterFlags(repository)

    @Test
    fun `bulk update encodes every combination of supported flags`() = runTest {
        val captured = slot<List<MangaUpdate>>()

        coEvery {
            repository.updateAll(capture(captured))
        } returns true

        for (unread in listOf(0L, Manga.CHAPTER_SHOW_READ, Manga.CHAPTER_SHOW_UNREAD)) {
            for (downloaded in listOf(0L, Manga.CHAPTER_SHOW_DOWNLOADED, Manga.CHAPTER_SHOW_NOT_DOWNLOADED)) {
                for (bookmarked in listOf(0L, Manga.CHAPTER_SHOW_BOOKMARKED, Manga.CHAPTER_SHOW_NOT_BOOKMARKED)) {
                    for (sorting in listOf(
                        Manga.CHAPTER_SORTING_SOURCE,
                        Manga.CHAPTER_SORTING_NUMBER,
                        Manga.CHAPTER_SORTING_UPLOAD_DATE,
                        Manga.CHAPTER_SORTING_ALPHABET,
                    )) {
                        for (direction in listOf(Manga.CHAPTER_SORT_ASC, Manga.CHAPTER_SORT_DESC)) {
                            for (display in listOf(Manga.CHAPTER_DISPLAY_NAME, Manga.CHAPTER_DISPLAY_NUMBER)) {
                                val expected = unread or downloaded or bookmarked or
                                    sorting or direction or display

                                subject.awaitSetAllFlags(
                                    mangaIds = listOf(42L),
                                    unreadFilter = unread,
                                    downloadedFilter = downloaded,
                                    bookmarkedFilter = bookmarked,
                                    sortingMode = sorting,
                                    sortingDirection = direction,
                                    displayMode = display,
                                ) shouldBe true

                                val updates = captured.captured
                                updates.size shouldBe 1
                                updates.single().id shouldBe 42L
                                updates.single().chapterFlags shouldBe expected
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `bulk update masks inputs without leaking unrelated bits`() = runTest {
        val captured = slot<List<MangaUpdate>>()

        coEvery {
            repository.updateAll(capture(captured))
        } returns true

        val masks = listOf(
            Manga.CHAPTER_UNREAD_MASK,
            Manga.CHAPTER_DOWNLOADED_MASK,
            Manga.CHAPTER_BOOKMARKED_MASK,
            Manga.CHAPTER_SORTING_MASK,
            Manga.CHAPTER_SORT_DIR_MASK,
            Manga.CHAPTER_DISPLAY_MASK,
        )

        masks.forEachIndexed { index, mask ->
            val values = List(6) {
                if (it == index) -1L else 0L
            }

            subject.awaitSetAllFlags(
                mangaIds = listOf(42L),
                unreadFilter = values[0],
                downloadedFilter = values[1],
                bookmarkedFilter = values[2],
                sortingMode = values[3],
                sortingDirection = values[4],
                displayMode = values[5],
            ) shouldBe true

            captured.captured.single().chapterFlags shouldBe mask
        }
    }

    @Test
    fun `bulk update preserves repository result`() = runTest {
        val captured = slot<List<MangaUpdate>>()

        coEvery {
            repository.updateAll(capture(captured))
        } returns false

        val result = subject.awaitSetAllFlags(
            mangaIds = listOf(42L, 43L),
            unreadFilter = Manga.CHAPTER_SHOW_READ,
            downloadedFilter = Manga.CHAPTER_SHOW_DOWNLOADED,
            bookmarkedFilter = Manga.CHAPTER_SHOW_BOOKMARKED,
            sortingMode = Manga.CHAPTER_SORTING_NUMBER,
            sortingDirection = Manga.CHAPTER_SORT_ASC,
            displayMode = Manga.CHAPTER_DISPLAY_NUMBER,
        )

        result shouldBe false

        val expectedFlags = Manga.CHAPTER_SHOW_READ or
            Manga.CHAPTER_SHOW_DOWNLOADED or
            Manga.CHAPTER_SHOW_BOOKMARKED or
            Manga.CHAPTER_SORTING_NUMBER or
            Manga.CHAPTER_SORT_ASC or
            Manga.CHAPTER_DISPLAY_NUMBER

        captured.captured.map { it.id } shouldBe listOf(42L, 43L)
        captured.captured.map { it.chapterFlags } shouldBe
            listOf(expectedFlags, expectedFlags)

        coVerify(exactly = 1) {
            repository.updateAll(any())
        }
    }
}
