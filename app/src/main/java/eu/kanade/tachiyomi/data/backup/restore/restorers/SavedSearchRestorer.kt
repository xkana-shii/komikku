package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.async.coroutines.awaitAsList
import tachiyomi.data.Database

import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import exh.EXHMigrations
import exh.util.nullIfBlank
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

class SavedSearchRestorer(
    private val database: Database = Injekt.get(),
) {
    suspend fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        if (backupSavedSearches.isEmpty()) return

        // KMK -->
        database.transaction {
            // KMK <--
            val currentSavedSearches = // KMK -->
                // database.saved_searchQueries.selectNamesAndSources()
                database.saved_searchQueries.selectAll().awaitAsList()
                // KMK <--

            backupSavedSearches.map {
                // KMK -->
                EXHMigrations.migrateBackupSavedSearch(it)
                // KMK <--
            }.filter { backupSavedSearch ->
                currentSavedSearches.none { currentSavedSearch ->
                    currentSavedSearch.source == backupSavedSearch.source &&
                        currentSavedSearch.name == backupSavedSearch.name &&
                        // KMK -->
                        currentSavedSearch.query.orEmpty() == backupSavedSearch.query &&
                        (currentSavedSearch.filters_json ?: "[]") == backupSavedSearch.filterList
                    // KMK <--
                }
            }.forEach { backupSavedSearch ->
                database.saved_searchQueries.insert(
                    source = backupSavedSearch.source,
                    name = backupSavedSearch.name,
                    query = backupSavedSearch.query.nullIfBlank(),
                    filtersJson = backupSavedSearch.filterList.nullIfBlank()
                        ?.takeUnless { it == "[]" },
                )
            }
        }
    }
}
// KMK <--
