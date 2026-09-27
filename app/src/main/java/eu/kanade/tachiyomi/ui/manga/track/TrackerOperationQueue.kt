package eu.kanade.tachiyomi.ui.manga.track

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// KMK --> Serialize sheet operations, including their reads and persistence, so queued
// taps see the previous operation's result. Services within each batch still run concurrently.
internal class TrackerOperationQueue {
    private val mutex = Mutex()

    suspend fun run(operation: suspend () -> Unit) = mutex.withLock { operation() }
}
// KMK <--
