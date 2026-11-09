package eu.kanade.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.domain.source.models.RemoteMangaUpdate
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import java.io.IOException

// KNS -->
class CheckDuplicateLibraryMangaTest {

    private val getDuplicates = mockk<GetDuplicateLibraryManga>()
    private val updateFromRemote = mockk<UpdateMangaFromRemote>()
    private val chapterRepository = mockk<ChapterRepository>()
    private val mangaRepository = mockk<MangaRepository>()
    private val check = CheckDuplicateLibraryManga(getDuplicates, updateFromRemote, chapterRepository, mangaRepository)

    private val listed = Manga.create().copy(id = 10, source = 5, ogTitle = "Mr.A's Farm", initialized = false)
    private val detailed = listed.copy(ogAuthor = "Nachioboy", initialized = true)
    private val library = MangaWithChapterCount(
        Manga.create().copy(id = 1, favorite = true, ogTitle = "Mr.A's Farm"),
        28,
    )

    private suspend fun MockKMatcherScope.loadFrom(manga: Manga, details: Boolean = true) = updateFromRemote(
        manga = manga,
        fetchDetails = details,
        fetchChapters = true,
        manualFetch = false,
        fetchWindow = null,
        throttleFunc = any<suspend () -> Unit>(),
    )

    private fun chapters(count: Int) = List(count) { Chapter.create().copy(id = it.toLong(), mangaId = 10) }

    @Test
    fun `details and chapters are loaded before looking for duplicates`() = runBlocking<Unit> {
        var loaded = false
        coEvery { chapterRepository.getChapterByMangaId(10) } answers { if (loaded) chapters(28) else emptyList() }
        coEvery { loadFrom(listed) } answers {
            loaded = true
            Result.success(RemoteMangaUpdate(detailed, emptyList()))
        }
        coEvery { mangaRepository.getMangaById(10) } returns detailed
        coEvery { getDuplicates(detailed, 28L) } returns listOf(library)

        val result = check(listed)

        result.manga shouldBe detailed
        result.chapterCount shouldBe 28L
        result.duplicates shouldBe listOf(library)
    }

    @Test
    fun `an entry that already has its details and chapters is not fetched again`() = runBlocking<Unit> {
        coEvery { chapterRepository.getChapterByMangaId(10) } returns chapters(3)
        coEvery { getDuplicates(detailed, 3L) } returns emptyList()

        val result = check(detailed)

        result.chapterCount shouldBe 3L
    }

    @Test
    fun `a source that fails still gives the duplicates found with what is known`() = runBlocking<Unit> {
        coEvery { chapterRepository.getChapterByMangaId(10) } returns emptyList()
        coEvery { loadFrom(listed) } returns
            Result.failure(IOException("offline"))
        coEvery { mangaRepository.getMangaById(10) } returns listed
        coEvery { getDuplicates(listed, null) } returns listOf(library)

        val result = check(listed)

        result.chapterCount shouldBe null
        result.duplicates shouldBe listOf(library)
    }

    @Test
    fun `looking up without loading never touches the source`() = runBlocking<Unit> {
        coEvery { chapterRepository.getChapterByMangaId(10) } returns emptyList()
        coEvery { getDuplicates(listed, null) } returns listOf(library)

        check(listed, loadDetails = false).duplicates shouldBe listOf(library)
    }

    @Test
    fun `a failed load is reported as inconclusive`() = runBlocking<Unit> {
        coEvery { chapterRepository.getChapterByMangaId(10) } returns emptyList()
        coEvery { loadFrom(listed) } returns Result.failure(IOException("offline"))
        coEvery { mangaRepository.getMangaById(10) } returns listed
        coEvery { getDuplicates(listed, null) } returns emptyList()

        check(listed).complete shouldBe false
    }

    @Test
    fun `an empty chapter list that was fetched is not fetched again`() = runBlocking<Unit> {
        val manga = detailed.copy(id = 77)
        var calls = 0
        coEvery { chapterRepository.getChapterByMangaId(77) } returns emptyList()
        coEvery { loadFrom(manga, details = false) } answers {
            calls++
            Result.success(RemoteMangaUpdate(manga, emptyList()))
        }
        coEvery { mangaRepository.getMangaById(77) } returns manga
        coEvery { getDuplicates(manga, null) } returns emptyList()

        check(manga)
        check(manga)

        calls shouldBe 1
    }
}
// KNS <--
