package eu.kanade.domain.track.interactor

import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.track.model.AutoRereadResetMode
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track

// KNS -->
class StartRereadTest {
    private val chapters = (1L..4L).map { Chapter.create().copy(id = it, chapterNumber = it.toDouble(), read = it != 4L) }

    @Test
    fun `reset to zero unreads every read chapter`() {
        StartReread.chaptersToReset(chapters, currentChapterId = 2, mode = AutoRereadResetMode.RESET_TO_ZERO)
            .map { it.id } shouldBe listOf(1L, 2L, 3L)
        StartReread.rereadProgress(chapters[1], AutoRereadResetMode.RESET_TO_ZERO) shouldBe 0.0
    }

    @Test
    fun `reset to the current chapter unreads only the read chapters after it`() {
        StartReread.chaptersToReset(chapters, currentChapterId = 2, mode = AutoRereadResetMode.RESET_TO_CURRENT_CHAPTER)
            .map { it.id } shouldBe listOf(3L)
        StartReread.chaptersToReset(chapters, currentChapterId = 99, mode = AutoRereadResetMode.RESET_TO_CURRENT_CHAPTER) shouldBe
            emptyList()
        StartReread.rereadProgress(chapters[1], AutoRereadResetMode.RESET_TO_CURRENT_CHAPTER) shouldBe 2.0
        StartReread.rereadProgress(chapters[1].copy(chapterNumber = -1.0), AutoRereadResetMode.RESET_TO_CURRENT_CHAPTER) shouldBe
            0.0
    }

    @Test
    fun `only completed trackers with a rereading status are moved to rereading`() = runTest {
        val getTracks = mockk<GetTracks>()
        val manager = mockk<TrackerManager>()
        val upsert = mockk<UpsertTrack>(relaxed = true)
        val setReadStatus = mockk<SetReadStatus>(relaxed = true)
        val mal = tracker(id = 1, completed = 2, rereading = 6)
        val kitsu = tracker(id = 2, completed = 2, rereading = -1)
        val anilist = tracker(id = 3, completed = 2, rereading = 6)
        every { manager.get(1) } returns mal
        every { manager.get(2) } returns kitsu
        every { manager.get(3) } returns anilist
        coEvery { getTracks.await(10) } returns listOf(
            track(trackerId = 1, status = 2),
            track(trackerId = 2, status = 2),
            track(trackerId = 3, status = 1),
        )

        val subject = StartReread(getTracks, manager, upsert, setReadStatus)
        subject.candidates(10).map { it.first } shouldBe listOf(mal)

        subject.await(10, chapters, currentChapter = chapters[1], mode = AutoRereadResetMode.RESET_TO_CURRENT_CHAPTER) shouldBe
            emptyList()
        coVerify { mal.update(match { it.status == 6L && it.last_chapter_read == 2.0 }, false) }
        coVerify(exactly = 0) { kitsu.update(any(), any()) }
        coVerify(exactly = 0) { anilist.update(any(), any()) }
        coVerify { upsert.awaitOrThrow(match { it.trackerId == 1L && it.status == 6L && it.startDate != null }) }
    }

    private fun tracker(id: Long, completed: Long, rereading: Long) = mockk<BaseTracker> {
        every { this@mockk.id } returns id
        every { isLoggedIn } returns true
        every { getCompletionStatus() } returns completed
        every { getRereadingStatus() } returns rereading
        coEvery { refresh(any()) } answers { firstArg() }
        coEvery { update(any(), any()) } answers { firstArg() }
    }

    private fun track(trackerId: Long, status: Long) = Track(
        id = trackerId, mangaId = 10, trackerId = trackerId, remoteId = trackerId, libraryId = null, title = "T",
        lastChapterRead = 4.0, totalChapters = 4, status = status, score = 0.0, remoteUrl = "",
        startDate = null, finishDate = null, private = false,
    )
}
// KNS <--
