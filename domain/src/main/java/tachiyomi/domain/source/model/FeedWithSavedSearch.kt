package tachiyomi.domain.source.model

/**
 * A [FeedSavedSearch] together with the [SavedSearch] it points at, if any. This is the shape of
 * the `selectAllFeedWithSavedSearch` join.
 */
data class FeedWithSavedSearch(
    val feed: FeedSavedSearch,
    val savedSearch: SavedSearch?,
)
