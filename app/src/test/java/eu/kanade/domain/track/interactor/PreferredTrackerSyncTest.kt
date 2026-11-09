package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import kotlin.time.Instant

// KNS -->
class PreferredTrackerSyncTest {

    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val upsert = mockk<UpsertTrack>(relaxed = true)
    private val preferences = mockk<TrackPreferences>()
    private val delayed = mockk<DelayedTrackingStore> { every { hasPending(any()) } returns false }
    private val subject = PreferredTrackerSync(getTracks, manager, upsert, preferences, delayed)

    private val day = 86_400_000L
    private fun date(days: Long) = Instant.fromEpochMilliseconds(days * day + day / 2)

    /** A tracker with its own score scale: [step] 1 is a 10-point scale, 10 a 100-point one. */
    private fun tracker(
        id: Long,
        step: Int = 1,
        dates: Boolean = true,
        statuses: Map<Long, dev.icerock.moko.resources.StringResource> = mapOf(
            1L to MR.strings.reading,
            2L to MR.strings.completed,
            3L to MR.strings.on_hold,
        ),
    ): BaseTracker = mockk {
        every { this@mockk.id } returns id
        every { isLoggedIn } returns true
        every { supportsReadingDates } returns dates
        every { getStatusList() } returns statuses.keys.toList()
        every { getStatus(any()) } answers { statuses[firstArg()] }
        every { getCompletionStatus() } returns 2
        every { getScoreList() } returns (0..(10 * step) step step).map(Int::toString).toImmutableList()
        every { indexToScore(any()) } answers { firstArg<Int>().toDouble() * step }
        every { get10PointScore(any()) } answers { firstArg<Track>().score / step }
        coEvery { refresh(any()) } answers { firstArg() }
        coEvery { update(any(), any()) } answers { firstArg() }
    }

    private fun track(
        trackerId: Long,
        progress: Double = 5.0,
        status: Long = 1,
        score: Double = 0.0,
        start: Instant? = null,
        finish: Instant? = null,
    ) = Track(
        id = trackerId, mangaId = 10, trackerId = trackerId, remoteId = trackerId, libraryId = null, title = "T",
        lastChapterRead = progress, totalChapters = 29, status = status, score = score, remoteUrl = "",
        startDate = start, finishDate = finish, private = false,
    )

    private fun link(preferred: Long?, vararg pairs: Pair<BaseTracker, Track>) {
        coEvery { getTracks.await(10) } returns pairs.map { it.second }
        pairs.forEach { (service, track) -> every { manager.get(track.trackerId) } returns service }
        every { preferences.resolvePreferredTracker(10, any()) } answers {
            preferred?.takeIf { it in secondArg<Set<Long>>() }
        }
    }

    @Test
    fun `the preferred score and chapters reach the others and the preferred tracker is left alone`() = runTest {
        val mangaBaka = tracker(1, step = 10)
        val aniList = tracker(2)
        val mal = tracker(3)
        link(
            1,
            mangaBaka to track(1, progress = 29.0, score = 85.0),
            aniList to track(2, progress = 29.0, score = 7.0),
            mal to track(3, progress = 28.0, score = 8.0),
        )

        val outcome = subject.run(10)!!

        outcome.preferredTrackerId shouldBe 1
        outcome.written shouldBe setOf(2L, 3L)
        coVerify { aniList.update(match { it.score == 8.0 && it.last_chapter_read == 29.0 }, false) }
        coVerify { mal.update(match { it.score == 8.0 && it.last_chapter_read == 29.0 }, false) }
        coVerify(exactly = 0) { mangaBaka.update(any(), any()) }
        coVerify(exactly = 0) { mangaBaka.refresh(any()) }
    }

    @Test
    fun `a manga that is already in step costs no request`() = runTest {
        val one = tracker(1)
        val two = tracker(2, step = 10)
        link(1, one to track(1, score = 8.0), two to track(2, score = 80.0))

        val outcome = subject.run(10)!!

        outcome.written shouldBe emptySet()
        outcome.unchanged shouldBe setOf(2L)
        coVerify(exactly = 0) { two.refresh(any()) }
        coVerify(exactly = 0) { two.update(any(), any()) }
    }

    @Test
    fun `a destination is refreshed before it is written so its other fields are kept`() = runTest {
        val one = tracker(1)
        val two = tracker(2)
        coEvery { two.refresh(any()) } returns track(2, progress = 5.0, score = 0.0, status = 1)
            .copy(private = true, totalChapters = 99).toDbTrack()
        link(1, one to track(1, progress = 9.0), two to track(2, progress = 5.0))

        subject.run(10)

        coVerify { two.update(match { it.last_chapter_read == 9.0 && it.private && it.total_chapters == 99L }, false) }
    }

    @Test
    fun `nothing is propagated without two linked trackers or a preferred one`() = runTest {
        val one = tracker(1)
        val two = tracker(2)
        link(1, one to track(1))
        subject.run(10) shouldBe null

        link(null, one to track(1), two to track(2, progress = 9.0))
        subject.run(10) shouldBe null

        coEvery { getTracks.await(10) } returns emptyList()
        subject.run(10) shouldBe null
        coVerify(exactly = 0) { two.update(any(), any()) }
    }

    @Test
    fun `an unreliable preferred tracker pauses the sync`() = runTest {
        val one = tracker(1)
        val two = tracker(2)
        link(1, one to track(1, progress = 1.0), two to track(2, progress = 9.0))

        subject.run(10, unreliableTrackerIds = setOf(1L)) shouldBe null

        coVerify(exactly = 0) { two.update(any(), any()) }
    }

    @Test
    fun `one failing destination is reported and the others are still written`() = runTest {
        val one = tracker(1)
        val two = tracker(2)
        val three = tracker(3)
        coEvery { two.update(any(), any()) } throws IllegalStateException("401")
        link(1, one to track(1, progress = 9.0), two to track(2), three to track(3))

        val outcome = subject.run(10)!!

        outcome.written shouldBe setOf(3L)
        outcome.failures.map { it.first.id to it.second.message } shouldBe listOf(2L to "401")
        outcome.toResult().failedTrackerIds shouldBe setOf(2L)
        outcome.toResult().updatedTrackerIds shouldBe setOf(3L)
        coVerify(exactly = 0) { one.update(any(), any()) }
    }

    @Test
    fun `progress is not lowered while an update is still queued for a tracker`() = runTest {
        val one = tracker(1)
        val two = tracker(2)
        link(1, one to track(1, progress = 3.0), two to track(2, progress = 9.0))

        every { delayed.hasPending(any()) } returns true
        subject.run(10)!!.written shouldBe emptySet()
        coVerify(exactly = 0) { two.update(any(), any()) }

        // with nothing queued the preferred tracker is authoritative, a deliberate step back included
        every { delayed.hasPending(any()) } returns false
        subject.run(10)!!.written shouldBe setOf(2L)
        coVerify { two.update(match { it.last_chapter_read == 3.0 }, false) }
    }

    @Test
    fun `a caught up releasing series is never completed by the sync`() = runTest {
        val preferred = tracker(1)
        val other = tracker(2)
        // reading all 29 chapters of a series that is still releasing: the preferred tracker stays on Reading
        link(1, preferred to track(1, progress = 29.0, status = 1), other to track(2, progress = 28.0, status = 1))

        subject.run(10)

        coVerify { other.update(match { it.status == 1L && it.last_chapter_read == 29.0 }, false) }
        coVerify(exactly = 0) { other.update(match { it.status == 2L }, any()) }
    }

    @Test
    fun `a status the destination has no equivalent for is left alone`() = runTest {
        val preferred = tracker(1)
        val other = tracker(2, statuses = mapOf(1L to MR.strings.reading, 2L to MR.strings.completed))
        link(1, preferred to track(1, status = 3), other to track(2, status = 1))

        subject.run(10)!!.written shouldBe emptySet()

        coVerify(exactly = 0) { other.update(any(), any()) }
    }

    @Test
    fun `a tracker without reading dates keeps its own dates`() = runTest {
        val preferred = tracker(1)
        val other = tracker(2, dates = false)
        link(1, preferred to track(1, start = date(10)), other to track(2))

        subject.run(10)!!.written shouldBe emptySet()
    }

    @Test
    fun `dates follow the preferred tracker, a removal included, and keep their day`() = runTest {
        val preferred = tracker(1)
        val other = tracker(2)
        link(1, preferred to track(1, start = date(10), finish = null), other to track(2, start = date(3), finish = date(20)))

        subject.run(10)

        coVerify { other.update(match { it.started_reading_date == date(10).toEpochMilliseconds() && it.finished_reading_date == 0L }, false) }
    }

    @Test
    fun `a date with another time of the same day is not a difference`() = runTest {
        val preferred = tracker(1)
        val other = tracker(2)
        val sameDay = Instant.fromEpochMilliseconds(date(10).toEpochMilliseconds() + 60_000)
        link(1, preferred to track(1, start = date(10)), other to track(2, start = sameDay))

        subject.run(10)!!.written shouldBe emptySet()
    }

    @Test
    fun `a score converges and is not written again`() = runTest {
        val ten = tracker(1)
        val hundred = tracker(2, step = 10)
        val source = track(1, score = 7.0)
        val destination = track(2, score = 0.0)

        val first = PreferredTrackerReconciler.reconcile(ten, source, hundred, destination, true)!!
        first.score shouldBe 70.0
        PreferredTrackerReconciler.reconcile(ten, source, hundred, first, true) shouldBe null
        // and the other way round, a rounded 100-point score does not differ from its 10-point equivalent
        val back = PreferredTrackerReconciler.reconcile(hundred, track(2, score = 73.0), ten, source.copy(score = 7.0), true)
        back shouldBe null
    }

    @Test
    fun `an unscored preferred entry does not erase a score`() = runTest {
        val preferred = tracker(1)
        val other = tracker(2)
        PreferredTrackerReconciler.reconcile(preferred, track(1, score = 0.0), other, track(2, score = 6.0), true) shouldBe null
    }

    @Test
    fun `chapters ahead of the preferred tracker are only lowered when that is allowed`() = runTest {
        val preferred = tracker(1)
        val other = tracker(2)
        val source = track(1, progress = 3.0)
        val destination = track(2, progress = 9.0)

        PreferredTrackerReconciler.reconcile(preferred, source, other, destination, allowProgressDecrease = false) shouldBe null
        PreferredTrackerReconciler.reconcile(preferred, source, other, destination, allowProgressDecrease = true)!!
            .lastChapterRead shouldBe 3.0
    }

    @Test
    fun `rereading translates only to a tracker that has the status`() = runTest {
        val rereading = MR.strings.repeating
        val preferred = tracker(1, statuses = mapOf(1L to MR.strings.reading, 6L to rereading))
        val without = tracker(2)
        val with = tracker(3, statuses = mapOf(1L to MR.strings.reading, 7L to rereading))

        PreferredTrackerReconciler.reconcile(preferred, track(1, status = 6), without, track(2, status = 1), true) shouldBe null
        PreferredTrackerReconciler.reconcile(preferred, track(1, status = 6), with, track(3, status = 1), true)!!.status shouldBe 7L
    }
}
// KNS <--
