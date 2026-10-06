package eu.kanade.tachiyomi.data.backup.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tachiyomi.data.Database

// KMK -->
/** Retry a failed chunk separately: a failed nested transaction rolls back the whole chunk. */
internal suspend fun <T> Database.restoreBatch(
    entries: List<T>,
    restore: suspend (T) -> Unit,
    onError: (T, Exception) -> Unit,
) {
    try {
        transaction {
            entries.forEach {
                currentCoroutineContext().ensureActive()
                restore(it)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        entries.forEach { entry ->
            currentCoroutineContext().ensureActive()
            try {
                transaction { restore(entry) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(entry, e)
            }
        }
    }
}
// KMK <--
