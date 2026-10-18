package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import exh.EXHMigrations
import exh.util.nullIfBlank
import tachiyomi.domain.source.model.FeedSavedSearch
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
            // Filter out source's global Popular/Latest feed already existed
            (
                backupFeed.savedSearch == null &&
                    currentFeeds.none { currentFeed ->
                        currentFeed.feed.source == backupFeed.source && backupFeed.global
                    }
                ) ||
                // Filter out feed with saveSearch already existed (both global/non-global)
                (
                    backupFeed.savedSearch != null &&
                        currentFeeds.none { currentFeed ->
                            currentFeed.feed.source == backupFeed.source &&
                                currentFeed.feed.global == backupFeed.global &&
                                currentFeed.savedSearch?.name == backupFeed.savedSearch.name &&
                                currentFeed.savedSearch?.query.orEmpty() == backupFeed.savedSearch.query &&
                                (currentFeed.savedSearch?.filtersJson ?: "[]") == backupFeed.savedSearch.filterList
                        }
                    )
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
}
