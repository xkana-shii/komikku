package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track

class SyncChapterProgressWithTrackTest {
    @Test
    fun `local progress upload persists remote returned dates and status`() = runTest {
        val getChapters = mockk<GetChaptersByMangaId>()
        val updateChapter = mockk<UpdateChapter>(relaxed = true)
        val insert = mockk<InsertTrack>(relaxed = true)
        val tracker = mockk<BaseTracker>()
        val remote = Track(1, 10, 1, 1, null, "Title", 2.0, 5, 1, 0.0, "", 10, 0, false)
        coEvery { getChapters.await(10) } returns listOf(Chapter.create().copy(id = 1, mangaId = 10, read = true, chapterNumber = 5.0))
        coEvery { tracker.update(any(), false) } returns remote.copy(lastChapterRead = 5.0, status = 2, finishDate = 99).toDbTrack()
        SyncChapterProgressWithTrack(updateChapter, insert, getChapters).sync(10, remote, tracker, propagateErrors = true)
        coVerify { tracker.update(match { it.last_chapter_read == 5.0 }, false) }
        coVerify { insert.await(match { it.lastChapterRead == 5.0 && it.status == 2L && it.finishDate == 99L && it.startDate == 10L }) }
    }
}
