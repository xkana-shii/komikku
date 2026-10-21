package tachiyomi.domain.history.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.ChapterVisibility
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga

// KNS -->
/** features.md § 13.2 and acceptance scenario 10: continuing from a hidden chapter. */
class GetNextChaptersVisibilityTest {

    private val ch1 = chapter(1, 1.0, read = true)
    private val ch2 = chapter(2, 2.0)
    private val ch3 = chapter(3, 3.0)
    private val ch4 = chapter(4, 4.0)

    @Test
    fun `continuing from a blacklisted chapter goes to the next visible one, not to chapter 1`() = runBlocking<Unit> {
        val manga = manga(blacklist = listOf(chapterUrlBlacklistKey(ch2.url)))
        val next = getNextChapters(manga, listOf(ch1, ch2, ch3, ch4))

        next.await(manga.id, fromChapterId = ch2.id, onlyUnread = true).map(Chapter::id) shouldBe listOf(3L, 4L)
        next.await(manga.id, fromChapterId = ch2.id, onlyUnread = false).map(Chapter::id) shouldBe listOf(3L, 4L)
    }

    @Test
    fun `continuing from a hidden duplicate starts at its visible release`() = runBlocking<Unit> {
        val hidden = chapter(5, 2.0, scanlator = "B")
        val preferred = chapter(6, 2.0, scanlator = "A")
        val manga = manga(priority = listOf("A", "B"), smartMerge = true)
        val next = getNextChapters(manga, listOf(ch1, hidden, preferred, ch3))

        next.await(manga.id, fromChapterId = hidden.id, onlyUnread = false).map(Chapter::id) shouldBe listOf(6L, 3L)
    }

    @Test
    fun `a hidden last chapter has nothing after it`() = runBlocking<Unit> {
        val manga = manga(blacklist = listOf(chapterUrlBlacklistKey(ch4.url)))
        getNextChapters(manga, listOf(ch1, ch2, ch3, ch4)).await(manga.id, ch4.id, onlyUnread = true) shouldBe emptyList()
    }

    @Test
    fun `visible chapters keep the upstream behavior`() = runBlocking<Unit> {
        val manga = manga(blacklist = listOf(chapterUrlBlacklistKey(ch2.url)))
        val next = getNextChapters(manga, listOf(ch1, ch2, ch3.copy(read = true), ch4))

        // A read starting chapter is skipped; the hidden chapter 2 never appears.
        next.await(manga.id, fromChapterId = ch3.id, onlyUnread = false).map(Chapter::id) shouldBe listOf(4L)
        next.await(manga.id, onlyUnread = false).map(Chapter::id) shouldBe listOf(1L, 3L, 4L)
    }

    private fun getNextChapters(manga: Manga, chapters: List<Chapter>): GetNextChapters {
        val getChapters = mockk<GetChaptersByMangaId>()
        coEvery { getChapters.await(manga.id, any()) } returns chapters
        val getManga = mockk<GetManga>()
        coEvery { getManga.await(manga.id) } returns manga
        val store = InMemoryPreferenceStore()
        return GetNextChapters(
            getChaptersByMangaId = getChapters,
            getMergedChaptersByMangaId = mockk(),
            getManga = getManga,
            historyRepository = mockk(),
            chapterVisibility = ChapterVisibility(LibraryPreferences(store)),
        )
    }

    private fun manga(
        blacklist: List<String> = emptyList(),
        priority: List<String> = emptyList(),
        smartMerge: Boolean? = null,
    ) = Manga.create().copy(
        id = 1,
        source = 1,
        // Sort by source order ascending = chapter number order in this fixture.
        chapterFlags = Manga.CHAPTER_SORTING_NUMBER,
        blacklistedChapters = blacklist,
        scanlatorPriority = priority,
        smartMergeOverride = smartMerge,
    )

    private fun chapter(id: Long, number: Double, read: Boolean = false, scanlator: String? = null) =
        Chapter.create().copy(
            id = id,
            mangaId = 1,
            url = "/chapter/$id",
            name = "Chapter $number",
            chapterNumber = number,
            sourceOrder = 100 - id,
            read = read,
            scanlator = scanlator,
        )
}
// KNS <--
