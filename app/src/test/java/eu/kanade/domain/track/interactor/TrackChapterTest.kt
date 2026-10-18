package eu.kanade.domain.track.interactor

import android.content.Context
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import kotlin.time.Instant
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

class TrackChapterTest {

    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val upsert = mockk<UpsertTrack>(relaxed = true)
    private val delayed = mockk<DelayedTrackingStore>(relaxed = true)
    private val service = mockk<BaseTracker>()
    private val context = mockk<Context>()

    private val subject = TrackChapter(
        getTracks = getTracks,
        trackerManager = manager,
        upsertTrack = upsert,
        delayedTrackingStore = delayed,
    )

    private val cached = Track(
        id = 1L,
        mangaId = 2L,
        trackerId = 3L,
        remoteId = 4L,
        libraryId = null,
        title = "Title",
        lastChapterRead = 1.0,
        totalChapters = 100L,
        status = 1L,
        score = 0.0,
        remoteUrl = "",
        startDate = Instant.fromEpochMilliseconds(10),
        finishDate = Instant.fromEpochMilliseconds(20),
        private = false,
    )

    init {
        coEvery { getTracks.await(2L) } returns listOf(cached)
        every { manager.get(3L) } returns service
        every { service.isLoggedIn } returns true
    }

    @Test
    fun `remote progress already ahead is not rolled back and returned dates persist`() = runTest {
        coEvery {
            service.refresh(any())
        } returns cached.copy(
            lastChapterRead = 12.0,
        ).toDbTrack()

        coEvery {
            service.update(any(), true)
        } answers {
            firstArg<DbTrack>().apply {
                finished_reading_date = 99L
            }
        }

        subject.await(
            context = context,
            mangaId = 2L,
            chapterNumber = 5.0,
            setupJobOnFailure = false,
        )

        coVerify(exactly = 1) {
            service.update(
                match { it.last_chapter_read == 12.0 },
                true,
            )
        }

        coVerify(exactly = 1) {
            upsert.await(
                match {
                    it.lastChapterRead == 12.0 &&
                        it.finishDate ==
                        Instant.fromEpochMilliseconds(99) &&
                        it.startDate ==
                        Instant.fromEpochMilliseconds(10)
                },
            )
        }

        verify(exactly = 1) {
            delayed.remove(1L)
        }
    }

    @Test
    fun `rereading status and start date survive chapter advancement`() = runTest {
        coEvery {
            service.refresh(any())
        } returns cached.copy(
            status = 6L,
            lastChapterRead = 2.0,
        ).toDbTrack()

        coEvery {
            service.update(any(), true)
        } answers {
            firstArg()
        }

        subject.await(
            context = context,
            mangaId = 2L,
            chapterNumber = 3.0,
            setupJobOnFailure = false,
        )

        coVerify {
            service.update(
                match {
                    it.status == 6L &&
                        it.last_chapter_read == 3.0 &&
                        it.started_reading_date == 10L
                },
                true,
            )
        }

        coVerify {
            upsert.await(
                match {
                    it.status == 6L &&
                        it.lastChapterRead == 3.0
                },
            )
        }
    }

    @Test
    fun `cached progress and invalid chapter numbers skip network`() = runTest {
        subject.await(
            context,
            2L,
            1.0,
            setupJobOnFailure = false,
        )

        subject.await(
            context,
            2L,
            Double.NaN,
            setupJobOnFailure = false,
        )

        subject.await(
            context,
            2L,
            Double.POSITIVE_INFINITY,
            setupJobOnFailure = false,
        )

        coVerify(exactly = 0) {
            service.refresh(any())
        }

        cached.startDate shouldBe Instant.fromEpochMilliseconds(10)
    }

    @Test
    fun `failed remote update is queued without persisting success`() = runTest {
        coEvery {
            service.refresh(any())
        } returns cached.toDbTrack()

        coEvery {
            service.update(any(), true)
        } throws IllegalStateException("Offline")

        subject.await(
            context = context,
            mangaId = 2L,
            chapterNumber = 5.0,
            setupJobOnFailure = false,
        )

        verify(exactly = 1) {
            delayed.add(1L, 5.0)
        }

        coVerify(exactly = 0) {
            upsert.await(any())
        }
    }
}
