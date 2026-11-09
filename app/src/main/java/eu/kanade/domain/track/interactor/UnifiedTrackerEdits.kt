package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// KNS -->
/**
 * The unified tracker editor's queue: edits of all trackers run one after another (each one sees the
 * result of the previous one), and the last failure of every tracker is kept per manga until that
 * tracker is updated successfully again, so the tracker sheet can mark it.
 */
@SingleIn(AppScope::class)
@Inject
class UnifiedTrackerEdits(
    private val updateTracks: UpdateTracks,
    // KNS -->
    private val preferredTrackerSync: PreferredTrackerSync,
    // KNS <--
) {
    private val queue = Mutex()
    // KNS -->
    /** Mangas with a preferred-tracker sync waiting in the queue: another request meets it instead of queueing. */
    private val syncWaiting = ConcurrentHashMap<Long, AtomicBoolean>()
    // KNS <--
    private val failures = MutableStateFlow<Map<Long, Map<Long, String>>>(emptyMap())

    /** Tracker id → message of its last failed unified edit of [mangaId]. */
    fun failures(mangaId: Long): Flow<Map<Long, String>> =
        failures.map { it[mangaId].orEmpty() }.distinctUntilChanged()

    suspend fun apply(mangaId: Long, change: UpdateTracks.Change): UpdateTracks.Result = queue.withLock {
        updateTracks.await(mangaId, change).also {
            record(mangaId, it)
            // KNS --> a successful edit of the preferred tracker reaches the others through the sync as well
            synchronizeLocked(mangaId)
            // KNS <--
        }
    }

    // KNS -->
    /**
     * Brings the linked trackers in step with the preferred tracker (see [PreferredTrackerSync]). It is the one entry
     * point for this: it waits its turn behind running edits, and a request that arrives while another one is still
     * waiting is dropped, because the waiting one reads the saved records only when it starts.
     *
     * @param unreliableTrackerIds trackers that just failed to refresh; the preferred one among them pauses the sync.
     * @return null when nothing was to be done or the request met a waiting one.
     */
    suspend fun sync(mangaId: Long, unreliableTrackerIds: Set<Long> = emptySet()): PreferredTrackerSync.Outcome? {
        val waiting = syncWaiting.getOrPut(mangaId) { AtomicBoolean(false) }
        if (!waiting.compareAndSet(false, true)) return null
        return queue.withLock {
            waiting.set(false)
            synchronizeLocked(mangaId, unreliableTrackerIds)
        }
    }

    private suspend fun synchronizeLocked(
        mangaId: Long,
        unreliableTrackerIds: Set<Long> = emptySet(),
    ): PreferredTrackerSync.Outcome? {
        // A tracker whose last edit failed still holds the old values: they must not overwrite the edit elsewhere
        val unreliable = unreliableTrackerIds + failures.value[mangaId].orEmpty().keys
        return try {
            preferredTrackerSync.run(mangaId, unreliable)?.also { record(mangaId, it.toResult()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Reading the saved records failed: nothing was written, and the edit that got here has succeeded
            logcat(LogPriority.ERROR, e)
            null
        }
    }
    // KNS <--

    /** Unlinks [trackerIds] (and removes the entries on the services when [remotely]) in the same queue. */
    suspend fun remove(mangaId: Long, trackerIds: Set<Long>, remotely: Boolean) = queue.withLock {
        updateTracks.remove(mangaId, trackerIds, remotely).also {
            failures.update { all ->
                val current = all[mangaId].orEmpty() - trackerIds
                if (current.isEmpty()) all - mangaId else all + (mangaId to current)
            }
        }
    }

    internal fun record(mangaId: Long, result: UpdateTracks.Result) {
        failures.update { all ->
            val current = all[mangaId].orEmpty() - result.updatedTrackerIds - result.skippedTrackerIds +
                result.failures.associate { (tracker, error) ->
                    tracker.id to (error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName.orEmpty())
                }
            if (current.isEmpty()) all - mangaId else all + (mangaId to current)
        }
    }
}
// KNS <--
