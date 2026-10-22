package tachiyomi.domain.source.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.SavedSearchRepository

// KNS -->
/**
 * Feeds point at their saved search by id, and replacing a saved search updates it in place. The feed
 * lists only watch the feed table, so this re-emits [feeds] whenever a saved search changes too, and a
 * feed then reloads with the saved search's new query and filters.
 */
@Inject
class ObserveSavedSearches(
    private val savedSearchRepository: SavedSearchRepository,
) {
    fun <T> withSavedSearches(feeds: Flow<T>): Flow<T> = reloadOn(feeds, savedSearchRepository.getAllAsFlow())

    companion object {
        /** [feeds], emitted again whenever [savedSearches] changes. */
        fun <T> reloadOn(feeds: Flow<T>, savedSearches: Flow<List<SavedSearch>>): Flow<T> =
            combine(feeds, savedSearches.distinctUntilChanged()) { feed, searches -> feed to searches }
                .distinctUntilChanged()
                .let { pairs -> kotlinx.coroutines.flow.flow { pairs.collect { emit(it.first) } } }
    }
}
// KNS <--
