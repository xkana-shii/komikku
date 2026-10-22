package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import logcat.asLog
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

// KNS -->
/** Replaces a saved search in place (same id, so feeds built on it keep pointing at it). From the previous personal fork. */
@Inject
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

    companion object {
        /** The saved search a new one named [name] replaces (names compare trimmed, ignoring case), if any. */
        fun <T> existingFor(name: String, searches: List<T>, nameOf: (T) -> String): T? {
            val trimmed = name.trim()
            return searches.firstOrNull { nameOf(it).trim().equals(trimmed, ignoreCase = true) }
        }
    }
}
// KNS <--
