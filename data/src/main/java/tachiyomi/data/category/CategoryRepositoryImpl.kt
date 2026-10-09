package tachiyomi.data.category

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

// KMK -->

class CategoryRepositoryImpl(
    private val database: Database,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return database.categoryQueries.getCategory(id, CategoryMapper::mapCategory).awaitAsOneOrNull()
    }

    override suspend fun getAll(): List<Category> {
        return database.categoryQueries.getCategories(CategoryMapper::mapCategory).awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return database.categoryQueries.getCategories(CategoryMapper::mapCategory).subscribeToList()
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoryQueries.getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory).awaitAsList()
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoryQueries.getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory).subscribeToList()
    }

    // SY -->
    override suspend fun insert(category: Category): Long {
        return database.transactionWithResult {
            database.categoryQueries.insertReturningId(
                name = category.name,
                order = category.order,
                flags = category.flags,
                // KMK -->
                hidden = if (category.hidden) 1L else 0L,
                // KMK <--
            ).awaitAsOne()
        }
    }
    // SY <--

    override suspend fun updateName(categoryId: Long, name: String) {
        database.categoryQueries.updateName(name = name, id = categoryId)
    }

    override suspend fun updateFlags(categoryId: Long, flags: Long) {
        database.categoryQueries.updateFlags(flags = flags, id = categoryId)
    }

    // KMK -->
    override suspend fun updateNameAndOrder(categoryId: Long, name: String, order: Long) {
        database.categoryQueries.updateNameAndOrder(name = name, order = order, categoryId = categoryId)
    }

    override suspend fun updateHidden(categoryId: Long, hidden: Boolean) {
        database.categoryQueries.updateHidden(hidden = if (hidden) 1L else 0L, categoryId = categoryId)
    }
    // KMK <--

    override suspend fun updateAllFlags(flags: Long) {
        database.categoryQueries.updateAllFlags(flags = flags)
    }

    override suspend fun updateAllOrders(orderedIds: List<Long>) {
        database.transaction {
            val current = database.categoryQueries.getUserCategoryIds().awaitAsList()
            val ids = orderedIds.filter { it in current } + current.filterNot { it in orderedIds }
            ids.forEachIndexed { index, categoryId ->
                database.categoryQueries.updateOrder(order = -index - 2L, id = categoryId)
            }
            ids.forEachIndexed { index, categoryId ->
                database.categoryQueries.updateOrder(order = index.toLong(), id = categoryId)
            }
        }
    }

    override suspend fun delete(categoryId: Long) {
        database.categoryQueries.delete(id = categoryId)
    }
}
// KMK <--
