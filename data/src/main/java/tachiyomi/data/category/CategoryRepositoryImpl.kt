package tachiyomi.data.category

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
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

    override suspend fun updatePartial(update: CategoryUpdate) {
        updatePartialBlocking(update)
    }

    override suspend fun updatePartial(updates: List<CategoryUpdate>) {
        database.transaction {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private suspend fun updatePartialBlocking(update: CategoryUpdate) {
        database.categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            // KMK -->
            hidden = update.hidden?.let { if (it) 1L else 0L },
            // KMK <--
            categoryId = update.id,
        )
    }

    override suspend fun updateAllFlags(flags: Long?) {
        database.categoriesQueries.updateAllFlags(flags)
    }

    override suspend fun delete(categoryId: Long) {
        database.categoriesQueries.delete(
            categoryId = categoryId,
        )
    }
}
// KMK <--
