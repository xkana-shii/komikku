package tachiyomi.domain.track.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.track.repository.TrackRepository

class DeleteTrackTest {
    private val repository = mockk<TrackRepository>()
    private val subject = DeleteTrack(repository)

    @Test
    fun `reporting deletion propagates repository failure and cancellation`() = runTest {
        val failure = IllegalStateException("database unavailable")
        coEvery { repository.delete(1, 7) } throws failure
        assertThrows<IllegalStateException> { subject.awaitOrThrow(1, 7) } shouldBe failure
        coEvery { repository.delete(1, 7) } throws CancellationException()
        assertThrows<CancellationException> { subject.awaitOrThrow(1, 7) }
    }

    @Test
    fun `reporting deletion returns only after repository success`() = runTest {
        coEvery { repository.delete(1, 7) } returns Unit
        subject.awaitOrThrow(1, 7)
        coVerify(exactly = 1) { repository.delete(1, 7) }
    }
}
