package tachiyomi.domain.history.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository

@Inject
class RemoveHistory(
    private val repository: HistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllHistory()
    }

    // KMK -->
    suspend fun await(historyIds: List<Long>) {
        repository.resetHistory(historyIds)
    }

    suspend fun awaitManga(mangaIds: List<Long>) {
        repository.resetHistoryByMangaIds(mangaIds)
    }
    // KMK <--
}
