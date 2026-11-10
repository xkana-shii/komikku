package eu.kanade.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.domain.source.models.RemoteMangaUpdate
import org.junit.jupiter.api.Test
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

    private fun count(id: Long, value: () -> Long) = coEvery { chapterRepository.countChaptersByMangaId(id) } answers { value() }

    @Test
    fun `the check before the dialog only uses local data and never touches the source`() = runBlocking<Unit> {
        count(10) { 0 }
        coEvery { getDuplicates(listed, null, null) } returns listOf(library)

        val result = check(listed)

        result.duplicates shouldBe listOf(library)
        result.chapterCount shouldBe null
        coVerify(exactly = 0) { loadFrom(listed) }
        coVerify(exactly = 0) { chapterRepository.getChapterByMangaId(any(), any()) }
    }

    @Test
    fun `enriching loads details and chapters and updates the incoming entry`() = runBlocking<Unit> {
        var loaded = false
        count(10) { if (loaded) 28 else 0 }
        coEvery { loadFrom(listed) } answers {
            loaded = true
            Result.success(RemoteMangaUpdate(detailed, emptyList()))
        }
        coEvery { mangaRepository.getMangaById(10) } returns detailed
        coEvery { getDuplicates(detailed, 28L, null) } returns listOf(library)

        val result = check.enrich(DuplicateCheck(listed, null, listOf(library)))

        result.manga shouldBe detailed
        result.chapterCount shouldBe 28L
        result.duplicates shouldBe listOf(library)
        result.complete shouldBe true
    }

    @Test
    fun `an entry that already has its details and chapters is not enriched`() = runBlocking<Unit> {
        val known = DuplicateCheck(detailed, 3, listOf(library))
        check.canEnrich(detailed, 3) shouldBe false
        check.enrich(known) shouldBe known
    }

    @Test
    fun `a failed load keeps the local duplicates and is reported as inconclusive`() = runBlocking<Unit> {
        val manga = listed.copy(id = 11)
        count(11) { 0 }
        coEvery { loadFrom(manga) } returns Result.failure(IOException("offline"))
        coEvery { mangaRepository.getMangaById(11) } returns manga
        coEvery { getDuplicates(manga, null, null) } returns emptyList()

        val result = check.enrich(DuplicateCheck(manga, null, listOf(library)))

        result.duplicates shouldBe listOf(library)
        result.complete shouldBe false
    }

    @Test
    fun `concurrent enrichments of one entry share a single load`() = runBlocking<Unit> {
        val manga = listed.copy(id = 12)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        count(12) { 0 }
        coEvery { loadFrom(manga) } coAnswers {
            calls++
            gate.await()
            Result.success(RemoteMangaUpdate(manga, emptyList()))
        }
        coEvery { mangaRepository.getMangaById(12) } returns manga
        coEvery { getDuplicates(manga, null, null) } returns emptyList()

        val first = async { check.enrich(DuplicateCheck(manga, null, emptyList())) }
        val second = async { check.enrich(DuplicateCheck(manga, null, emptyList())) }
        delay(200)
        gate.complete(Unit)
        first.await()
        second.await()

        calls shouldBe 1
    }

    @Test
    fun `an empty chapter list that was fetched is not fetched again`() = runBlocking<Unit> {
        val manga = detailed.copy(id = 77)
        var calls = 0
        count(77) { 0 }
        coEvery { loadFrom(manga, details = false) } answers {
            calls++
            Result.success(RemoteMangaUpdate(manga, emptyList()))
        }
        coEvery { mangaRepository.getMangaById(77) } returns manga
        coEvery { getDuplicates(manga, null, null) } returns emptyList()

        check.enrich(DuplicateCheck(manga, null, emptyList()))
        check.canEnrich(manga, null) shouldBe false

        calls shouldBe 1
    }
}
// KNS <--
