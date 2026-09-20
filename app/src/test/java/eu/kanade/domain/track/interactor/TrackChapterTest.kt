package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.store.DelayedTrackingStore
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
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track

class TrackChapterTest {
    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val insert = mockk<InsertTrack>(relaxed = true)
    private val delayed = mockk<DelayedTrackingStore>(relaxed = true)
    private val service = mockk<BaseTracker>()
    private val preferences = mockk<eu.kanade.domain.track.service.TrackPreferences>(relaxed = true)
    private val refreshTracks = mockk<RefreshTracks>(relaxed = true)
    private val subject = TrackChapter(getTracks, manager, insert, delayed, preferences, refreshTracks)
    private val cached = Track(1, 2, 3, 4, null, "Title", 1.0, 100, 1, 0.0, "", 10, 20, false)

    init {
        every { preferences.autoSyncProgressFromTrackers().get() } returns false
        coEvery { getTracks.await(2) } returns listOf(cached)
        every { manager.get(3) } returns service
        every { service.isLoggedIn } returns true
    }

    @Test
    fun `already ahead remote progress is not rolled back and returned dates persist`() = runTest {
        coEvery { service.refresh(any()) } returns cached.copy(lastChapterRead = 12.0).toDbTrack()
        coEvery { service.update(any(), true) } answers {
            firstArg<eu.kanade.tachiyomi.data.database.models.Track>().apply { finished_reading_date = 99 }
        }
        subject.await(mockk(), 2, 5.0, setupJobOnFailure = false)
        coVerify { service.update(match { it.last_chapter_read == 12.0 }, true) }
        coVerify { insert.await(match { it.lastChapterRead == 12.0 && it.finishDate == 99L && it.startDate == 10L }) }
    }

    @Test
    fun `reread status and start date survive chapter advancement`() = runTest {
        coEvery { service.refresh(any()) } returns cached.copy(status = 6, lastChapterRead = 2.0).toDbTrack()
        coEvery { service.update(any(), true) } answers { firstArg() }
        subject.await(mockk(), 2, 3.0, setupJobOnFailure = false)
        coVerify { service.update(match { it.status == 6L && it.last_chapter_read == 3.0 && it.started_reading_date == 10L }, true) }
        coVerify { insert.await(match { it.status == 6L && it.lastChapterRead == 3.0 }) }
    }

    @Test
    fun `cached ahead progress and invalid chapter numbers skip network`() = runTest {
        subject.await(mockk(), 2, 1.0, setupJobOnFailure = false)
        subject.await(mockk(), 2, Double.NaN, setupJobOnFailure = false)
        coVerify(exactly = 0) { service.refresh(any()) }
        cached.toDbTrack().toDomainTrack()!!.startDate shouldBe 10L
    }

    @Test
    fun `reader invokes full reconciliation only when auto sync is enabled`() = runTest {
        coEvery { service.refresh(any()) } returns cached.toDbTrack()
        coEvery { service.update(any(), true) } answers { firstArg() }
        every { preferences.autoSyncProgressFromTrackers().get() } returns false
        subject.await(mockk(), 2, 5.0, setupJobOnFailure = false)
        coVerify(exactly = 0) { refreshTracks.await(any()) }
        every { preferences.autoSyncProgressFromTrackers().get() } returns true
        subject.await(mockk(), 2, 6.0, setupJobOnFailure = false)
        coVerify(exactly = 1) { refreshTracks.await(2) }
    }
}
