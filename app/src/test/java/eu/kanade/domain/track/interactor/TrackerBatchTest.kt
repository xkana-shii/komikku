package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.Tracker
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

class TrackerBatchTest {
    private val insert = mockk<InsertTrack>(relaxed = true)
    private val entries = (1L..3L).map { id ->
        val track = Track(id, 10, id, id, null, "Title", 3.0, 100, 1, 0.0, "", 0, 0, false)
        val service = mockk<Tracker> {
            every { this@mockk.id } returns id
            coEvery { refresh(any()) } returns track.copy(startDate = id * 10).toDbTrack()
            coEvery { update(any(), any()) } returns track.copy(finishDate = id * 100).toDbTrack()
        }
        service to track
    }

    @Test
    fun `real insert interactor propagates database errors to the batch`() = runTest {
        val repository = mockk<TrackRepository>(relaxed = true)
        coEvery { repository.insert(match { it.trackerId == 2L }) } throws IllegalStateException("database")
        val results = trackerBatch(entries, InsertTrack(repository)) { update(binding) }
        results.filter { it.error != null }.map { it.service.id } shouldBe listOf(2L)
        coVerify(exactly = 3) { repository.insert(any()) }
    }

    @Test
    fun `remote and persistence failures for the same service are both retained`() = runTest {
        coEvery { entries[0].first.update(any(), any()) } throws IllegalStateException("remote")
        coEvery { insert.awaitOrThrow(match { it.trackerId == 1L }) } throws IllegalStateException("local")
        val results = trackerBatch(entries, insert) {
            refresh()
            update(binding)
        }
        results.first().failures.map { it.message } shouldBe listOf("remote", "local")
        results.drop(1).all { it.failures.isEmpty() } shouldBe true
    }

    @Test
    fun `B and C finish while A is blocked and batch persists correct returned objects after settling`() = runTest {
        val release = CompletableDeferred<Unit>()
        val started = mutableListOf<Long>()
        entries.forEach { (service, binding) ->
            coEvery { service.update(any(), any()) } coAnswers {
                started += binding.trackerId
                if (binding.trackerId == 1L) release.await()
                binding.copy(startDate = binding.trackerId * 100, remoteUrl = "returned/${binding.trackerId}").toDbTrack()
            }
        }
        val result = async { trackerBatch(entries + entries.first(), insert) { update(binding) } }
        try {
            testScheduler.runCurrent()
            started shouldBe listOf(1L, 2L, 3L)
            result.isCompleted shouldBe false
            coVerify(exactly = 0) { insert.awaitOrThrow(any()) }
        } finally {
            release.complete(Unit)
        }
        result.await().map { it.error } shouldBe listOf(null, null, null)
        entries.forEach { (service, binding) ->
            val id = binding.trackerId
            coVerify(exactly = 1) { service.update(any(), any()) }
            coVerify(exactly = 1) { insert.awaitOrThrow(match { it.trackerId == id && it.startDate == id * 100 && it.remoteUrl == "returned/$id" }) }
        }
    }

    @Test
    fun `first middle and all remote failures preserve every sibling result and refreshed data`() = runTest {
        for (failed in listOf(setOf(1L), setOf(2L), setOf(1L, 2L, 3L))) {
            entries.forEach { (service, binding) ->
                coEvery { service.update(any(), any()) } coAnswers {
                    if (binding.trackerId in failed) error("remote ${binding.trackerId}")
                    binding.copy(finishDate = binding.trackerId * 100).toDbTrack()
                }
            }
            val results = trackerBatch(entries, insert) {
                refresh()
                update(binding)
            }
            results.filter { it.error != null }.map { it.service.id }.toSet() shouldBe failed
            results.forEach { result ->
                val id = result.service.id
                if (id in failed) {
                    result.track?.startDate shouldBe id * 10
                } else {
                    result.track?.finishDate shouldBe id * 100
                }
            }
        }
    }

    @Test
    fun `persistence is serial and a failed insert does not prevent other successful records`() = runTest {
        val release = CompletableDeferred<Unit>()
        val stored = mutableListOf<Long>()
        coEvery { insert.awaitOrThrow(any()) } coAnswers {
            val id = firstArg<Track>().trackerId
            if (id == 1L) release.await()
            if (id == 2L) error("database")
            stored += id
        }
        val result = async { trackerBatch(entries, insert) { update(binding) } }
        testScheduler.runCurrent()
        stored shouldBe emptyList()
        coVerify(exactly = 1) { insert.awaitOrThrow(any()) }
        release.complete(Unit)
        result.await().filter { it.error != null }.map { it.service.id } shouldBe listOf(2L)
        stored shouldBe listOf(1L, 3L)
    }
}
