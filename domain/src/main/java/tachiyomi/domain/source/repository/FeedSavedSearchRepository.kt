package tachiyomi.domain.source.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.FeedWithSavedSearch
import tachiyomi.domain.source.model.SavedSearch

interface FeedSavedSearchRepository {

    // KUN -->
    /**
     * Every feed together with the saved search it points at, if any (the backup/restore view of
     * the `selectAllFeedWithSavedSearch` join).
     */
    suspend fun getAllWithSavedSearch(): List<FeedWithSavedSearch>
    // KUN <--

    suspend fun getGlobal(): List<FeedSavedSearch>

    fun getGlobalAsFlow(): Flow<List<FeedSavedSearch>>

    suspend fun getGlobalFeedSavedSearch(): List<SavedSearch>

    suspend fun countGlobal(): Long

    suspend fun getBySourceId(sourceId: Long): List<FeedSavedSearch>

    fun getBySourceIdAsFlow(sourceId: Long): Flow<List<FeedSavedSearch>>

    suspend fun getBySourceIdFeedSavedSearch(sourceId: Long): List<SavedSearch>

    suspend fun countBySourceId(sourceId: Long): Long

    suspend fun delete(feedSavedSearchId: Long)

    suspend fun insert(feedSavedSearch: FeedSavedSearch): Long?

    suspend fun insertAll(feedSavedSearch: List<FeedSavedSearch>)

    // KMK -->
    suspend fun updatePartial(update: FeedSavedSearchUpdate)

    suspend fun updatePartial(updates: List<FeedSavedSearchUpdate>)
    // KMK <--
}
