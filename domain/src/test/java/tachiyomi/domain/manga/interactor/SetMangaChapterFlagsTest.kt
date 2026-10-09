
package tachiyomi.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaChapterFlagsTest {
    private val repository = mockk<MangaRepository>()
    private val subject = SetMangaChapterFlags(repository)

    @Test
    fun `builder encodes every combination of the seven supported settings`() {
        for (unread in listOf(0L, Manga.CHAPTER_SHOW_READ, Manga.CHAPTER_SHOW_UNREAD)) {
            for (downloaded in listOf(0L, Manga.CHAPTER_SHOW_DOWNLOADED, Manga.CHAPTER_SHOW_NOT_DOWNLOADED)) {
                for (bookmarked in listOf(0L, Manga.CHAPTER_SHOW_BOOKMARKED, Manga.CHAPTER_SHOW_NOT_BOOKMARKED)) {
                    for (filler in listOf(0L, Manga.CHAPTER_SHOW_FILLERMARKED, Manga.CHAPTER_SHOW_NOT_FILLERMARKED)) {
                        for (sorting in listOf(Manga.CHAPTER_SORTING_SOURCE, Manga.CHAPTER_SORTING_NUMBER, Manga.CHAPTER_SORTING_UPLOAD_DATE, Manga.CHAPTER_SORTING_ALPHABET)) {
                            for (direction in listOf(Manga.CHAPTER_SORT_ASC, Manga.CHAPTER_SORT_DESC)) {
                                for (display in listOf(Manga.CHAPTER_DISPLAY_NAME, Manga.CHAPTER_DISPLAY_NUMBER)) {
                                    val expected = unread or downloaded or bookmarked or filler or sorting or direction or display
                                    subject.buildAllFlags(unread, downloaded, bookmarked, filler, sorting, direction, display) shouldBe expected
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `builder masks each input without leaking unrelated bits`() {
        val masks = listOf(
            Manga.CHAPTER_UNREAD_MASK,
            Manga.CHAPTER_DOWNLOADED_MASK,
            Manga.CHAPTER_BOOKMARKED_MASK,
            Manga.CHAPTER_FILLERMARKED_MASK,
            Manga.CHAPTER_SORTING_MASK,
            Manga.CHAPTER_SORT_DIR_MASK,
            Manga.CHAPTER_DISPLAY_MASK,
        )
        masks.forEachIndexed { index, mask ->
            val values = List(7) { if (it == index) -1L else 0L }
            subject.buildAllFlags(values[0], values[1], values[2], values[3], values[4], values[5], values[6]) shouldBe mask
        }
    }

    @Test
    fun `per manga update uses the same combined flags and preserves repository result`() = runTest {
        val expected = subject.buildAllFlags(4, 8, 32, 128, 1024, 1, 1048576)
        coEvery { repository.updateAll(any()) } returns false
        subject.awaitSetAllFlags(listOf(42L), 4, 8, 32, 128, 1024, 1, 1048576) shouldBe false
        coVerify(exactly = 1) { repository.updateAll(listOf(MangaUpdate(id = 42, chapterFlags = expected))) }
        confirmVerified(repository)
    }
}
