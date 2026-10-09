package eu.kanade.tachiyomi.ui.reader.model

// KMK -->
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DownloadStreamTest {

    @Test
    fun `reader receives bytes appended while download is active`() {
        val stream = DownloadStream()
        val readerStarted = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val result = executor.submit<String> {
                readerStarted.countDown()
                stream.reader().use { it.readBytes().decodeToString() }
            }

            readerStarted.await(2, TimeUnit.SECONDS) shouldBe true
            val first = "progressive ".encodeToByteArray()
            val second = "image".encodeToByteArray()
            stream.append(first, 0, first.size)
            stream.append(second, 0, second.size)
            stream.finish()

            result.get(2, TimeUnit.SECONDS) shouldBe "progressive image"
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `reader surfaces download failure`() {
        val stream = DownloadStream()
        val failure = IllegalStateException("network failed")
        val reader = stream.reader()

        stream.finish(failure)

        shouldThrow<IOException> { reader.read() }.cause shouldBe failure
    }

    @Test
    fun `closing preview reader does not fail the download producer`() {
        val stream = DownloadStream()
        val reader = stream.reader()
        val bytes = "remaining download".encodeToByteArray()

        reader.close()
        stream.append(bytes, 0, bytes.size)
        stream.finish()
    }
}
// KMK <--
