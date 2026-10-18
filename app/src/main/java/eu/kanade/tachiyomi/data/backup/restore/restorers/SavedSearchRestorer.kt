package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import exh.EXHMigrations
import exh.util.nullIfBlank
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

@Inject
class SavedSearchRestorer(
    private val savedSearchRepository: SavedSearchRepository,
) {
    suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        if (backupSavedSearches.isEmpty()) return

        val currentSavedSearches = savedSearchRepository.getAll()

        val savedSearchesToRestore = backupSavedSearches
            .map {
                // KMK -->
                EXHMigrations.migrateBackupSavedSearch(it)
                // KMK <--
            }
            .filter { backupSavedSearch ->
                currentSavedSearches.none { currentSavedSearch ->
                    currentSavedSearch.source == backupSavedSearch.source &&
                        currentSavedSearch.name == backupSavedSearch.name &&
                        // KMK -->
                        currentSavedSearch.query.orEmpty() == backupSavedSearch.query &&
                        (currentSavedSearch.filtersJson ?: "[]") == backupSavedSearch.filterList
                    // KMK <--
                }
            }
            .map { backupSavedSearch ->
                SavedSearch(
                    id = -1,
                    source = backupSavedSearch.source,
                    name = backupSavedSearch.name,
                    query = backupSavedSearch.query.nullIfBlank(),
                    filtersJson = backupSavedSearch.filterList.nullIfBlank()
                        ?.takeUnless { it == "[]" },
                )
            }

        savedSearchRepository.insertAll(savedSearchesToRestore)
    }
}
