package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.Tracker
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

// KNS -->
class UnifiedTrackerEditsTest {
    private val updateTracks = mockk<UpdateTracks>()
    // KNS: a preferred-tracker sync that has nothing to do (see PreferredTrackerSyncTest for the sync itself)
    private val sync = mockk<PreferredTrackerSync> { coEvery { run(any(), any()) } returns null }
    private val subject = UnifiedTrackerEdits(updateTracks, sync)
    private val anilist = mockk<Tracker> { every { id } returns 2L }

    @Test
    fun `a failure is kept until the tracker succeeds again`() = runTest {
        coEvery { updateTracks.await(1, any()) } returns result(failed = mapOf(anilist to IllegalStateException("401")))
        subject.apply(1, UpdateTracks.Change.Progress(3))
        subject.failures(1).first() shouldBe mapOf(2L to "401")
        subject.failures(9).first() shouldBe emptyMap()

        coEvery { updateTracks.await(1, any()) } returns result(updated = setOf(2L))
        subject.apply(1, UpdateTracks.Change.Progress(4))
        subject.failures(1).first() shouldBe emptyMap()
    }

    @Test
    fun `removing a tracker drops its failure marker`() = runTest {
        coEvery { updateTracks.await(1, any()) } returns result(failed = mapOf(anilist to IllegalStateException("401")))
        coEvery { updateTracks.remove(1, setOf(2L), remotely = true) } returns emptyList()
        subject.apply(1, UpdateTracks.Change.Progress(3))

        subject.remove(1, setOf(2L), remotely = true) shouldBe emptyList()
        subject.failures(1).first() shouldBe emptyMap()
    }

    @Test
    fun `edits run one after another`() = runTest {
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<Int>()
        coEvery { updateTracks.await(1, UpdateTracks.Change.Progress(1)) } coAnswers {
            order += 1
            release.await()
            result()
        }
        coEvery { updateTracks.await(1, UpdateTracks.Change.Progress(2)) } coAnswers {
            order += 2
            result()
        }
        val first = async { subject.apply(1, UpdateTracks.Change.Progress(1)) }
        val second = async { subject.apply(1, UpdateTracks.Change.Progress(2)) }
        testScheduler.runCurrent()
        order shouldBe listOf(1)
        release.complete(Unit)
        first.await()
        second.await()
        order shouldBe listOf(1, 2)
    }

    @Test
    fun `a sync request meets one that is still waiting and a running one is not doubled`() = runTest {
        val release = CompletableDeferred<Unit>()
        var runs = 0
        coEvery { sync.run(1, any()) } coAnswers {
            runs++
            if (runs == 1) release.await()
            null
        }
        val running = async { subject.sync(1) }
        testScheduler.runCurrent()
        val waiting = async { subject.sync(1) }
        testScheduler.runCurrent()
        // the second request is queued behind the running one; a third finds it waiting and is dropped
        subject.sync(1) shouldBe null
        runs shouldBe 1
        release.complete(Unit)
        running.await()
        waiting.await()
        runs shouldBe 2
    }

    @Test
    fun `a sync waits for a running edit`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { updateTracks.await(1, any()) } coAnswers {
            release.await()
            result()
        }
        val edit = async { subject.apply(1, UpdateTracks.Change.Progress(1)) }
        testScheduler.runCurrent()
        val sync = async { subject.sync(1) }
        testScheduler.runCurrent()
        io.mockk.coVerify(exactly = 0) { this@UnifiedTrackerEditsTest.sync.run(any(), any()) }
        release.complete(Unit)
        edit.await()
        sync.await()
        // the edit's own sync and the requested one
        io.mockk.coVerify(exactly = 2) { this@UnifiedTrackerEditsTest.sync.run(1, any()) }
    }

    @Test
    fun `a tracker that failed its last edit is not trusted by the sync`() = runTest {
        coEvery { updateTracks.await(1, any()) } returns result(failed = mapOf(anilist to IllegalStateException("401")))
        subject.apply(1, UpdateTracks.Change.Progress(3))

        subject.sync(1, unreliableTrackerIds = setOf(9L))

        // the failed tracker and the one that just failed to refresh both go to the sync as unreliable
        io.mockk.coVerify { sync.run(1, setOf(9L, 2L)) }
    }

    @Test
    fun `a synced tracker clears its earlier error and a failed one is marked`() = runTest {
        val outcome = PreferredTrackerSync.Outcome(
            preferredTrackerId = 1,
            written = setOf(2L),
            unchanged = emptySet(),
            failures = listOf(anilist to IllegalStateException("offline")),
        )
        coEvery { sync.run(1, any()) } returns outcome
        subject.sync(1)
        subject.failures(1).first() shouldBe mapOf(2L to "offline")

        coEvery { sync.run(1, any()) } returns outcome.copy(failures = emptyList(), unchanged = setOf(2L))
        subject.sync(1)
        subject.failures(1).first() shouldBe emptyMap()
    }

    @Test
    fun `a sync that cannot read the saved records does not fail the caller`() = runTest {
        coEvery { sync.run(1, any()) } throws IllegalStateException("db")
        subject.sync(1) shouldBe null
    }

    private fun result(updated: Set<Long> = emptySet(), failed: Map<Tracker, Throwable> = emptyMap()) =
        UpdateTracks.Result(
            updatedTrackerIds = updated,
            failedTrackerIds = failed.keys.mapTo(mutableSetOf()) { it.id },
            skippedTrackerIds = emptySet(),
            failures = failed.toList(),
        )
}
// KNS <--
