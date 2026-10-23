package eu.kanade.tachiyomi.ui.manga

import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// KNS -->
/**
 * Runs [write] for submitted values one at a time, in submission order, on [scope]. Settings that
 * are stored the moment they change use it so a slow earlier write can never land after, and
 * overwrite, a newer one. A failed write is passed to [onError] and does not stop the ones after it.
 */
class SequentialWriter<T>(
    scope: CoroutineScope,
    private val onError: (T, Exception) -> Unit = ::logWriteFailure,
    private val write: suspend (T) -> Unit,
) {

    private val pending = Channel<T>(Channel.UNLIMITED)

    /** Submitted values not written yet. */
    private val unwritten = MutableStateFlow(0)

    init {
        scope.launch {
            for (value in pending) {
                try {
                    write(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(value, e)
                } finally {
                    unwritten.update { it - 1 }
                }
            }
        }
    }

    fun submit(value: T) {
        unwritten.update { it + 1 }
        if (pending.trySend(value).isFailure) unwritten.update { it - 1 }
    }

    /** Suspends until every value submitted so far has been written (or has failed). */
    suspend fun awaitIdle() {
        unwritten.first { it <= 0 }
    }
}

private fun logWriteFailure(value: Any?, e: Exception) {
    SequentialWriter::class.xLogE("Could not store $value", e)
}
// KNS <--
