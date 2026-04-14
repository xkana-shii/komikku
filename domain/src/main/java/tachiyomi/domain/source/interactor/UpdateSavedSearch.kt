package tachiyomi.domain.source.interactor

import logcat.LogPriority
import logcat.asLog
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

class UpdateSavedSearch(
    private val savedSearchRepository: SavedSearchRepository,
) {

    suspend fun await(savedSearch: SavedSearch) {
        try {
            savedSearchRepository.update(savedSearch)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { e.asLog() }
        }
    }
}
