package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
) {
    private val queue = Mutex()
    private val failures = MutableStateFlow<Map<Long, Map<Long, String>>>(emptyMap())

    /** Tracker id → message of its last failed unified edit of [mangaId]. */
    fun failures(mangaId: Long): Flow<Map<Long, String>> =
        failures.map { it[mangaId].orEmpty() }.distinctUntilChanged()

    suspend fun apply(mangaId: Long, change: UpdateTracks.Change): UpdateTracks.Result = queue.withLock {
        updateTracks.await(mangaId, change).also { record(mangaId, it) }
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
