package tachiyomi.domain.history.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.history.service.HistoryTombstoneDelivery

@Inject
class RemoveHistory(
    private val repository: HistoryRepository,
    // KUN -->
    private val tombstoneDelivery: HistoryTombstoneDelivery,
    // KUN <--
) {

    suspend fun awaitAll(): Boolean {
        // KUN -->
        // "Clear all" is the same soft delete as the other two paths, so it is queued only when a
        // server can be told about it: a marker nothing can deliver would block every removed row.
        return repository.removeAllHistory(tombstoneDelivery.canDeliver())
        // KUN <--
    }

    // KMK -->
    suspend fun await(historyIds: List<Long>) {
        // KUN -->
        // Queued only when the configured sync can actually send the tombstone; otherwise the rows
        // are just reset, and any stale marker on them is dropped.
        repository.removeHistory(historyIds, tombstoneDelivery.canDeliver())
        // KUN <--
    }

    suspend fun awaitManga(mangaIds: List<Long>) {
        // KUN -->
        repository.removeHistoryByMangaIds(mangaIds, tombstoneDelivery.canDeliver())
        // KUN <--
    }
    // KMK <--
}
