package eu.kanade.tachiyomi.data.coil

import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CoverFailureClassifierTest {

    @Test
    fun `wrapped DNS failures are recoverable`() {
        // Call.await wraps the original exception, as seen in the reported crash.
        IOException("Unable to resolve host", UnknownHostException("example.org"))
            .isRecoverableCoverFailure() shouldBe true
    }

    @Test
    fun `connection failures are recoverable`() {
        ConnectException("Connection refused").isRecoverableCoverFailure() shouldBe true
    }

    @Test
    fun `stale URL status codes are recoverable`() {
        listOf(403, 404, 410).forEach { code ->
            HttpException(code).isRecoverableCoverFailure() shouldBe true
        }
    }

    @Test
    fun `transient errors are not treated as stale URLs`() {
        HttpException(500).isRecoverableCoverFailure() shouldBe false
        HttpException(429).isRecoverableCoverFailure() shouldBe false
        SocketTimeoutException("timeout").isRecoverableCoverFailure() shouldBe false
    }
}
