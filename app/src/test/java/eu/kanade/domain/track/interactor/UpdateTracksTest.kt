package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

class UpdateTracksTest {
    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val insert = mockk<InsertTrack>(relaxed = true)
    private val delete = mockk<DeleteTrack>(relaxed = true)
    private val subject = UpdateTracks(getTracks, manager, insert, delete)
    private val bindings = (1L..3L).map { Track(it, 10, it, it, null, "Title", 3.0, 100, 1, 0.0, "", 0, 0, false) }
    private val services = bindings.map { binding ->
        mockk<BaseTracker>(moreInterfaces = arrayOf(DeletableTracker::class)) {
            every { id } returns binding.trackerId
            every { isLoggedIn } returns true
            every { supportsReadingDates } returns true
            every { getCompletionStatus() } returns 2
            every { getStatusList() } returns listOf(1, 2)
            every { getStatus(1) } returns MR.strings.reading
            every { getStatus(2) } returns MR.strings.completed
            every { getScoreList() } returns (0..10).map(Int::toString).toImmutableList()
            every { indexToScore(any()) } answers { firstArg<Int>().toDouble() }
            every { get10PointScore(any()) } answers { firstArg<Track>().score }
            coEvery { refresh(any()) } answers { firstArg() }
            coEvery { update(any(), any()) } answers { firstArg<DbTrack>().toDomainTrack()!!.copy(startDate = binding.trackerId * 100).toDbTrack() }
        }
    }
    init {
        coEvery { getTracks.await(10) } returns bindings
        services.forEachIndexed { index, service ->
            every { manager.get((index + 1).toLong()) } returns service
            coEvery { (service as DeletableTracker).delete(any()) } returns Unit
        }
    }

    @Test
    fun `progress status score and both dates start all writes before a slow first tracker finishes`() = runTest {
        val changes = listOf(UpdateTracks.Change.Progress(8), UpdateTracks.Change.Status(1, 2), UpdateTracks.Change.Score(1, "7"), UpdateTracks.Change.Date(true, 500), UpdateTracks.Change.Date(false, 900))
        for (change in changes) {
            val release = CompletableDeferred<Unit>()
            val started = mutableListOf<Long>()
            services.forEachIndexed { index, service ->
                coEvery { service.update(any(), any()) } coAnswers {
                    started += index + 1L
                    if (index == 0) release.await()
                    firstArg()
                }
            }
            val operation = async { subject.await(10, change) }
            try {
                testScheduler.runCurrent()
                started shouldBe listOf(1L, 2L, 3L)
                operation.isCompleted shouldBe false
            } finally {
                release.complete(Unit)
            }
            operation.await() shouldBe emptyList()
        }
    }

    @Test
    fun `two and three bound trackers all receive explicit progress and preserve individual returns`() = runTest {
        for (count in 2..3) {
            coEvery { getTracks.await(10) } returns bindings.take(count)
            subject.await(10, UpdateTracks.Change.Progress(8)) shouldBe emptyList()
            services.take(count).forEachIndexed { index, service ->
                val serviceId = bindings[index].trackerId
                coVerify { service.update(match { it.last_chapter_read == 8.0 }, true) }
                coVerify { insert.awaitOrThrow(match { it.trackerId == serviceId && it.lastChapterRead == 8.0 && it.startDate == serviceId * 100 }) }
            }
        }
    }

    @Test
    fun `preferred score source updates every supporting tracker through its own score conversion`() = runTest {
        val percent = services[1]
        every { percent.indexToScore(any()) } answers { firstArg<Int>() * 10.0 }
        every { percent.get10PointScore(any()) } answers { firstArg<Track>().score / 10.0 }
        subject.await(10, UpdateTracks.Change.Score(1, "7")) shouldBe emptyList()
        coVerify { services[0].update(match { it.score == 7.0 }, false) }
        coVerify { percent.update(match { it.score == 70.0 }, false) }
        coVerify { services[2].update(match { it.score == 7.0 }, false) }
        services.forEachIndexed { index, _ ->
            val serviceId = bindings[index].trackerId
            coVerify { insert.awaitOrThrow(match { it.trackerId == serviceId && it.startDate == serviceId * 100 }) }
        }
    }

    @Test
    fun `score clear propagates and unsupported score service stays untouched`() = runTest {
        every { services[2].getScoreList() } returns emptyList<String>().toImmutableList()
        subject.await(10, UpdateTracks.Change.Score(1, "0")) shouldBe emptyList()
        services.take(2).forEach { service -> coVerify { service.update(match { it.score == 0.0 }, false) } }
        coVerify(exactly = 0) {
            services[2].refresh(any())
            services[2].update(any(), any())
        }
    }

    @Test
    fun `score failure on first service does not stop the other bound services`() = runTest {
        coEvery { services[0].update(any(), any()) } throws IllegalStateException("offline")
        subject.await(10, UpdateTracks.Change.Score(1, "6")).single().first shouldBe services[0]
        services.drop(1).forEach { service -> coVerify { service.update(match { it.score == 6.0 }, false) } }
    }

    @Test
    fun `status and both dates propagate while respecting service support`() = runTest {
        subject.await(10, UpdateTracks.Change.Status(1, 2)) shouldBe emptyList()
        services.forEach { service -> coVerify { service.update(match { it.status == 2L && it.last_chapter_read == 100.0 }, false) } }
        every { services[2].supportsReadingDates } returns false
        subject.await(10, UpdateTracks.Change.Date(true, 500)) shouldBe emptyList()
        subject.await(10, UpdateTracks.Change.Date(false, 900)) shouldBe emptyList()
        services.take(2).forEach { service ->
            coVerify { service.update(match { it.started_reading_date == 500L }, false) }
            coVerify { service.update(match { it.finished_reading_date == 900L }, false) }
        }
        coVerify(exactly = 1) { services[2].update(any(), any()) }
    }

    @Test
    fun `neutral removal removes nothing and local selection only unlinks selected services`() = runTest {
        subject.remove(10, emptySet(), false) shouldBe emptyList()
        coVerify(exactly = 0) { delete.awaitOrThrow(any(), any()) }
        subject.remove(10, setOf(1, 3), false) shouldBe emptyList()
        coVerify {
            delete.awaitOrThrow(10, 1)
            delete.awaitOrThrow(10, 3)
        }
        coVerify(exactly = 0) { delete.awaitOrThrow(10, 2) }
        services.forEach { service -> coVerify(exactly = 0) { (service as DeletableTracker).delete(any()) } }
    }

    @Test
    fun `remote removal failure is reported without stopping selected local or remote removals`() = runTest {
        coEvery { (services[0] as DeletableTracker).delete(any()) } throws IllegalStateException("offline")
        subject.remove(10, setOf(1, 2, 3), true).single().first shouldBe services[0]
        services.forEachIndexed { index, service ->
            val serviceId = bindings[index].trackerId
            coVerify { (service as DeletableTracker).delete(match { it.trackerId == serviceId }) }
        }
        coVerify {
            delete.awaitOrThrow(10, 1)
            delete.awaitOrThrow(10, 2)
            delete.awaitOrThrow(10, 3)
        }
    }

    @Test
    fun `first and middle local failures preserve failed bindings and still remove later selections`() = runTest {
        for (failedId in listOf(1L, 2L)) {
            val remaining = bindings.toMutableList()
            coEvery { delete.awaitOrThrow(10, any()) } coAnswers {
                val id = secondArg<Long>()
                if (id == failedId) error("local $id")
                remaining.removeAll { it.trackerId == id }
            }
            val failures = subject.remove(10, setOf(1, 2, 3), false)
            failures.map { it.first } shouldBe listOf(services[(failedId - 1).toInt()])
            remaining.map { it.trackerId } shouldBe listOf(failedId)
        }
    }

    @Test
    fun `all remote and local failures are returned and successful removals stay removed`() = runTest {
        val remaining = bindings.toMutableList()
        coEvery { (services[0] as DeletableTracker).delete(any()) } throws IllegalStateException("remote first")
        coEvery { (services[1] as DeletableTracker).delete(any()) } throws IllegalStateException("remote middle")
        coEvery { delete.awaitOrThrow(10, any()) } coAnswers {
            val id = secondArg<Long>()
            if (id == 2L) error("local middle")
            remaining.removeAll { it.trackerId == id }
        }
        val failures = subject.remove(10, setOf(1, 2, 3), true)
        failures.map { it.second.message } shouldBe listOf("remote first", "remote middle", "local middle")
        failures.map { it.first } shouldBe listOf(services[0], services[1], services[1])
        remaining.map { it.trackerId } shouldBe listOf(2L)
        coVerify { (services[2] as DeletableTracker).delete(bindings[2]) }
    }

    @Test
    fun `MangaUpdates list labels propagate equivalent statuses in both directions`() = runTest {
        val common = listOf(MR.strings.reading, MR.strings.completed, MR.strings.plan_to_read, MR.strings.dropped, MR.strings.on_hold)
        val lists = listOf(MR.strings.reading_list, MR.strings.complete_list, MR.strings.wish_list, MR.strings.unfinished_list, MR.strings.on_hold_list)
        every { services[0].getStatusList() } returns (1L..5L).toList()
        every { services[1].getStatusList() } returns (10L..14L).toList()
        common.forEachIndexed { index, label -> every { services[0].getStatus(index + 1L) } returns label }
        lists.forEachIndexed { index, label -> every { services[1].getStatus(index + 10L) } returns label }
        for (index in common.indices) {
            subject.await(10, UpdateTracks.Change.Status(1, index + 1L)) shouldBe emptyList()
            coVerify { services[1].update(match { it.status == index + 10L }, false) }
            subject.await(10, UpdateTracks.Change.Status(2, index + 10L)) shouldBe emptyList()
            coVerify { services[0].update(match { it.status == index + 1L }, false) }
        }
    }
}
