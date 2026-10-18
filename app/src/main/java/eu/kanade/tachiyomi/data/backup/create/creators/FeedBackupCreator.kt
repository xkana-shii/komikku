package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

@Inject
class FeedBackupCreator(
    private val feedSavedSearchRepository: FeedSavedSearchRepository,
) {

    /**
     * Backup:
     * - Global Popular/Latest feeds
     * - Global feeds from saved searches
     * - Source's feeds from saved searches
     */
    suspend operator fun invoke(): List<BackupFeed> {
        return feedSavedSearchRepository.getAllWithSavedSearch().map { (feed, savedSearch) ->
            BackupFeed(
                source = feed.source,
                global = feed.global,
                savedSearch = savedSearch?.let {
                    BackupSavedSearch(
                        // The joined backup row carries the feed's source, not the saved search's.
                        source = feed.source,
                        name = it.name,
                        query = it.query.orEmpty(),
                        filterList = it.filtersJson ?: "[]",
                    )
                },
            )
        }
    }
}
