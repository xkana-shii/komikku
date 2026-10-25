package eu.kanade.tachiyomi.ui.history

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterVisibility
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.time.Instant

// KNS -->
class HistoryVisibleCountsTest {
    private val mangaRepository = mockk<MangaRepository> {
        coEvery { getExcludedScanlatorsByMangaIds(any()) } returns emptyMap()
    }
    private val chapterRepository = mockk<ChapterRepository>()
    private val subject = HistoryVisibleCounts(
        mangaRepository,
        chapterRepository,
        ChapterVisibility(LibraryPreferences(InMemoryPreferenceStore())),
    )

    @Test
    fun `a manga whose only unread chapter is blacklisted is finished`() = runTest {
        val manga = Manga.create().copy(id = 1, blacklistedChapters = listOf(chapterUrlBlacklistKey("/2")))
        coEvery { mangaRepository.getMangaById(1) } returns manga
        coEvery { mangaRepository.getMangaById(2) } returns Manga.create().copy(id = 2)
        coEvery { chapterRepository.getChaptersByMangaIds(listOf(1L)) } returns listOf(
            chapter(id = 1, read = true),
            chapter(id = 2, read = false),
        )
        // The query counts both chapters of manga 1: 2 total, 1 read
        val history = listOf(entry(mangaId = 1, total = 2, read = 1), entry(mangaId = 2, total = 5, read = 2))

        val counted = subject.apply(history)

        counted.map { it.totalCountCalculated to it.readCountCalculated } shouldBe listOf(1L to 1L, 5L to 2L)
        HistoryVisibleCounts.filterUnfinished(counted, unfinished = true).map { it.mangaId } shouldBe listOf(2L)
        HistoryVisibleCounts.filterUnfinished(counted, unfinished = false).map { it.mangaId } shouldBe listOf(1L)
        HistoryVisibleCounts.filterUnfinished(counted, unfinished = null).size shouldBe 2
    }

    @Test
    fun `entries without rules cost no chapter query`() = runTest {
        coEvery { mangaRepository.getMangaById(2) } returns Manga.create().copy(id = 2)
        val history = listOf(entry(mangaId = 2, total = 5, read = 2))
        subject.apply(history) shouldBe history
        coVerify(exactly = 0) { chapterRepository.getChaptersByMangaIds(any()) }
    }

    @Test
    fun `a read chapter whose unread duplicate Smart Merge hides leaves no unread dot`() = runTest {
        val manga = Manga.create().copy(id = 1, smartMergeOverride = true, scanlatorPriority = listOf("A", "B"))
        coEvery { mangaRepository.getMangaById(1) } returns manga
        coEvery { chapterRepository.getChaptersByMangaIds(listOf(1L)) } returns listOf(
            chapter(id = 1, read = true, number = 1.0, scanlator = "A"),
            chapter(id = 2, read = false, number = 1.0, scanlator = "B"),
        )
        val counted = subject.apply(listOf(entry(mangaId = 1, total = 2, read = 1))).single()
        (counted.totalCountCalculated to counted.readCountCalculated) shouldBe (1L to 1L)
        counted.unreadCount shouldBe 0L
    }

    @Test
    fun `an excluded scanlator's unread release never becomes the visible one`() = runTest {
        // B would win the merge, but B is excluded, so A's read chapter is the visible release.
        val manga = Manga.create().copy(id = 1, smartMergeOverride = true, scanlatorPriority = listOf("B", "A"))
        coEvery { mangaRepository.getMangaById(1) } returns manga
        coEvery { mangaRepository.getExcludedScanlatorsByMangaIds(listOf(1L)) } returns mapOf(1L to listOf("B"))
        coEvery { chapterRepository.getChaptersByMangaIds(listOf(1L)) } returns listOf(
            chapter(id = 1, read = true, number = 1.0, scanlator = "A"),
            chapter(id = 2, read = false, number = 1.0, scanlator = "B"),
        )
        // The query already leaves the excluded release out: 1 chapter, read.
        val counted = subject.apply(listOf(entry(mangaId = 1, total = 1, read = 1))).single()
        counted.unreadCount shouldBe 0L
        HistoryVisibleCounts.filterUnfinished(listOf(counted), unfinished = true) shouldBe emptyList()
    }

    @Test
    fun `a genuinely unread visible chapter keeps the dot`() = runTest {
        val manga = Manga.create().copy(id = 1, blacklistedChapters = listOf(chapterUrlBlacklistKey("/3")))
        coEvery { mangaRepository.getMangaById(1) } returns manga
        coEvery { chapterRepository.getChaptersByMangaIds(listOf(1L)) } returns listOf(
            chapter(id = 1, read = true),
            chapter(id = 2, read = false),
            chapter(id = 3, read = false),
        )
        subject.apply(listOf(entry(mangaId = 1, total = 3, read = 1))).single().unreadCount shouldBe 1L
    }

    private fun chapter(id: Long, read: Boolean, number: Double = id.toDouble(), scanlator: String? = null) =
        Chapter.create().copy(id = id, mangaId = 1, url = "/$id", chapterNumber = number, read = read, scanlator = scanlator)

    private fun entry(mangaId: Long, total: Long, read: Long) = HistoryWithRelations(
        id = mangaId,
        chapterId = mangaId,
        mangaId = mangaId,
        ogTitle = "M",
        chapterNumber = 1.0,
        read = true,
        lastPageRead = 0,
        totalCountCalculated = total,
        readCountCalculated = read,
        readAt = Instant.fromEpochMilliseconds(0),
        readDuration = 0,
        coverData = MangaCover(mangaId, 0, true, null, null),
    )
}
// KNS <--
