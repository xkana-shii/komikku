package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track

class RefreshTracksTest {
    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val insert = mockk<InsertTrack>(relaxed = true)
    private val local = mockk<SyncChapterProgressWithTrack>()
    private val preferences = mockk<TrackPreferences>(relaxed = true)
    private val one = tracker(1)
    private val two = tracker(2)
    private val subject = RefreshTracks(getTracks, manager, insert, local, preferences)
    private fun track(id: Long, progress: Double, status: Long = 1) = Track(id, 10, id, id, null, "Title", progress, 100, status, 0.0, "", 10, 20, false)
    private fun tracker(id: Long) = mockk<BaseTracker> {
        every { this@mockk.id } returns id
        every { isLoggedIn } returns true
        every { getRereadingStatus() } returns 6
        every { getReadingStatus() } returns 1
        coEvery { refresh(any()) } answers { firstArg() }
        coEvery { update(any(), any()) } answers { firstArg() }
    }

    init {
        every { manager.get(1) } returns one
        every { manager.get(2) } returns two
        every { preferences.autoSyncProgressFromTrackers().get() } returns true
        every { preferences.resolvePreferredTracker(any(), any()) } returns null
        coEvery { local.await(any(), any(), any(), any()) } returns null
        coEvery { local.sync(any(), any(), any(), any()) } returns null
    }

    @Test
    fun `plus and minus start sibling writes before a slow tracker finishes`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        for (delta in listOf(-1, 1)) {
            val release = CompletableDeferred<Unit>()
            val started = mutableListOf<Long>()
            coEvery { one.update(any(), true) } coAnswers {
                started += 1L
                release.await()
                firstArg()
            }
            coEvery { two.update(any(), true) } coAnswers {
                started += 2L
                firstArg()
            }
            val operation = async { subject.adjustProgress(10, delta) }
            try {
                testScheduler.runCurrent()
                started shouldBe listOf(1L, 2L)
                operation.isCompleted shouldBe false
            } finally {
                release.complete(Unit)
            }
            operation.await() shouldBe emptyList()
        }
    }

    @Test
    fun `configured reconciliation advances only lagging tracker`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        subject.await(10) shouldBe emptyList()
        coVerify { one.update(match { it.last_chapter_read == 8.0 && it.started_reading_date == 10L }, false) }
        coVerify(exactly = 0) { two.update(any(), any()) }
    }

    @Test
    fun `rereading and preferred lower progress do not cause rollback or fast forward`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0, 6), track(2, 100.0, 2))
        subject.await(10) shouldBe emptyList()
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        every { preferences.resolvePreferredTracker(any(), any()) } returns 1
        subject.await(10) shouldBe emptyList()
        coVerify(exactly = 0) { one.update(any(), any()) }
        coVerify(exactly = 0) { two.update(any(), any()) }
    }

    @Test
    fun `refresh failures stay visible and prevent propagating stale values`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        coEvery { two.refresh(any()) } throws IllegalStateException("remote failed")
        val errors = subject.await(10)
        errors.size shouldBe 1
        errors.single().first shouldBe two
        coVerify(exactly = 0) { one.update(any(), any()) }
    }

    @Test
    fun `single tracker behavior keeps local sync but does not reconcile services`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0))
        subject.await(10) shouldBe emptyList()
        coVerify { local.sync(10, any(), one, true) }
        coVerify(exactly = 0) { one.update(any(), any()) }
    }

    @Test
    fun `auto sync off refreshes and persists without any reconciliation`() = runTest {
        every { preferences.autoSyncProgressFromTrackers().get() } returns false
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        subject.await(10) shouldBe emptyList()
        coVerify(exactly = 2) { insert.await(any()) }
        coVerify(exactly = 0) {
            one.update(any(), any())
            two.update(any(), any())
            local.sync(any(), any(), any(), any())
            local.await(any(), any(), any(), any())
        }
    }

    @Test
    fun `plus refreshes ahead remote and persists returned dates on every service`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        coEvery { two.refresh(any()) } returns track(2, 12.0).toDbTrack()
        coEvery { one.update(any(), true) } answers { firstArg<eu.kanade.tachiyomi.data.database.models.Track>().apply { finished_reading_date = 99 } }
        subject.adjustProgress(10, 1) shouldBe emptyList()
        coVerify {
            one.update(match { it.last_chapter_read == 13.0 }, true)
            two.update(match { it.last_chapter_read == 13.0 }, true)
        }
        coVerify { insert.awaitOrThrow(match { it.trackerId == 1L && it.lastChapterRead == 13.0 && it.finishDate == 99L }) }
    }

    @Test
    fun `minus updates every service and clamps at zero`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        subject.adjustProgress(10, -1) shouldBe emptyList()
        coVerify {
            one.update(match { it.last_chapter_read == 7.0 }, true)
            two.update(match { it.last_chapter_read == 7.0 }, true)
        }
        coEvery { getTracks.await(10) } returns listOf(track(1, 0.0), track(2, 0.0))
        subject.adjustProgress(10, -1) shouldBe emptyList()
        coVerify {
            one.update(match { it.last_chapter_read == 0.0 }, true)
            two.update(match { it.last_chapter_read == 0.0 }, true)
        }
    }

    @Test
    fun `failed update does not block other services and next attempt clears failures`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        coEvery { one.update(any(), any()) } throws IllegalStateException("update failed")
        subject.adjustProgress(10, 1).single().first shouldBe one
        coVerify { two.update(match { it.last_chapter_read == 9.0 }, true) }
        coEvery { one.update(any(), any()) } answers { firstArg() }
        subject.adjustProgress(10, 1) shouldBe emptyList()
    }

    @Test
    fun `failed refresh does not block explicit adjustment of successful service`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0))
        coEvery { one.refresh(any()) } throws IllegalStateException("refresh failed")
        subject.adjustProgress(10, 1).single().first shouldBe one
        coVerify(exactly = 0) { one.update(any(), any()) }
        coVerify { two.update(match { it.last_chapter_read == 9.0 }, true) }
    }

    @Test
    fun `explicit adjustment keeps rereading separate from completed progress`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0, 6), track(2, 100.0, 2))
        subject.adjustProgress(10, 1) shouldBe emptyList()
        coVerify { one.update(match { it.last_chapter_read == 4.0 && it.status == 6L }, true) }
    }

    @Test
    fun `unfollowed MDList is refreshed but never updated or imported`() = runTest {
        val mdlist = mockk<eu.kanade.tachiyomi.data.track.mdlist.MdList> {
            every { id } returns 3L
            every { isLoggedIn } returns true
            coEvery { refresh(any()) } answers { firstArg() }
        }
        every { manager.get(3) } returns mdlist
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0), track(3, 90.0, exh.md.utils.FollowStatus.UNFOLLOWED.long))
        subject.adjustProgress(10, 1) shouldBe emptyList()
        subject.await(10) shouldBe emptyList()
        coVerify(exactly = 0) {
            mdlist.update(any(), any())
            local.sync(any(), any(), mdlist, any())
        }
        coVerify { one.update(match { it.last_chapter_read == 9.0 }, true) }
    }

    @Test
    fun `rereading guard also prevents completed services marking local chapters read`() = runTest {
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0, 6), track(2, 100.0, 2))
        subject.await(10) shouldBe emptyList()
        coVerify(exactly = 0) { local.sync(any(), any(), any(), any()) }
    }

    @Test
    fun `third service failure does not stop successful service reconciliation`() = runTest {
        val three = tracker(3)
        every { manager.get(3) } returns three
        coEvery { three.refresh(any()) } throws IllegalStateException("offline")
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0), track(3, 100.0))
        subject.await(10).single().first shouldBe three
        coVerify { one.update(match { it.last_chapter_read == 8.0 }, false) }
        coVerify(exactly = 0) { three.update(any(), any()) }
    }

    @Test
    fun `plus and minus update three bound trackers even with a preferred service`() = runTest {
        val three = tracker(3)
        every { manager.get(3) } returns three
        every { preferences.resolvePreferredTracker(any(), any()) } returns 1
        coEvery { getTracks.await(10) } returns listOf(track(1, 3.0), track(2, 8.0), track(3, 6.0))
        subject.adjustProgress(10, 1) shouldBe emptyList()
        subject.adjustProgress(10, -1) shouldBe emptyList()
        listOf(one, two, three).forEach { service ->
            coVerify { service.update(match { it.last_chapter_read == 9.0 }, true) }
            coVerify { service.update(match { it.last_chapter_read == 7.0 }, true) }
        }
    }
}
