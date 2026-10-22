package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Instant

// KNS -->
/** A chapter the source re-uploads under a new URL keeps its bookmark and its bookmark color. */
class SyncChaptersBookmarkColorTest {

    @Test
    fun `a re-uploaded chapter takes over the removed chapter's bookmark color`() = runBlocking<Unit> {
        val manga = Manga.create().copy(id = 1, source = 2, ogTitle = "Title", url = "/m")
        val removed = Chapter.create().copy(
            id = 10,
            mangaId = 1,
            url = "/old-5",
            name = "Chapter 5",
            chapterNumber = 5.0,
            bookmark = true,
            bookmarkColor = 4,
            dateFetch = Instant.fromEpochMilliseconds(1),
        )
        val plain = removed.copy(id = 11, url = "/old-6", name = "Chapter 6", chapterNumber = 6.0, bookmarkColor = 0)
        val repository = mockk<ChapterRepository>(relaxed = true)
        coEvery { repository.getChapterByMangaId(1, any()) } returns listOf(removed, plain)
        val added = slot<List<Chapter>>()
        coEvery { repository.updateFromRemote(any(), capture(added), any()) } answers {
            added.captured.mapIndexed { i, chapter -> chapter.copy(id = 100L + i) }
        }
        val source = mockk<Source>()
        every { source.id } returns 2L
        val store = InMemoryPreferenceStore()
        val sync = SyncChaptersWithSource(
            downloadManager = mockk(relaxed = true) { every { getQueuedDownloadOrNull(any()) } returns null },
            downloadProvider = mockk(relaxed = true),
            chapterRepository = repository,
            shouldUpdateDbChapter = ShouldUpdateDbChapter(),
            updateManga = mockk(relaxed = true),
            getExcludedScanlators = mockk { coEvery { await(any()) } returns emptySet() },
            libraryPreferences = LibraryPreferences(store),
            syncPreferences = SyncPreferences(store),
            downloadPreferences = DownloadPreferences(store),
        )

        sync.await(
            rawSourceChapters = listOf(
                SChapter.create().apply {
                    url = "/new-5"
                    name = "Chapter 5"
                },
                SChapter.create().apply {
                    url = "/new-6"
                    name = "Chapter 6"
                },
            ),
            manga = manga,
            source = source,
        )

        val byUrl = added.captured.associateBy { it.url }
        byUrl.getValue("/new-5").bookmark shouldBe true
        byUrl.getValue("/new-5").bookmarkColor shouldBe 4
        byUrl.getValue("/new-6").bookmark shouldBe true
        byUrl.getValue("/new-6").bookmarkColor shouldBe 0
    }
}
// KNS <--
