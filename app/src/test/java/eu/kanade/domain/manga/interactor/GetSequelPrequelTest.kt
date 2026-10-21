package eu.kanade.domain.manga.interactor

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.domain.manga.model.SequelPrequelRelation
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track

// KNS -->
class GetSequelPrequelTest {
    private val manager = mockk<TrackerManager>()
    private val preferences = mockk<TrackPreferences>()
    private val repository = mockk<MangaRepository>()
    private val tracks = mockk<GetTracks>()
    private val service = mockk<BaseTracker>()
    private val manga = Manga.create().copy(id = 10, source = 1)
    private val binding = Track(
        id = 1, mangaId = 10, trackerId = 1, remoteId = 100, libraryId = null, title = "Title",
        lastChapterRead = 0.0, totalChapters = 0, status = 1, score = 0.0, remoteUrl = "",
        startDate = null, finishDate = null, private = false,
    )
    private val relation = SequelPrequelEntry(
        title = "Sequel",
        url = "https://example.invalid/101",
        relation = SequelPrequelRelation.SEQUEL,
        trackerId = 1,
        remoteId = 101,
    )
    private val subject = GetSequelPrequel(manager, preferences, repository, tracks, mockk())

    init {
        every { manager.get(1) } returns service
        every { service.isLoggedIn } returns true
        every { preferences.resolvePreferredTracker(any(), any()) } returns null
        coEvery { repository.getFavorites() } returns emptyList()
        coEvery { tracks.await() } returns emptyList()
        coEvery { service.getRelatedEntries(any()) } returns listOf(relation)
    }

    @Test
    fun `the preferred tracker is asked first and an empty result falls through`() = runTest {
        val other = mockk<BaseTracker> {
            every { isLoggedIn } returns true
            coEvery { getRelatedEntries(200) } returns listOf(relation.copy(trackerId = 2, url = "https://example.invalid/b"))
        }
        every { manager.get(2) } returns other
        every { preferences.resolvePreferredTracker(any(), any()) } returns 1
        coEvery { service.getRelatedEntries(100) } returns emptyList()

        subject.await(manga, listOf(binding.copy(trackerId = 2, remoteId = 200), binding)).single().trackerId shouldBe 2L
        coVerify(exactly = 1) { service.getRelatedEntries(100) }
    }

    @Test
    fun `a tracker binding identifies the local manga and the library flag`() = runTest {
        coEvery { tracks.await() } returns listOf(binding.copy(mangaId = 20, remoteId = 101))
        coEvery { repository.getFavorites() } returns listOf(Manga.create().copy(id = 20, favorite = true))

        val found = subject.await(manga, listOf(binding)).single()

        found.localMangaId shouldBe 20L
        found.inLibrary shouldBe true
    }

    @Test
    fun `results are cached until the bindings change or a refresh is asked`() = runTest {
        repeat(2) { subject.await(manga, listOf(binding)) }
        coVerify(exactly = 1) { service.getRelatedEntries(100) }
        subject.await(manga, listOf(binding.copy(remoteId = 102)))
        subject.await(manga, listOf(binding), refresh = true)
        coVerify(exactly = 2) { service.getRelatedEntries(100) }
        coVerify(exactly = 1) { service.getRelatedEntries(102) }
    }

    @Test
    fun `empty and failed lookups are not cached`() = runTest {
        coEvery { service.getRelatedEntries(100) } returns emptyList()
        subject.await(manga, listOf(binding)) shouldBe emptyList()
        coEvery { service.getRelatedEntries(100) } throws IllegalStateException("offline")
        assertThrows<IllegalStateException> { subject.await(manga, listOf(binding)) }
        coEvery { service.getRelatedEntries(100) } returns listOf(relation)
        subject.await(manga, listOf(binding)).single().localMangaId shouldBe null
        coVerify(exactly = 3) { service.getRelatedEntries(100) }
    }

    @Test
    fun `logged out trackers are not asked`() = runTest {
        every { service.isLoggedIn } returns false
        subject.await(manga, listOf(binding)) shouldBe emptyList()
        coVerify(exactly = 0) { service.getRelatedEntries(any()) }
    }
}
// KNS <--
