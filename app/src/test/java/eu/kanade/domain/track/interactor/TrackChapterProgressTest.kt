package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
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

// KNS -->
class TrackChapterProgressTest {
    private val getTracks = mockk<GetTracks>()
    private val manager = mockk<TrackerManager>()
    private val upsert = mockk<UpsertTrack>(relaxed = true)
    private val delayed = mockk<DelayedTrackingStore>(relaxed = true)
    private val service = mockk<BaseTracker>()
    private val subject = TrackChapter(getTracks, manager, upsert, delayed)
    private val cached = Track(
        id = 1, mangaId = 2, trackerId = 3, remoteId = 4, libraryId = null, title = "Title",
        lastChapterRead = 1.0, totalChapters = 100, status = 1, score = 0.0, remoteUrl = "",
        startDate = null, finishDate = null, private = false,
    )

    init {
        coEvery { getTracks.await(2) } returns listOf(cached)
        every { manager.get(3) } returns service
        every { service.isLoggedIn } returns true
    }

    @Test
    fun `progress already further ahead on the service is not rolled back`() = runTest {
        coEvery { service.refresh(any()) } returns cached.copy(lastChapterRead = 12.0).toDbTrack()
        coEvery { service.update(any(), true) } answers {
            firstArg<DbTrack>().apply { finished_reading_date = 99 }
        }

        subject.await(mockk(), mangaId = 2, chapterNumber = 5.0, setupJobOnFailure = false)

        coVerify { service.update(match { it.last_chapter_read == 12.0 }, true) }
        coVerify {
            upsert.await(
                match { it.lastChapterRead == 12.0 && it.finishDate == Instant.fromEpochMilliseconds(99) },
            )
        }
    }

    @Test
    fun `a newer chapter advances the service`() = runTest {
        coEvery { service.refresh(any()) } returns cached.toDbTrack()
        coEvery { service.update(any(), true) } answers { firstArg() }

        subject.await(mockk(), mangaId = 2, chapterNumber = 5.0, setupJobOnFailure = false)

        coVerify { upsert.await(match { it.lastChapterRead == 5.0 }) }
    }

    @Test
    fun `invalid chapter numbers never reach a service`() = runTest {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -2.0).forEach { number ->
            subject.await(mockk(), mangaId = 2, chapterNumber = number, setupJobOnFailure = false)
        }
        coVerify(exactly = 0) { service.refresh(any()) }
        coVerify(exactly = 0) { service.update(any(), any()) }
    }
}
// KNS <--
