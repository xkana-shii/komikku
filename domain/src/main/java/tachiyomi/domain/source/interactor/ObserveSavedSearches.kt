package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

// KNS -->
/**
 * Resolves feed rows and saved searches from the same observed snapshot. Replacing a saved search
 * updates the resolved value even though the feed row and its saved-search id stay unchanged.
 */
@Inject
class ObserveSavedSearches(
    private val savedSearchRepository: SavedSearchRepository,
) {
    fun resolve(feeds: Flow<List<FeedSavedSearch>>): Flow<List<ResolvedFeedSavedSearch>> {
        // KNS --> The write event is a second, explicit invalidation path for active ViewModels.
        val savedSearches = merge(
            savedSearchRepository.getAllAsFlow(),
            savedSearchRepository.getUpdatesAsFlow().mapLatest { savedSearchRepository.getAll() },
        ).distinctUntilChanged()
        return resolve(feeds, savedSearches)
        // KNS <--
    }

    companion object {
        fun resolve(
            feeds: Flow<List<FeedSavedSearch>>,
            savedSearches: Flow<List<SavedSearch>>,
        ): Flow<List<ResolvedFeedSavedSearch>> = combine(
            feeds,
            savedSearches.distinctUntilChanged(),
        ) { feedRows, searches ->
            val searchesById = searches.associateBy(SavedSearch::id)
            feedRows.map { feed -> ResolvedFeedSavedSearch(feed, searchesById[feed.savedSearch]) }
        }.distinctUntilChanged()
    }
}

data class ResolvedFeedSavedSearch(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
)
// KNS <--
