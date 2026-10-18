package eu.kanade.domain.track.interactor

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.Tracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.track.interactor.UpsertTrack

class AddTracksTest {

    private val upsert = mockk<UpsertTrack>(relaxed = true)

    private val sync = mockk<SyncChapterProgressWithTrack> {
        coEvery {
            await(any(), any(), any())
        } returns null
    }

    private val chapters = mockk<GetChaptersByMangaId> {
        coEvery {
            await(1L)
        } returns emptyList()
    }

    private val service = mockk<Tracker>()

    private val subject = AddTracks(
        upsertTrack = upsert,
        syncChapterProgressWithTrack = sync,
        getChaptersByMangaId = chapters,
        trackerManager = mockk(),
        getHistory = mockk<GetHistory>(),
        context = mockk<Context>(),
    )

    private fun track() = Track.create(7L).apply {
        manga_id = 1L
        remote_id = 123L
        title = "Title"
        tracking_url = ""
    }

    @Test
    fun `persist returned binding only after successful remote initialization`() = runTest {
        val returned = track().apply {
            status = 1L
            remote_id = 456L
        }

        coEvery {
            service.bind(any(), false)
        } returns returned

        subject.bind(
            tracker = service,
            item = track(),
            mangaId = 1L,
        )

        coVerify(exactly = 1) {
            upsert.await(
                match {
                    it.status == 1L &&
                        it.remoteId == 456L &&
                        it.mangaId == 1L
                },
            )
        }

        coVerify(exactly = 1) {
            sync.await(
                1L,
                match {
                    it.remoteId == 456L &&
                        it.status == 1L
                },
                service,
            )
        }
    }

    @Test
    fun `remote bind failure cannot create a successful local binding`() = runTest {
        coEvery {
            service.bind(any(), false)
        } throws IllegalStateException("remote failed")

        assertThrows<IllegalStateException> {
            subject.bind(
                tracker = service,
                item = track(),
                mangaId = 1L,
            )
        }

        coVerify(exactly = 0) {
            upsert.await(any())
        }

        coVerify(exactly = 0) {
            sync.await(any(), any(), any())
        }
    }
}
