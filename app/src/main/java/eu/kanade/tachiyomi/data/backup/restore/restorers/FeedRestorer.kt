package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import exh.EXHMigrations
import exh.util.nullIfBlank
import tachiyomi.data.Database
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

class FeedRestorer(
    private val database: Database = Injekt.get(),
) {
    suspend fun restoreFeeds(backupFeeds: List<BackupFeed>) {
        if (backupFeeds.isEmpty()) return

        database.transactionWithResult {
            val currentFeeds = database.feed_saved_searchQueries.selectAllFeedWithSavedSearch().awaitAsList()
            val currentSavedSearches = database.saved_searchQueries.selectAll().awaitAsList()

            backupFeeds.map {
                // KMK -->
                EXHMigrations.migrateBackupFeed(it)
                // KMK <--
            }.filter { backupFeed ->
                // Filter out source's global Popular/Latest feed already existed
                (
                    backupFeed.savedSearch == null &&
                        currentFeeds.none { currentFeed ->
                            currentFeed.source == backupFeed.source && backupFeed.global
                        }
                    ) ||
                    // Filter out feed with saveSearch already existed (both global/non-global)
                    (
                        backupFeed.savedSearch != null &&
                            currentFeeds.none { currentFeed ->
                                currentFeed.source == backupFeed.source &&
                                    currentFeed.global == backupFeed.global &&
                                    currentFeed.name == backupFeed.savedSearch.name &&
                                    currentFeed.query.orEmpty() == backupFeed.savedSearch.query &&
                                    (currentFeed.filters_json ?: "[]") == backupFeed.savedSearch.filterList
                            }
                        )
            }.forEach { backupFeed ->
                val savedSearchId = backupFeed.savedSearch?.let {
                    val existedSavedSearchId = currentSavedSearches.find { currentSavedSearch ->
                        currentSavedSearch.source == backupFeed.source &&
                            currentSavedSearch.name == backupFeed.savedSearch.name &&
                            currentSavedSearch.query.orEmpty() == backupFeed.savedSearch.query &&
                            (currentSavedSearch.filters_json ?: "[]") == backupFeed.savedSearch.filterList
                    }?._id

                    // KMK -->
                    existedSavedSearchId ?:
                        // Just in case, trying to create the associated saved_search
                        database.saved_searchQueries.insertReturningId(
                            source = backupFeed.source,
                            name = backupFeed.savedSearch.name,
                            query = backupFeed.savedSearch.query.nullIfBlank(),
                            filtersJson = backupFeed.savedSearch.filterList.nullIfBlank()
                                ?.takeUnless { it == "[]" },
                        ).awaitAsOne()
                    // KMK <--
                }

                database.feed_saved_searchQueries.insert(
                    sourceId = backupFeed.source,
                    savedSearch = savedSearchId,
                    global = backupFeed.global,
                )
            }
        }
    }
}
// KMK <--
