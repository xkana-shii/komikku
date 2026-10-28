// KUN -->
package eu.kanade.tachiyomi.data.backup

import android.net.Uri
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.source.service.SourceManager
import java.io.IOException

/**
 * The validator turns any decode failure into an "invalid backup" error. Cancellation is not a
 * failure — a restore the user backs out of must stay cancelled, or the caller reports a broken
 * backup for an operation that never finished.
 *
 * [BackupDecoder] is a fake here: decoding needs a real `contentResolver` stream, which these
 * JVM-only tests do not have. What stays unverified is the decoder itself and the real
 * `contentResolver` path; what is verified is that the validator's own error handling passes each
 * failure kind through unchanged.
 */
class BackupFileValidatorTest {

    @Test
    fun `a cancelled decode is rethrown instead of being wrapped as an invalid backup`() {
        val cancelled = CancellationException("restore cancelled")
        val validator = validator(failingWith = cancelled)

        val thrown = assertThrows<CancellationException> {
            runBlocking { validator.validate(mockk<Uri>()) }
        }

        thrown shouldBe cancelled
    }

    @Test
    fun `an undecodable backup is still reported as an invalid backup`() {
        val failure = IOException("not a backup")
        val validator = validator(failingWith = failure)

        val thrown = assertThrows<IllegalStateException> {
            runBlocking { validator.validate(mockk<Uri>()) }
        }

        thrown.cause shouldBe failure
    }

    private inline fun <reified T : Throwable> validator(failingWith: T): BackupFileValidator {
        val decoder = mockk<BackupDecoder>()
        every {
            decoder.decodeMetadata(any<Uri>(), any<DeserializationStrategy<Any>>(), any())
        } throws failingWith
        return BackupFileValidator(mockk<SourceManager>(), mockk(), decoder)
    }
}
// KUN <--
