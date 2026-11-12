package eu.kanade.tachiyomi.data.job

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// KNS -->
/**
 * Keeps backup creation and library updates from ever running at the same time, whichever way each was started
 * (scheduled, manual or from a notification). The second job simply waits, before it shows any progress, until the
 * first one finishes, then starts. Both workers run in the app process, so one lock is enough.
 */
object BackupLibraryUpdateGate {
    private val mutex = Mutex()

    suspend fun <T> withExclusiveAccess(block: suspend () -> T): T = mutex.withLock { block() }
}
// KNS <--
