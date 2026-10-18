package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import kotlin.time.Instant
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

class SyncChapterProgressWithTrackTest {

    private val getChapters = mockk<GetChaptersByMangaId>()
    private val updateChapter = mockk<UpdateChapter>(relaxed = true)
    private val upsert = mockk<UpsertTrack>(relaxed = true)
    private val tracker = mockk<BaseTracker>()

    private val subject = SyncChapterProgressWithTrack(
        updateChapter = updateChapter,
        upsertTrack = upsert,
        getChaptersByMangaId = getChapters,
    )

    private val remote = Track(
        id = 1L,
        mangaId = 10L,
        trackerId = 1L,
        remoteId = 1L,
        libraryId = null,
        title = "Title",
        lastChapterRead = 2.0,
        totalChapters = 5L,
        status = 1L,
        score = 0.0,
        remoteUrl = "",
        startDate = Instant.fromEpochMilliseconds(10),
        finishDate = null,
        private = false,
    )

    @Test
    fun `local progress upload persists returned dates and status`() = runTest {
        coEvery {
            getChapters.await(10L)
        } returns listOf(
            Chapter.create().copy(
                id = 1L,
                mangaId = 10L,
                read = true,
                chapterNumber = 5.0,
            ),
        )

        coEvery {
            tracker.update(any(), false)
        } answers {
            firstArg<DbTrack>().apply {
                status = 2L
                finished_reading_date = 99L
            }
        }

        subject.sync(
            mangaId = 10L,
            remoteTrack = remote,
            tracker = tracker,
        )

        coVerify(exactly = 1) {
            tracker.update(
                match {
                    it.last_chapter_read == 5.0 &&
                        it.started_reading_date == 10L
                },
                false,
            )
        }

        coVerify(exactly = 1) {
            upsert.await(
                match {
                    it.lastChapterRead == 5.0 &&
                        it.status == 2L &&
                        it.startDate ==
                        Instant.fromEpochMilliseconds(10) &&
                        it.finishDate ==
                        Instant.fromEpochMilliseconds(99)
                },
            )
        }
    }

    @Test
    fun `remote progress marks eligible local chapters as read`() = runTest {
        coEvery {
            getChapters.await(10L)
        } returns listOf(
            Chapter.create().copy(
                id = 1L,
                mangaId = 10L,
                sourceOrder = 2L,
                chapterNumber = 1.0,
            ),
            Chapter.create().copy(
                id = 2L,
                mangaId = 10L,
                sourceOrder = 1L,
                chapterNumber = 2.0,
            ),
        )

        every {
            tracker.hasNotStartedReading(1L)
        } returns false

        subject.sync(
            mangaId = 10L,
            remoteTrack = remote,
            tracker = tracker,
        ) shouldBe 2

        coVerify(exactly = 1) {
            updateChapter.awaitAll(
                match {
                    it.size == 2 &&
                        it.map { update -> update.id } ==
                        listOf(1L, 2L)
                },
            )
        }

        coVerify(exactly = 0) {
            upsert.await(any())
        }
    }

    @Test
    fun `wishlist tracker does not mark local chapters as read`() = runTest {
        coEvery {
            getChapters.await(10L)
        } returns listOf(
            Chapter.create().copy(
                id = 1L,
                mangaId = 10L,
                chapterNumber = 1.0,
            ),
        )

        every {
            tracker.hasNotStartedReading(1L)
        } returns true

        subject.sync(
            mangaId = 10L,
            remoteTrack = remote,
            tracker = tracker,
        ) shouldBe null

        coVerify(exactly = 0) {
            updateChapter.awaitAll(any())
        }
    }

    @Test
    fun `non enhanced tracker is excluded from enhanced-only synchronization`() = runTest {
        subject.await(
            mangaId = 10L,
            remoteTrack = remote,
            tracker = tracker,
        ) shouldBe null

        coVerify(exactly = 0) {
            getChapters.await(any())
        }
    }
}
