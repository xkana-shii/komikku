package eu.kanade.tachiyomi.ui.manga

import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// KNS -->
/**
 * Runs [write] for submitted values one at a time, in submission order, on [scope]. Settings that
 * are stored the moment they change use it so a slow earlier write can never land after, and
 * overwrite, a newer one.
 */
class SequentialWriter<T>(scope: CoroutineScope, private val write: suspend (T) -> Unit) {

    private val pending = Channel<T>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (value in pending) {
                try {
                    write(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One failed write must not stop the ones after it.
                    xLogE("Could not store $value", e)
                }
            }
        }
    }

    fun submit(value: T) {
        pending.trySend(value)
    }
}
// KNS <--
