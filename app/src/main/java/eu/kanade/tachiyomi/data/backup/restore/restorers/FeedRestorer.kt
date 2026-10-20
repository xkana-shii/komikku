package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import exh.EXHMigrations
import exh.util.nullIfBlank
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedWithSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository
import tachiyomi.domain.source.repository.SavedSearchRepository

@Inject
class FeedRestorer(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
    private val savedSearchRepository: SavedSearchRepository,
) {
    suspend fun restoreFeeds(backupFeeds: List<BackupFeed>) {
        if (backupFeeds.isEmpty()) return

        val currentFeeds = feedSavedSearchRepository.getAllWithSavedSearch()
        val currentSavedSearches = savedSearchRepository.getAll()

        backupFeeds.map {
            // KMK -->
            EXHMigrations.migrateBackupFeed(it)
            // KMK <--
        }.filter { backupFeed ->
            // KUN -->
            // A feed's identity is its source, whether it is global, and (for saved searches) the
            // saved search itself. A saved-search feed for the same source must not suppress
            // restoring that source's global Popular/Latest feed, and vice versa.
            currentFeeds.none { currentFeed ->
                currentFeed.feed.source == backupFeed.source &&
                    currentFeed.feed.global == backupFeed.global &&
                    currentFeed.matchesSavedSearchOf(backupFeed)
            }
            // KUN <--
        }.forEach { backupFeed ->
            val savedSearchId = backupFeed.savedSearch?.let {
                val existedSavedSearchId = currentSavedSearches.find { currentSavedSearch ->
                    currentSavedSearch.source == backupFeed.source &&
                        currentSavedSearch.name == backupFeed.savedSearch.name &&
                        currentSavedSearch.query.orEmpty() == backupFeed.savedSearch.query &&
                        (currentSavedSearch.filtersJson ?: "[]") == backupFeed.savedSearch.filterList
                }?.id

                existedSavedSearchId ?: savedSearchRepository.insert(
                    // Just in case, trying to create the associated saved_search
                    SavedSearch(
                        id = -1,
                        source = backupFeed.source,
                        name = backupFeed.savedSearch.name,
                        query = backupFeed.savedSearch.query.nullIfBlank(),
                        filtersJson = backupFeed.savedSearch.filterList.nullIfBlank()
                            ?.takeUnless { it == "[]" },
                    ),
                )
            }

            feedSavedSearchRepository.insert(
                FeedSavedSearch(
                    id = -1,
                    source = backupFeed.source,
                    savedSearch = savedSearchId,
                    global = backupFeed.global,
                    feedOrder = 0,
                ),
            )
        }
    }

    // KUN -->
    /**
     * Whether this stored feed represents the same saved search as [backupFeed]. A feed without a
     * saved search (the source's global Popular/Latest feed) only matches another feed without one.
     */
    private fun FeedWithSavedSearch.matchesSavedSearchOf(backupFeed: BackupFeed): Boolean {
        val backupSavedSearch: BackupSavedSearch = backupFeed.savedSearch ?: return savedSearch == null
        val currentSavedSearch: SavedSearch = savedSearch ?: return false
        return currentSavedSearch.name == backupSavedSearch.name &&
            currentSavedSearch.query.orEmpty() == backupSavedSearch.query &&
            (currentSavedSearch.filtersJson ?: "[]") == backupSavedSearch.filterList
    }
    // KUN <--
}
