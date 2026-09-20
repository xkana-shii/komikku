package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.Tracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.track.interactor.InsertTrack

class AddTracksTest {
    private val insert = mockk<InsertTrack>(relaxed = true)
    private val sync = mockk<SyncChapterProgressWithTrack> { coEvery { await(any(), any(), any()) } returns null }
    private val chapters = mockk<GetChaptersByMangaId> { coEvery { await(1) } returns emptyList() }
    private val service = mockk<Tracker>()
    private val subject = AddTracks(insert, sync, chapters, mockk())
    private fun track() = Track.create(7).apply {
        manga_id = 1
        remote_id = 123
        title = "Title"
        tracking_url = ""
    }

    @Test
    fun `persist returned binding only after successful remote initialization`() = runTest {
        val returned = track().apply { status = 1 }
        coEvery { service.bind(any(), false) } returns returned
        subject.bind(service, track(), 1)
        coVerify(exactly = 1) { insert.await(match { it.status == 1L && it.remoteId == 123L }) }
    }

    @Test
    fun `remote bind failure cannot create a successful local binding`() = runTest {
        coEvery { service.bind(any(), false) } throws IllegalStateException("remote failed")
        assertThrows<IllegalStateException> { subject.bind(service, track(), 1) }
        coVerify(exactly = 0) {
            insert.await(any())
            sync.await(any(), any(), any())
        }
    }
}
