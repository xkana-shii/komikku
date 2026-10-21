package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

// KNS -->
/** Acceptance scenarios 9 and 26: library counts follow the visible chapters, recomputed selectively. */
class LibraryVisibleCountsTest {

    private val chapterRepository = mockk<ChapterRepository>()
    private val mangaRepository = mockk<MangaRepository>()
    private val counts = LibraryVisibleCounts(chapterRepository, mangaRepository, mockk())

    private val chaptersOf1 = listOf(
        chapter(1, mangaId = 1, number = 1.0, read = true),
        chapter(2, mangaId = 1, number = 2.0),
        chapter(3, mangaId = 1, number = 2.0, scanlator = "B"),
        chapter(4, mangaId = 1, number = 3.0),
    )

    init {
        coEvery { mangaRepository.getExcludedScanlatorsByMangaIds(any()) } returns emptyMap()
        coEvery { chapterRepository.getChaptersByMangaIds(any()) } answers {
            val ids = firstArg<List<Long>>()
            chaptersOf1.filter { it.mangaId in ids }
        }
    }

    @Test
    fun `entries with rules get visible counts, the others are untouched`() = runBlocking<Unit> {
        val withRules = item(1, manga(1, blacklist = listOf(chapterUrlBlacklistKey("/4")), smartMerge = true), total = 4)
        val plain = item(2, manga(2), total = 10)

        val result = counts.apply(listOf(withRules, plain), globalSmartMerge = false)

        // Chapter 4 blacklisted, chapter 2 and 3 merged into one: 1, 2 visible.
        result[0].totalChapters shouldBe 2
        result[0].readCount shouldBe 1
        result[0].unreadCount shouldBe 1
        result[1] shouldBe plain
    }

    @Test
    fun `unchanged entries reuse the cache and a change recomputes only that entry`() = runBlocking<Unit> {
        val entry = item(1, manga(1, smartMerge = true), total = 4)
        counts.apply(listOf(entry), globalSmartMerge = false)
        counts.apply(listOf(entry), globalSmartMerge = false)
        coVerify(exactly = 1) { chapterRepository.getChaptersByMangaIds(any()) }

        // A chapter read elsewhere changes the stored counts: recompute.
        counts.apply(listOf(entry.copy(readCount = 2)), globalSmartMerge = false)
        coVerify(exactly = 2) { chapterRepository.getChaptersByMangaIds(any()) }
    }

    @Test
    fun `the visible chapters are kept for download counts of entries with rules`() = runBlocking<Unit> {
        val withRules = item(1, manga(1, blacklist = listOf(chapterUrlBlacklistKey("/4")), smartMerge = true), total = 4)
        val plain = item(2, manga(2), total = 10)
        counts.apply(listOf(withRules, plain), globalSmartMerge = false)

        counts.visibleChapters(1)?.map { it.id } shouldBe listOf(1L, 2L)
        counts.visibleChapters(2) shouldBe null
    }

    @Test
    fun `the global Smart Merge switch applies to entries without an override`() = runBlocking<Unit> {
        val entry = item(1, manga(1), total = 4)
        counts.apply(listOf(entry), globalSmartMerge = false).single().totalChapters shouldBe 4
        counts.apply(listOf(entry), globalSmartMerge = true).single().totalChapters shouldBe 3
    }

    private fun manga(id: Long, blacklist: List<String> = emptyList(), smartMerge: Boolean? = null) =
        Manga.create().copy(id = id, source = 1, blacklistedChapters = blacklist, smartMergeOverride = smartMerge)

    private fun item(id: Long, manga: Manga, total: Long) = LibraryManga(
        manga = manga,
        categories = emptyList(),
        totalChapters = total,
        readCount = 1,
        bookmarkCount = 0,
        bookmarkReadCount = 0,
        chapterFlags = 0,
        latestUpload = null,
        chapterFetchedAt = null,
        lastRead = null,
    ).also { check(it.id == id) }

    private fun chapter(id: Long, mangaId: Long, number: Double, read: Boolean = false, scanlator: String = "A") =
        Chapter.create().copy(
            id = id,
            mangaId = mangaId,
            url = "/$id",
            name = "Chapter $number",
            chapterNumber = number,
            read = read,
            scanlator = scanlator,
        )
}
// KNS <--
