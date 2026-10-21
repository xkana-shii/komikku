package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.model.RestoredCategoryHierarchy

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
    suspend fun restoreCategories(
        categories: List<Category>,
        isSync: Boolean,
        // KNS -->
        hierarchy: RestoredCategoryHierarchy = RestoredCategoryHierarchy(),
        // KNS <--
    ): List<Category>
    // SY <--

    suspend fun updateName(categoryId: Long, name: String)

    suspend fun updateFlags(categoryId: Long, flags: Long)

    suspend fun updateAllFlags(flags: Long?)

    suspend fun updateAllOrders(orderedIds: List<Long>)

    suspend fun updatePartial(update: CategoryUpdate)

    suspend fun updatePartial(updates: List<CategoryUpdate>)

    suspend fun delete(categoryId: Long)

    // KUN -->
    /**
     * Deletes [categoryIds] in a single transaction so observers see one list update.
     * Callers must exclude the system category (`id <= 0`): `system_category_delete_trigger`
     * aborts the whole batch. The batch is atomic.
     */
    suspend fun delete(categoryIds: List<Long>)
    // KUN <--

    // KNS -->
    /**
     * Writes the whole hierarchy in one transaction: each category's parent and the depth-first
     * global order given by the list position. Observers see one list update, and a burst of
     * drag-and-drop reorders costs one write instead of one per step.
     */
    suspend fun updateHierarchy(ordered: List<Category>)

    suspend fun updateTrackStatus(categoryId: Long, trackStatus: Int?)

    suspend fun updateUpdateInterval(categoryId: Long, updateInterval: Int?)

    /** Adds every manga of [sourceIds] to [targetId], keeping their other memberships. */
    suspend fun copyMemberships(sourceIds: List<Long>, targetId: Long)
    // KNS <--
}
