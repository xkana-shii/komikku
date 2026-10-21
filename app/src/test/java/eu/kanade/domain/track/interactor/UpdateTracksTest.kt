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
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import kotlin.time.Instant
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

// KNS -->
class UpdateTracksTest {
    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val upsert = mockk<UpsertTrack>(relaxed = true)
    private val delete = mockk<DeleteTrack>(relaxed = true)
    private val subject = UpdateTracks(getTracks, manager, upsert, delete)
    private val bindings = (1L..3L).map {
        Track(
            id = it, mangaId = 10, trackerId = it, remoteId = it, libraryId = null, title = "Title",
            lastChapterRead = 3.0, totalChapters = 100, status = 1, score = 0.0, remoteUrl = "",
            startDate = null, finishDate = null, private = false,
        )
    }
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
            coEvery { update(any(), any()) } answers { firstArg() }
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
    fun `every tracker's write starts before a slow first tracker finishes`() = runTest {
        val release = CompletableDeferred<Unit>()
        val started = mutableListOf<Long>()
        services.forEachIndexed { index, service ->
            coEvery { service.update(any(), any()) } coAnswers {
                started += index + 1L
                if (index == 0) release.await()
                firstArg()
            }
        }
        val operation = async { subject.await(10, UpdateTracks.Change.Progress(8)) }
        testScheduler.runCurrent()
        started shouldBe listOf(1L, 2L, 3L)
        operation.isCompleted shouldBe false
        release.complete(Unit)
        operation.await().failures shouldBe emptyList()
    }

    @Test
    fun `progress reaches every tracker and the returned records are saved`() = runTest {
        services.forEach { service ->
            coEvery { service.update(any(), any()) } answers {
                firstArg<DbTrack>().toDomainTrack()!!.copy(startDate = Instant.fromEpochMilliseconds(500)).toDbTrack()
            }
        }
        subject.await(10, UpdateTracks.Change.Progress(8)).updatedTrackerIds shouldBe setOf(1L, 2L, 3L)
        services.forEachIndexed { index, service ->
            val trackerId = bindings[index].trackerId
            coVerify { service.update(match { it.last_chapter_read == 8.0 }, true) }
            coVerify {
                upsert.awaitOrThrow(
                    match { it.trackerId == trackerId && it.lastChapterRead == 8.0 && it.startDate != null },
                )
            }
        }
    }

    @Test
    fun `a score is converted to each tracker's own scale`() = runTest {
        val percent = services[1]
        every { percent.indexToScore(any()) } answers { firstArg<Int>() * 10.0 }
        every { percent.get10PointScore(any()) } answers { firstArg<Track>().score / 10.0 }
        subject.await(10, UpdateTracks.Change.Score(1, "7")).failures shouldBe emptyList()
        coVerify { services[0].update(match { it.score == 7.0 }, false) }
        coVerify { percent.update(match { it.score == 70.0 }, false) }
        coVerify { services[2].update(match { it.score == 7.0 }, false) }
    }

    @Test
    fun `the result separates updated, failed and skipped trackers`() = runTest {
        every { services[2].getScoreList() } returns emptyList<String>().toImmutableList()
        coEvery { services[1].update(any(), any()) } throws IllegalStateException("offline")

        val result = subject.await(10, UpdateTracks.Change.Score(1, "6"))

        result.updatedTrackerIds shouldBe setOf(1L)
        result.failedTrackerIds shouldBe setOf(2L)
        result.skippedTrackerIds shouldBe setOf(3L)
        result.failures.single().first shouldBe services[1]
        coVerify(exactly = 0) { services[2].refresh(any()) }
    }

    @Test
    fun `a failed save is reported for that tracker only`() = runTest {
        coEvery { upsert.awaitOrThrow(match { it.trackerId == 2L }) } throws IllegalStateException("disk full")
        val result = subject.await(10, UpdateTracks.Change.Progress(9))
        result.failedTrackerIds shouldBe setOf(2L)
        result.updatedTrackerIds shouldBe setOf(1L, 3L)
    }

    @Test
    fun `a completed status fills progress, and dates respect support and order`() = runTest {
        subject.await(10, UpdateTracks.Change.Status(1, 2)).failures shouldBe emptyList()
        services.forEach { service ->
            coVerify { service.update(match { it.status == 2L && it.last_chapter_read == 100.0 }, false) }
        }

        every { services[2].supportsReadingDates } returns false
        subject.await(10, UpdateTracks.Change.Date(start = true, millis = 500)).skippedTrackerIds shouldBe setOf(3L)
        services.take(2).forEach { service ->
            coVerify { service.update(match { it.started_reading_date == 500L }, false) }
        }

        services.forEach { service ->
            coEvery { service.refresh(any()) } answers {
                firstArg<DbTrack>().also { it.finished_reading_date = 400 }
            }
        }
        subject.await(10, UpdateTracks.Change.Date(start = true, millis = 500)).failedTrackerIds shouldBe setOf(1L, 2L)
    }

    @Test
    fun `a status without an equivalent is skipped`() = runTest {
        every { services[1].getStatus(2) } returns MR.strings.dropped
        every { services[1].getStatus(1) } returns MR.strings.on_hold
        val result = subject.await(10, UpdateTracks.Change.Status(1, 2))
        result.skippedTrackerIds shouldBe setOf(2L)
        coVerify(exactly = 0) { services[1].update(any(), any()) }
    }

    @Test
    fun `removing remotely deletes on the service and always unlinks locally`() = runTest {
        coEvery { (services[0] as DeletableTracker).delete(any()) } throws IllegalStateException("offline")
        val failures = subject.remove(10, setOf(1L, 2L), remotely = true)
        failures.single().first shouldBe services[0]
        coVerify { delete.await(10, 1) }
        coVerify { delete.await(10, 2) }
        coVerify(exactly = 0) { delete.await(10, 3) }
    }
}
// KNS <--
