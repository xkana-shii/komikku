package tachiyomi.data.category

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CategoryRepositoryImpl(
    private val database: Database,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return database.categoryQueries
            .getCategory(id, CategoryMapper::mapCategory)
            .awaitAsOneOrNull()
    }

    override suspend fun getAll(): List<Category> {
        return database.categoryQueries
            .getCategories(CategoryMapper::mapCategory)
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return database.categoryQueries
            .getCategories(CategoryMapper::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return database.categoryQueries
            .getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory)
            .awaitAsList()
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return database.categoryQueries
            .getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByMangaIds(mangaIds: List<Long>): Map<Long, List<Category>> {
        return database.categoryQueries
            .getCategoriesByMangaIds(mangaIds) { mangaId, id, name, order, flags, hidden, version, uid, lastModifiedAt ->
                mangaId to CategoryMapper.mapCategory(id, name, order, flags, hidden, version, uid, lastModifiedAt)
            }
            .awaitAsList()
            .groupBy({ it.first }, { it.second })
    }

    // SY -->
    override suspend fun insert(category: Category): Long? {
        // KUN -->
        val uid = if (category.uid != 0L) category.uid else Category.nameToUid(category.name)
        // KUN <--
        return database.transactionWithResult {
            database.categoryQueries.insertWithNextOrder(
                name = category.name,
                flags = category.flags,
                // KMK -->
                hidden = if (category.hidden) 1L else 0L,
                // KMK <--
                // SY -->
                version = category.version,
                // KUN -->
                uid = uid,
                // KUN <--
                lastModifiedAt = category.lastModifiedAt,
                isSyncing = 0L,
                // SY <--
            ).awaitAsOneOrNull()
        }
    }

    override suspend fun restoreCategories(categories: List<Category>, isSync: Boolean): List<Category> {
        val dbCategories = database.categoryQueries
            .getCategories(CategoryMapper::mapCategory)
            .awaitAsList()
        // KUN -->
        val dbCategoriesByName = dbCategories.associateBy { it.name.trim().lowercase() }
        val dbCategoriesByUid = dbCategories.filter { it.uid != 0L }.associateBy { it.uid } // Map by UID

        // Deduplicate incoming categories by normalized name and ensure deterministic UIDs
        val deduplicatedCategories = categories
            .map { cat ->
                val uid = if (cat.uid != 0L) cat.uid else Category.nameToUid(cat.name)
                cat.copy(uid = uid)
            }
            .distinctBy { it.name.trim().lowercase() }

        // `order` is UNIQUE, so a reorder would trip it mid-swap. Free the positions of every category
        // we are about to reorder first, then assign the server's positions.
        val reordered = if (isSync) {
            deduplicatedCategories.mapNotNull { category ->
                (if (category.uid != 0L) dbCategoriesByUid[category.uid] else null)
                    ?: dbCategoriesByName[category.name.trim().lowercase()]
            }.distinctBy { it.id }
        } else {
            emptyList()
        }
        if (reordered.isNotEmpty()) {
            database.transaction {
                reordered.forEachIndexed { index, db ->
                    database.categoryQueries.update(
                        name = db.name,
                        order = -(index + 1L) - 1_000_000L,
                        flags = db.flags,
                        hidden = if (db.hidden) 1L else 0L,
                        version = db.version,
                        uid = db.uid,
                        lastModifiedAt = db.lastModifiedAt,
                        isSyncing = 1L,
                        categoryId = db.id,
                    )
                }
            }
        }
        val reorderedIds = reordered.mapTo(mutableSetOf()) { it.id }
        val usedOrders = dbCategories
            .filterNot { it.id in reorderedIds }
            .mapTo(mutableSetOf()) { it.order }
        var nextOrder = (dbCategories.maxOfOrNull { it.order } ?: -1L) + 1
        fun freeOrder(): Long {
            while (nextOrder in usedOrders) nextOrder++
            return nextOrder++
        }
        // KUN <--
        // SY <--

        val restored = deduplicatedCategories
            .sortedBy { it.order }
            .map { category ->
                val normalizedName = category.name.trim().lowercase()
                var dbCategory = if (category.uid != 0L) {
                    dbCategoriesByUid[category.uid]
                } else {
                    null
                }

                if (dbCategory == null) {
                    dbCategory = dbCategoriesByName[normalizedName]
                }

                if (dbCategory != null) {
                    // KUN -->
                    // A user-initiated (non-sync) restore only inserts new categories and leaves
                    // existing ones untouched, matching master's CategoriesRestorer; updating them
                    // would rewrite order, flags and the sync counters (`version`/`last_modified_at`).
                    // The sync path still converges order and counters below.
                    if (!isSync) return@map dbCategory
                    val order = if (category.order !in usedOrders) category.order else freeOrder()
                    usedOrders.add(order)
                    val canonicalUid = if (category.uid != 0L) category.uid else dbCategory.uid
                    // KUN <--
                    database.categoryQueries.update(
                        name = category.name,
                        order = order,
                        flags = category.flags,
                        // KMK -->
                        // `hidden = coalesce(:hidden, hidden)`, so null would keep the stored value and
                        // a sync restore could never un-hide a category. Pass 0 like the insert path.
                        hidden = if (category.hidden) 1L else 0L,
                        // KMK <--
                        version = category.version,
                        // KUN -->
                        uid = canonicalUid,
                        // KUN <--
                        lastModifiedAt = category.lastModifiedAt,
                        isSyncing = 1,
                        categoryId = dbCategory.id,
                    )
                    // KUN -->
                    return@map dbCategory.copy(order = order, uid = canonicalUid)
                    // KUN <--
                }

                // A sync delta is a converging replica, not an import: new
                // categories must land at the position the server holds or
                // reorders never converge across devices.
                // KUN -->
                val order = if (isSync && category.order !in usedOrders) category.order else freeOrder()
                usedOrders.add(order)
                // KUN <--
                database.categoryQueries.insert(
                    name = category.name,
                    order = order,
                    flags = category.flags,
                    // KMK -->
                    hidden = if (category.hidden) 1L else 0L,
                    // KMK <--
                    version = category.version,
                    uid = category.uid,
                    lastModifiedAt = category.lastModifiedAt,
                    // SY --> a sync restore must not stamp last_modified_at, or the next delta echoes it back
                    isSyncing = if (isSync) 1L else 0L,
                    // SY <--
                ).awaitAsOne()
                    .let { id -> category.copy(id = id, order = order) }
            }

        database.categoryQueries.resetIsSyncing()

        return restored
    }
    // SY <--

    override suspend fun updateName(categoryId: Long, name: String) {
        database.categoryQueries.updateName(name = name, categoryId = categoryId)
    }

    override suspend fun updateFlags(categoryId: Long, flags: Long) {
        database.categoryQueries.updateFlags(flags = flags, categoryId = categoryId)
    }

    override suspend fun updateAllFlags(flags: Long?) {
        database.categoryQueries.updateAllFlags(flags)
    }

    override suspend fun updateAllOrders(orderedIds: List<Long>) {
        database.transaction {
            val current = database.categoryQueries.getUserCategoryIds().awaitAsList()
            val ids = orderedIds.filter { it in current } + current.filterNot { it in orderedIds }
            ids.forEachIndexed { index, categoryId ->
                database.categoryQueries.updateOrder(order = -index - 2L, categoryId = categoryId)
            }
            ids.forEachIndexed { index, categoryId ->
                database.categoryQueries.updateOrder(order = index.toLong(), categoryId = categoryId)
            }
        }
    }

    override suspend fun updatePartial(update: CategoryUpdate) {
        database.updatePartialBlocking(update)
    }

    override suspend fun updatePartial(updates: List<CategoryUpdate>) {
        database.transaction {
            updates.forEach { database.updatePartialBlocking(it) }
        }
    }

    private suspend fun Database.updatePartialBlocking(update: CategoryUpdate) {
        categoryQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            // KMK -->
            hidden = update.hidden?.let { if (it) 1L else 0L },
            // KMK <--
            // SY -->
            version = update.version,
            uid = update.uid,
            lastModifiedAt = update.lastModifiedAt,
            isSyncing = null,
            // SY <--
            categoryId = update.id,
        )
    }

    override suspend fun delete(categoryId: Long) {
        database.categoryQueries.delete(categoryId = categoryId)
    }
}
