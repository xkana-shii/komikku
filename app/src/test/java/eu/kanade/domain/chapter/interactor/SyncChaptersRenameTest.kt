package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga

// KNS -->
/** "Trigger chapter rename": a downloaded chapter the source renamed is renamed on disk only when it is on. */
class SyncChaptersRenameTest {

    @Test
    fun `the setting is on by default and keeps the previous fork's key`() {
        val preference = DownloadPreferences(InMemoryPreferenceStore()).triggerChapterRename
        preference.key() shouldBe "trigger_chapter_rename"
        preference.get() shouldBe true
    }

    @Test
    fun `a renamed downloaded chapter is renamed on disk when the setting is on`() = runBlocking<Unit> {
        val (downloadManager, _) = sync(triggerRename = true)
        coVerify(exactly = 1) { downloadManager.renameChapter(any(), any(), any(), any()) }
    }

    @Test
    fun `nothing is renamed on disk when the setting is off`() = runBlocking<Unit> {
        val (downloadManager, renamedInDb) = sync(triggerRename = false)
        coVerify(exactly = 0) { downloadManager.renameChapter(any(), any(), any(), any()) }
        // The chapter itself still takes the source's new name.
        renamedInDb shouldBe true
    }

    private suspend fun sync(triggerRename: Boolean): Pair<DownloadManager, Boolean> {
        val manga = Manga.create().copy(id = 1, source = 2, ogTitle = "Title", url = "/m")
        val existing = Chapter.create().copy(id = 10, mangaId = 1, url = "/c5", name = "Chapter 5", chapterNumber = 5.0)
        val repository = mockk<ChapterRepository>(relaxed = true)
        coEvery { repository.getChapterByMangaId(1, any()) } returns listOf(existing)
        var renamedInDb = false
        coEvery { repository.updateFromRemote(any(), any(), any()) } answers {
            renamedInDb = thirdArg<List<*>>().isNotEmpty()
            emptyList()
        }
        val downloadManager = mockk<DownloadManager>(relaxed = true) {
            every { getQueuedDownloadOrNull(any()) } returns null
            every { isChapterDownloaded(any(), any(), any(), any(), any()) } returns true
        }
        val downloadProvider = mockk<DownloadProvider>(relaxed = true) {
            every { isChapterDirNameChanged(any(), any()) } returns true
        }
        val source = mockk<Source> { every { id } returns 2L }
        val store = InMemoryPreferenceStore()
        val downloadPreferences = DownloadPreferences(store).also { it.triggerChapterRename.set(triggerRename) }
        SyncChaptersWithSource(
            downloadManager = downloadManager,
            downloadProvider = downloadProvider,
            chapterRepository = repository,
            shouldUpdateDbChapter = ShouldUpdateDbChapter(),
            updateManga = mockk(relaxed = true),
            getExcludedScanlators = mockk { coEvery { await(any()) } returns emptySet() },
            libraryPreferences = LibraryPreferences(store),
            syncPreferences = SyncPreferences(store),
            downloadPreferences = downloadPreferences,
        ).await(
            rawSourceChapters = listOf(
                SChapter.create().apply {
                    url = "/c5"
                    name = "Chapter 5: Renamed"
                },
            ),
            manga = manga,
            source = source,
        )
        return downloadManager to renamedInDb
    }
}
// KNS <--
