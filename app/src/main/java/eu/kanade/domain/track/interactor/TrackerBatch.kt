package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.Tracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track

// KMK --> Network work is concurrent; returned records are persisted sequentially after the batch settles.
internal class TrackerBatchItem(val service: Tracker, val binding: Track) {
    var returned: Track? = null
        private set

    suspend fun refresh(): Track = service.refresh(binding.toDbTrack()).toDomainTrack()!!.also { returned = it }

    suspend fun update(track: Track, didReadChapter: Boolean = false): Track =
        service.update(track.toDbTrack(), didReadChapter).toDomainTrack()!!.also { returned = it }
}

internal data class TrackerBatchResult(val service: Tracker, val track: Track?, val error: Throwable?, val persistenceError: Throwable? = null) {
    val failures get() = listOfNotNull(error, persistenceError)
}

internal suspend fun trackerBatch(
    entries: List<Pair<Tracker, Track>>,
    insert: InsertTrack,
    operation: suspend TrackerBatchItem.() -> Unit,
): List<TrackerBatchResult> = supervisorScope {
    val results = entries.distinctBy { it.first.id }.map { (service, binding) ->
        async {
            val item = TrackerBatchItem(service, binding)
            val error = try {
                item.operation()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            TrackerBatchResult(service, item.returned, error)
        }
    }.awaitAll()
    results.map { result ->
        try {
            result.track?.let { insert.awaitOrThrow(it) }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Retain both failures if refreshing succeeded but the subsequent update and persistence failed.
            if (result.error == null) result.copy(error = e) else result.copy(persistenceError = e)
        }
    }
}
// KMK <--
