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
        return database.categoriesQueries.getCategory(id, CategoryMapper::mapCategory).awaitAsOneOrNull()
    }

    override suspend fun getAll(): List<Category> {
        return database.categoriesQueries.getCategories(CategoryMapper::mapCategory).awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return database.categoriesQueries.getCategories(CategoryMapper::mapCategory).subscribeToList()
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoriesQueries.getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory).awaitAsList()
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoriesQueries.getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory).subscribeToList()
    }

    // SY -->
    override suspend fun insert(category: Category): Long {
        return database.transactionWithResult {
            database.categoriesQueries.insertReturningId(
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
        database.categoriesQueries.updateName(name = name, categoryId = categoryId)
    }

    override suspend fun updateFlags(categoryId: Long, flags: Long) {
        database.categoriesQueries.updateFlags(flags = flags, categoryId = categoryId)
    }

    // KMK -->
    override suspend fun updateNameAndOrder(categoryId: Long, name: String, order: Long) {
        database.categoriesQueries.updateNameAndOrder(name = name, order = order, categoryId = categoryId)
    }

    override suspend fun updateHidden(categoryId: Long, hidden: Boolean) {
        database.categoriesQueries.updateHidden(hidden = if (hidden) 1L else 0L, categoryId = categoryId)
    }
    // KMK <--

    override suspend fun updateAllFlags(flags: Long?) {
        database.categoriesQueries.updateAllFlags(flags = flags)
    }

    override suspend fun updateAllOrders(orderedIds: List<Long>) {
        database.transaction {
            orderedIds.forEachIndexed { index, categoryId ->
                database.categoriesQueries.updateOrder(order = index.toLong(), categoryId = categoryId)
            }
        }
    }

    override suspend fun delete(categoryId: Long) {
        database.categoriesQueries.delete(
            categoryId = categoryId,
        )
    }
}
// KMK <--
