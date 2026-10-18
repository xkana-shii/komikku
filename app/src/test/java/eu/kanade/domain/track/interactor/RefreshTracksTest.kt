package eu.kanade.domain.track.interactor

import android.content.Context
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track

class RefreshTracksTest {

    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val upsert = mockk<UpsertTrack>(relaxed = true)
    private val sync = mockk<SyncChapterProgressWithTrack>()
    private val subject = RefreshTracks(
        getTracks = getTracks,
        trackerManager = manager,
        upsertTrack = upsert,
        syncChapterProgressWithTrack = sync,
        context = mockk<Context>(),
    )

    private fun track(trackerId: Long) = Track(
        id = trackerId,
        mangaId = 10L,
        trackerId = trackerId,
        remoteId = trackerId,
        libraryId = null,
        title = "Title",
        lastChapterRead = 3.0,
        totalChapters = 100L,
        status = 1L,
        score = 0.0,
        remoteUrl = "",
        startDate = null,
        finishDate = null,
        private = false,
    )

    private fun tracker(id: Long) = mockk<BaseTracker> {
        every { this@mockk.id } returns id
        every { isLoggedIn } returns true
        coEvery { refresh(any()) } answers { firstArg() }
    }

    @Test
    fun `refresh persists and synchronizes each logged-in tracker`() = runTest {
        val first = tracker(1L)
        val second = tracker(2L)
        every { manager.get(1L) } returns first
        every { manager.get(2L) } returns second
        coEvery { getTracks.await(10L) } returns listOf(track(1L), track(2L))
        coEvery { sync.await(any(), any(), any()) } returns null

        subject.await(10L) shouldBe emptyList()

        coVerify(exactly = 2) { upsert.await(any()) }
        coVerify(exactly = 2) { sync.await(any(), any(), any()) }
    }

    @Test
    fun `full synchronization uses the unrestricted path`() = runTest {
        val service = tracker(1L)
        every { manager.get(1L) } returns service
        coEvery { getTracks.await(10L) } returns listOf(track(1L))
        coEvery { sync.sync(any(), any(), any()) } returns null

        subject.await(10L, enhancedTrackersOnly = false) shouldBe emptyList()

        coVerify(exactly = 1) { sync.sync(10L, any(), service) }
        coVerify(exactly = 0) { sync.await(any(), any(), any()) }
    }

    @Test
    fun `a failed tracker does not prevent sibling refreshes`() = runTest {
        val failed = tracker(1L)
        val successful = tracker(2L)
        every { manager.get(1L) } returns failed
        every { manager.get(2L) } returns successful
        coEvery { getTracks.await(10L) } returns listOf(track(1L), track(2L))
        coEvery { failed.refresh(any()) } throws IllegalStateException("Offline")
        coEvery { sync.await(any(), any(), any()) } returns null

        val failures = subject.await(10L)

        failures.single().first shouldBe failed
        coVerify(exactly = 1) { upsert.await(match { it.trackerId == 2L }) }
        coVerify(exactly = 0) { upsert.await(match { it.trackerId == 1L }) }
    }
}
