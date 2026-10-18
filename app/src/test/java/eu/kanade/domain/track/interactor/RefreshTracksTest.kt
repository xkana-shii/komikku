
package eu.kanade.domain.track.interactor

import android.content.Context
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.Tracker
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
import kotlin.time.Instant
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

class RefreshTracksTest {

    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val upsert = mockk<UpsertTrack>(relaxed = true)
    private val local = mockk<SyncChapterProgressWithTrack>()
    private val context = mockk<Context>()

    private val one = tracker(1L)
    private val two = tracker(2L)

    private val subject = RefreshTracks(
        getTracks = getTracks,
        trackerManager = manager,
        upsertTrack = upsert,
        syncChapterProgressWithTrack = local,
        context = context,
    )

    private fun track(
        trackerId: Long,
        progress: Double,
        status: Long = 1L,
    ) = Track(
        id = trackerId,
        mangaId = 10L,
        trackerId = trackerId,
        remoteId = trackerId,
        libraryId = null,
        title = "Title",
        lastChapterRead = progress,
        totalChapters = 100L,
        status = status,
        score = 0.0,
        remoteUrl = "",
        startDate = null,
        finishDate = null,
        private = false,
    )

    private fun tracker(id: Long) = mockk<Tracker> {
        every { this@mockk.id } returns id
        every { isLoggedIn } returns true
        every { getReadingStatus() } returns 1L
        every { getCompletionStatus() } returns 2L
        every { getRereadingStatus() } returns 6L

        coEvery {
            refresh(any())
        } answers {
            firstArg()
        }

        coEvery {
            update(any(), any())
        } answers {
            firstArg()
        }
    }

    init {
        every { manager.get(1L) } returns one
        every { manager.get(2L) } returns two

        coEvery {
            getTracks.await(10L)
        } returns listOf(
            track(1L, 3.0),
            track(2L, 8.0),
        )

        coEvery {
            local.await(any(), any(), any())
        } returns null

        coEvery {
            local.sync(any(), any(), any())
        } returns null
    }

    @Test
    fun `refresh persists every successful tracker`() = runTest {
        subject.await(10L) shouldBe emptyList()

        coVerify(exactly = 2) {
            upsert.await(any())
        }

        coVerify(exactly = 2) {
            local.await(any(), any(), any())
        }
    }

    @Test
    fun `full synchronization uses the unrestricted sync path`() = runTest {
        subject.await(
            mangaId = 10L,
            enhancedTrackersOnly = false,
        ) shouldBe emptyList()

        coVerify(exactly = 2) {
            local.sync(any(), any(), any())
        }

        coVerify(exactly = 0) {
            local.await(any(), any(), any())
        }
    }

    @Test
    fun `refresh failure does not prevent another tracker from persisting`() = runTest {
        coEvery {
            one.refresh(any())
        } throws IllegalStateException("Offline")

        val failures = subject.await(10L)

        failures.size shouldBe 1
        failures.single().first shouldBe one

        coVerify(exactly = 1) {
            upsert.await(
                match {
                    it.trackerId == 2L
                },
            )
        }

        coVerify(exactly = 0) {
            upsert.await(
                match {
                    it.trackerId == 1L
                },
            )
        }
    }

    @Test
    fun `plus updates all bound trackers to one shared progress value`() = runTest {
        subject.adjustProgress(
            mangaId = 10L,
            delta = 1,
        ) shouldBe emptyList()

        coVerify(exactly = 1) {
            one.update(
                match {
                    it.last_chapter_read == 9.0
                },
                true,
            )
        }

        coVerify(exactly = 1) {
            two.update(
                match {
                    it.last_chapter_read == 9.0
                },
                true,
            )
        }

        coVerify(exactly = 2) {
            upsert.await(
                match {
                    it.lastChapterRead == 9.0
                },
            )
        }
    }

    @Test
    fun `minus updates all trackers and clamps progress to zero`() = runTest {
        subject.adjustProgress(
            mangaId = 10L,
            delta = -1,
        ) shouldBe emptyList()

        coVerify {
            one.update(
                match {
                    it.last_chapter_read == 7.0
                },
                true,
            )
        }

        coVerify {
            two.update(
                match {
                    it.last_chapter_read == 7.0
                },
                true,
            )
        }

        coEvery {
            getTracks.await(10L)
        } returns listOf(
            track(1L, 0.0),
            track(2L, 0.0),
        )

        subject.adjustProgress(
            mangaId = 10L,
            delta = -1,
        ) shouldBe emptyList()

        coVerify {
            one.update(
                match {
                    it.last_chapter_read == 0.0
                },
                true,
            )
        }

        coVerify {
            two.update(
                match {
                    it.last_chapter_read == 0.0
                },
                true,
            )
        }
    }

    @Test
    fun `failed service does not prevent successful sibling progress`() = runTest {
        coEvery {
            one.update(any(), true)
        } throws IllegalStateException("Remote failed")

        val failures = subject.adjustProgress(
            mangaId = 10L,
            delta = 1,
        )

        failures.size shouldBe 1
        failures.single().first shouldBe one

        coVerify(exactly = 1) {
            upsert.await(
                match {
                    it.trackerId == 2L &&
                        it.lastChapterRead == 9.0
                },
            )
        }

        coVerify(exactly = 0) {
            upsert.await(
                match {
                    it.trackerId == 1L
                },
            )
        }
    }

    @Test
    fun `failed refresh does not propagate stale tracker progress`() = runTest {
        coEvery {
            one.refresh(any())
        } throws IllegalStateException("Refresh failed")

        val failures = subject.adjustProgress(
            mangaId = 10L,
            delta = 1,
        )

        failures.size shouldBe 1
        failures.single().first shouldBe one

        coVerify(exactly = 0) {
            one.update(any(), any())
        }

        coVerify(exactly = 1) {
            two.update(
                match {
                    it.last_chapter_read == 9.0
                },
                true,
            )
        }
    }

    @Test
    fun `remote returned status and reading dates are persisted`() = runTest {
        coEvery {
            one.update(any(), true)
        } answers {
            firstArg<DbTrack>().apply {
                status = 1L
                started_reading_date = 50L
                finished_reading_date = 99L
            }
        }

        subject.adjustProgress(
            mangaId = 10L,
            delta = 1,
        ) shouldBe emptyList()

        coVerify(exactly = 1) {
            upsert.await(
                match {
                    it.trackerId == 1L &&
                        it.lastChapterRead == 9.0 &&
                        it.startDate ==
                        Instant.fromEpochMilliseconds(50) &&
                        it.finishDate ==
                        Instant.fromEpochMilliseconds(99)
                },
            )
        }
    }

    @Test
    fun `rereading progress cannot be fast forwarded by a completed tracker`() = runTest {
        coEvery {
            getTracks.await(10L)
        } returns listOf(
            track(1L, 3.0, status = 6L),
            track(2L, 100.0, status = 2L),
        )

        subject.adjustProgress(
            mangaId = 10L,
            delta = 1,
        ) shouldBe emptyList()

        coVerify(exactly = 1) {
            one.update(
                match {
                    it.last_chapter_read == 4.0 &&
                        it.status == 6L
                },
                true,
            )
        }

        coVerify(exactly = 0) {
            two.update(any(), any())
        }
    }

    @Test
    fun `successive operations use newly persisted progress`() = runTest {
        val persisted = mutableMapOf(
            1L to track(1L, 8.0),
            2L to track(2L, 8.0),
        )

        coEvery {
            getTracks.await(10L)
        } answers {
            persisted.values.toList()
        }

        coEvery {
            upsert.await(any())
        } answers {
            val written = firstArg<Track>()
            persisted[written.trackerId] = written
        }

        subject.adjustProgress(10L, 1) shouldBe emptyList()
        subject.adjustProgress(10L, 1) shouldBe emptyList()
        subject.adjustProgress(10L, -1) shouldBe emptyList()

        persisted.values.map {
            it.lastChapterRead
        } shouldBe listOf(9.0, 9.0)
    }
}
