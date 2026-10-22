package tachiyomi.domain.source.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import tachiyomi.domain.source.model.SavedSearch

interface SavedSearchRepository {

    suspend fun getById(savedSearchId: Long): SavedSearch?

    // KUN -->
    suspend fun getAll(): List<SavedSearch>

    suspend fun deleteAll()

    suspend fun update(savedSearch: SavedSearch)

    // KNS --> every saved search, re-emitted when one is added, replaced or removed
    fun getAllAsFlow(): Flow<List<SavedSearch>>

    /** Successful in-place replacements, emitted immediately after their database write. */
    fun getUpdatesAsFlow(): Flow<SavedSearch> = emptyFlow()
    // KNS <--
    // KUN <--

    suspend fun getBySourceId(sourceId: Long): List<SavedSearch>

    fun getBySourceIdAsFlow(sourceId: Long): Flow<List<SavedSearch>>

    suspend fun delete(savedSearchId: Long)

    suspend fun insert(savedSearch: SavedSearch): Long?

    suspend fun insertAll(savedSearch: List<SavedSearch>)
}
