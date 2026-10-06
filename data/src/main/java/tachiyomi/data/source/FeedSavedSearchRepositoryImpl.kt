package tachiyomi.data.source

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import tachiyomi.data.subscribeToList

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository

// KMK -->

class FeedSavedSearchRepositoryImpl(
    private val database: Database,
) : FeedSavedSearchRepository {

    override suspend fun getGlobal(): List<FeedSavedSearch> {
        return database.feed_saved_searchQueries.selectAllGlobal(FeedSavedSearchMapper::map).awaitAsList()
    }

    override fun getGlobalAsFlow(): Flow<List<FeedSavedSearch>> {
        return database.feed_saved_searchQueries.selectAllGlobal(FeedSavedSearchMapper::map).subscribeToList()
    }

    override suspend fun getGlobalFeedSavedSearch(): List<SavedSearch> {
        return database.feed_saved_searchQueries.selectGlobalFeedSavedSearch(SavedSearchMapper::map).awaitAsList()
    }

    override suspend fun countGlobal(): Long {
        return database.feed_saved_searchQueries.countGlobal().awaitAsOne()
    }

    override suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch> {
        return database.feed_saved_searchQueries.selectBySource(sourceId, FeedSavedSearchMapper::map).awaitAsList()
    }

    override fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>> {
        return database.feed_saved_searchQueries.selectBySource(sourceId, FeedSavedSearchMapper::map).subscribeToList()
    }

    override suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch> {
        return database.feed_saved_searchQueries.selectSourceFeedSavedSearch(sourceId, SavedSearchMapper::map).awaitAsList()
    }

    override suspend fun countBySourceId(sourceId: Long): Long {
        return database.feed_saved_searchQueries.countSourceFeedSavedSearch(sourceId).awaitAsOne()
    }

    override suspend fun delete(feedSavedSearchId: Long) {
        database.feed_saved_searchQueries.deleteById(feedSavedSearchId)
    }

    override suspend fun insert(feedSavedSearch: FeedSavedSearch): Long {
        // KMK -->
        return database.transactionWithResult {
            val currentFeeds = database.feed_saved_searchQueries.selectAll(FeedSavedSearchMapper::map).awaitAsList()
            val existedFeedId = currentFeeds.find { currentFeed ->
                currentFeed.source == feedSavedSearch.source &&
                    currentFeed.savedSearch == feedSavedSearch.savedSearch &&
                    currentFeed.global == feedSavedSearch.global
            }?.id

            existedFeedId
                // KMK <--
                ?: database.transactionWithResult {
                    database.feed_saved_searchQueries.insertReturningId(
                        feedSavedSearch.source,
                        feedSavedSearch.savedSearch,
                        feedSavedSearch.global,
                    ).awaitAsOne()
}
        }
    }

    override suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>) {
        return database.transactionWithResult {
            feedSavedSearch.forEach {
                database.feed_saved_searchQueries.insert(
                    it.source,
                    it.savedSearch,
                    it.global,
                )
            }
        }
    }

    // KMK -->
    override suspend fun updatePartial(update: FeedSavedSearchUpdate) {
        updatePartialBlocking(update)
    }

    override suspend fun updatePartial(updates: List<FeedSavedSearchUpdate>) {
        database.transaction {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private suspend fun updatePartialBlocking(update: FeedSavedSearchUpdate) {
        database.feed_saved_searchQueries.update(
            source = update.source,
            saved_search = update.savedSearch,
            global = update.global,
            feed_order = update.feedOrder,
            id = update.id,
        )
    }
    // KMK <--
}
// KMK <--
