package tachiyomi.domain.track.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.track.service.TrackPersistenceListener

// KNS -->
/** Acceptance scenario 16: Smart Category automation only follows a persisted tracker change. */
class UpsertTrackTest {

    private val repository: TrackRepository = mockk(relaxed = true)
    private val listener: TrackPersistenceListener = mockk(relaxed = true)
    private val upsertTrack = UpsertTrack(repository, listener)

    @Test
    fun `the listener runs after a successful write`() {
        runBlocking {
            upsertTrack.await(track(mangaId = 7))
            upsertTrack.awaitAll(listOf(track(mangaId = 8), track(mangaId = 9), track(mangaId = 8)))
        }
        coVerify(exactly = 1) { listener.onTracksPersisted(setOf(7L)) }
        coVerify(exactly = 1) { listener.onTracksPersisted(setOf(8L, 9L)) }
    }

    @Test
    fun `a failed write never reaches the listener`() {
        coEvery { repository.upsert(any()) } throws IllegalStateException("db")
        coEvery { repository.upsertAll(any()) } throws IllegalStateException("db")
        runBlocking {
            upsertTrack.await(track(mangaId = 7))
            upsertTrack.awaitAll(listOf(track(mangaId = 8)))
        }
        coVerify(exactly = 0) { listener.onTracksPersisted(any()) }
    }

    @Test
    fun `a failing listener does not make the write fail`() {
        coEvery { listener.onTracksPersisted(any()) } throws IllegalStateException("automation")
        runBlocking { upsertTrack.await(track(mangaId = 7)) }
        coVerify(exactly = 1) { repository.upsert(any()) }
    }

    private fun track(mangaId: Long) = Track(
        id = 0,
        mangaId = mangaId,
        trackerId = 1,
        remoteId = 1,
        libraryId = null,
        title = "",
        lastChapterRead = 1.0,
        totalChapters = 0,
        status = 1,
        score = 0.0,
        remoteUrl = "",
        startDate = null,
        finishDate = null,
        private = false,
    )
}
// KNS <--
