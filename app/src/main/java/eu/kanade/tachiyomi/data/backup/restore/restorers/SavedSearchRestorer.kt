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

        // KUN -->
        val currentSavedSearchesMap = currentSavedSearches.associateBy { it.source to it.name }
        val toInsert = mutableListOf<SavedSearch>()

        backupSavedSearches
            .map {
                // KMK -->
                EXHMigrations.migrateBackupSavedSearch(it)
                // KMK <--
            }
            .forEach { backupSavedSearch ->
                val key = backupSavedSearch.source to backupSavedSearch.name
                val existing = currentSavedSearchesMap[key]
                val query = backupSavedSearch.query.nullIfBlank()
                val filtersJson = backupSavedSearch.filterList.nullIfBlank()?.takeUnless { it == "[]" }

                if (existing != null) {
                    if (existing.query != query || existing.filtersJson != filtersJson) {
                        savedSearchRepository.update(
                            existing.copy(
                                query = query,
                                filtersJson = filtersJson,
                            ),
                        )
                    }
                } else {
                    toInsert.add(
                        SavedSearch(
                            id = -1,
                            source = backupSavedSearch.source,
                            name = backupSavedSearch.name,
                            query = query,
                            filtersJson = filtersJson,
                        ),
                    )
                }
            }

        if (toInsert.isNotEmpty()) {
            savedSearchRepository.insertAll(toInsert)
        }
        // KUN <--
    }
}
