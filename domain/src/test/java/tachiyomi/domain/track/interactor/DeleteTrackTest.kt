package tachiyomi.domain.track.interactor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.repository.TrackRepository

class DeleteTrackTest {

    private val repository = mockk<TrackRepository>()
    private val subject = DeleteTrack(repository)

    @Test
    fun `successful deletion delegates to repository exactly once`() = runTest {
        coEvery {
            repository.delete(1L, 7L)
        } returns Unit

        subject.await(1L, 7L)

        coVerify(exactly = 1) {
            repository.delete(1L, 7L)
        }
    }

    @Test
    fun `repository exception is handled by the interactor`() = runTest {
        coEvery {
            repository.delete(1L, 7L)
        } throws IllegalStateException("database unavailable")

        // The current interactor logs repository exceptions
        // rather than propagating them.
        subject.await(1L, 7L)

        coVerify(exactly = 1) {
            repository.delete(1L, 7L)
        }
    }
}
