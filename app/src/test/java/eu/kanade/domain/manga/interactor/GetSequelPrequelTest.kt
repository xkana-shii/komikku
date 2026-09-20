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

class GetSequelPrequelTest {
    private val manager = mockk<TrackerManager>()
    private val preferences = mockk<TrackPreferences>()
    private val repository = mockk<MangaRepository>()
    private val tracks = mockk<GetTracks>()
    private val service = mockk<BaseTracker>()
    private val manga = Manga.create().copy(id = 10, source = 1)
    private val binding = Track(1, 10, 1, 100, null, "Title", 0.0, 0, 1, 0.0, "", 0, 0, false)
    private val relation = SequelPrequelEntry("Sequel", "https://example.invalid/101", SequelPrequelRelation.SEQUEL, 1, 101, "https://example.invalid/cover")
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
    fun `tracker binding resolves local entry without inserting a stub`() = runTest {
        coEvery { tracks.await() } returns listOf(binding.copy(mangaId = 20, remoteId = 101))
        val found = subject.await(manga, listOf(binding)).single()
        found.localMangaId shouldBe 20L
        found.coverUrl shouldBe relation.coverUrl
        found.relation shouldBe SequelPrequelRelation.SEQUEL
        coVerify(exactly = 0) { repository.insertNetworkManga(any(), any()) }
    }

    @Test
    fun `cache respects binding changes explicit refresh and preference changes`() = runTest {
        repeat(2) { subject.await(manga, listOf(binding)) }
        coVerify(exactly = 1) { service.getRelatedEntries(100) }
        subject.await(manga, listOf(binding.copy(remoteId = 102)))
        subject.await(manga, listOf(binding), refresh = true)
        every { preferences.resolvePreferredTracker(any(), any()) } returns 1
        subject.await(manga, listOf(binding))
        coVerify(exactly = 3) { service.getRelatedEntries(100) }
        coVerify(exactly = 1) { service.getRelatedEntries(102) }
    }

    @Test
    fun `empty and failed lookups are retried and unresolved entries remain transient`() = runTest {
        coEvery { service.getRelatedEntries(100) } returns emptyList()
        subject.await(manga, listOf(binding)) shouldBe emptyList()
        coEvery { service.getRelatedEntries(100) } throws IllegalStateException("offline")
        assertThrows<IllegalStateException> { subject.await(manga, listOf(binding)) }
        coEvery { service.getRelatedEntries(100) } returns listOf(relation)
        subject.await(manga, listOf(binding)).single().localMangaId shouldBe null
        coVerify(exactly = 3) { service.getRelatedEntries(100) }
        coVerify(exactly = 0) { repository.insertNetworkManga(any(), any()) }
    }
}
