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
    private val subject = UnifiedTrackerEdits(updateTracks)
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

    private fun result(updated: Set<Long> = emptySet(), failed: Map<Tracker, Throwable> = emptyMap()) =
        UpdateTracks.Result(
            updatedTrackerIds = updated,
            failedTrackerIds = failed.keys.mapTo(mutableSetOf()) { it.id },
            skippedTrackerIds = emptySet(),
            failures = failed.toList(),
        )
}
// KNS <--
