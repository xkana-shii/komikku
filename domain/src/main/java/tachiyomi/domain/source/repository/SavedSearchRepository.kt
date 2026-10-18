package tachiyomi.domain.source.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.SavedSearch

interface SavedSearchRepository {

    suspend fun getById(savedSearchId: Long): SavedSearch?

    // KUN -->
    suspend fun getAll(): List<SavedSearch>

    suspend fun deleteAll()
    // KUN <--

    suspend fun getBySourceId(sourceId: Long): List<SavedSearch>

    fun getBySourceIdAsFlow(sourceId: Long): Flow<List<SavedSearch>>

    suspend fun delete(savedSearchId: Long)

    suspend fun insert(savedSearch: SavedSearch): Long?

    // KMK -->
    suspend fun update(savedSearch: SavedSearch)
    // KMK <--

    suspend fun insertAll(savedSearch: List<SavedSearch>)
}
