package eu.kanade.tachiyomi.ui.updates

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterVisibility
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.updates.model.UpdatesWithRelations
import kotlin.time.Instant

// KNS -->
class UpdatesVisibilityFilterTest {

    @Test
    fun `blacklisted chapters and hidden duplicates leave the Updates feed`() = runBlocking<Unit> {
        val chapters = listOf(
            chapter(1, 1.0, "A"),
            chapter(2, 2.0, "A"),
            chapter(3, 2.0, "B"),
            chapter(4, 3.0, "A"),
        )
        val manga = Manga.create().copy(
            id = 1,
            blacklistedChapters = listOf(chapterUrlBlacklistKey("/4")),
            scanlatorPriority = listOf("A", "B"),
            smartMergeOverride = true,
        )
        val mangaRepository = mockk<MangaRepository>()
        coEvery { mangaRepository.getMangaById(1) } returns manga
        coEvery { mangaRepository.getMangaById(2) } returns Manga.create().copy(id = 2)
        val chapterRepository = mockk<ChapterRepository>()
        coEvery { chapterRepository.getChaptersByMangaIds(listOf(1L)) } returns chapters
        val filter = UpdatesVisibilityFilter(
            mangaRepository,
            chapterRepository,
            ChapterVisibility(LibraryPreferences(InMemoryPreferenceStore())),
        )

        val updates = chapters.map { update(it.mangaId, it.id) } + update(mangaId = 2, chapterId = 9)

        filter.filter(updates).map { it.chapterId } shouldBe listOf(1L, 2L, 9L)
    }

    private fun chapter(id: Long, number: Double, scanlator: String) = Chapter.create().copy(
        id = id,
        mangaId = 1,
        url = "/$id",
        name = "Chapter $number",
        chapterNumber = number,
        scanlator = scanlator,
    )

    private fun update(mangaId: Long, chapterId: Long) = UpdatesWithRelations(
        mangaId = mangaId,
        ogMangaTitle = "M",
        chapterId = chapterId,
        chapterName = "C",
        scanlator = null,
        chapterUrl = "/$chapterId",
        read = false,
        bookmark = false,
        lastPageRead = 0,
        sourceId = 1,
        dateFetch = Instant.fromEpochMilliseconds(0),
        coverData = MangaCover(mangaId, 1, true, null, null),
    )
}
// KNS <--
