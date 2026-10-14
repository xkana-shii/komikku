package tachiyomi.data.source

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.FeedWithSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FeedSavedSearchRepositoryImpl(
    private val database: Database,
) : FeedSavedSearchRepository {

    // KUN -->
    override suspend fun getAllWithSavedSearch(): List<FeedWithSavedSearch> {
        val savedSearches = database.saved_searchQueries
            .selectAll(SavedSearchMapper::map)
            .awaitAsList()
            .associateBy { it.id }

        return database.feed_saved_searchQueries
            .selectAll(FeedSavedSearchMapper::map)
            .awaitAsList()
            .map { feed ->
                FeedWithSavedSearch(
                    feed = feed,
                    savedSearch = feed.savedSearch?.let(savedSearches::get),
                )
            }
    }
    // KUN <--

    override suspend fun getGlobal(): List<FeedSavedSearch> {
        return database.feed_saved_searchQueries
            .selectAllGlobal(FeedSavedSearchMapper::map)
            .awaitAsList()
    }

    override fun getGlobalAsFlow(): Flow<List<FeedSavedSearch>> {
        return database.feed_saved_searchQueries
            .selectAllGlobal(FeedSavedSearchMapper::map)
            .subscribeToList()
    }

    override suspend fun getGlobalFeedSavedSearch(): List<SavedSearch> {
        return database.feed_saved_searchQueries
            .selectGlobalFeedSavedSearch(SavedSearchMapper::map)
            .awaitAsList()
    }

    override suspend fun countGlobal(): Long {
        return database.feed_saved_searchQueries
            .countGlobal()
            .awaitAsOne()
    }

    override suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch> {
        return database.feed_saved_searchQueries
            .selectBySource(sourceId, FeedSavedSearchMapper::map)
            .awaitAsList()
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>> {
        return database.feed_saved_searchQueries
            .selectBySource(sourceId, FeedSavedSearchMapper::map)
            .subscribeToList()
    }

    override suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch> {
        return database.feed_saved_searchQueries
            .selectSourceFeedSavedSearch(sourceId, SavedSearchMapper::map)
            .awaitAsList()
    }

    override suspend fun countBySourceId(sourceId: Long): Long {
        return database.feed_saved_searchQueries
            .countSourceFeedSavedSearch(sourceId)
            .awaitAsOne()
    }

    override suspend fun delete(feedSavedSearchId: Long) {
        database.feed_saved_searchQueries
            .deleteById(feedSavedSearchId)
    }

    override suspend fun insert(feedSavedSearch: FeedSavedSearch): Long {
        // KMK -->
        return database.transactionWithResult {
            val currentFeeds = database.feed_saved_searchQueries
                .selectAll(FeedSavedSearchMapper::map)
                .awaitAsList()
            val existedFeedId = currentFeeds.find { currentFeed ->
                currentFeed.source == feedSavedSearch.source &&
                    currentFeed.savedSearch == feedSavedSearch.savedSearch &&
                    currentFeed.global == feedSavedSearch.global
            }?.id

            existedFeedId
                // KMK <--
                ?: database.feed_saved_searchQueries.insert(
                    feedSavedSearch.source,
                    feedSavedSearch.savedSearch,
                    feedSavedSearch.global,
                ).awaitAsOne()
        }
    }

    override suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>) {
        return database.transaction {
            feedSavedSearch.forEach {
                database.feed_saved_searchQueries.insert(
                    it.source,
                    it.savedSearch,
                    it.global,
                ).awaitAsOne()
            }
        }
    }

    // KMK -->
    override suspend fun updatePartial(update: FeedSavedSearchUpdate) {
        database.updatePartialBlocking(update)
    }

    override suspend fun updatePartial(updates: List<FeedSavedSearchUpdate>) {
        database.transaction {
            updates.forEach { database.updatePartialBlocking(it) }
        }
    }

    private suspend fun Database.updatePartialBlocking(update: FeedSavedSearchUpdate) {
        feed_saved_searchQueries.update(
            source = update.source,
            saved_search = update.savedSearch,
            global = update.global,
            feed_order = update.feedOrder,
            id = update.id,
        )
    }
    // KMK <--
}
