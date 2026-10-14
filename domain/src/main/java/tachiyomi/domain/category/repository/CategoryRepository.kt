package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate

interface CategoryRepository {

    suspend fun get(id: Long): Category?

    suspend fun getAll(): List<Category>

    fun getAllAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByMangaId(mangaId: Long): List<Category>

    fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>>

    suspend fun getCategoriesByMangaIds(mangaIds: List<Long>): Map<Long, List<Category>>

    // SY -->
    suspend fun insert(category: Category): Long?

    /**
     * Restores [categories] from a backup or sync delta, matching existing entries by uid (or name),
     * updating them in place and inserting the rest. Returns the categories as they now stand.
     */
    suspend fun restoreCategories(categories: List<Category>, isSync: Boolean): List<Category>
    // SY <--

    suspend fun updateName(categoryId: Long, name: String)

    suspend fun updateFlags(categoryId: Long, flags: Long)

    suspend fun updateAllFlags(flags: Long?)

    suspend fun updateAllOrders(orderedIds: List<Long>)

    // KMK -->
    suspend fun updatePartial(update: CategoryUpdate)

    suspend fun updatePartial(updates: List<CategoryUpdate>)
    // KMK <--

    suspend fun delete(categoryId: Long)
}
