package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.async.coroutines.awaitAsOne
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.Database
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTreeHandler
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

class CategoriesRestorer(
    private val database: Database = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>): Map<Long, Long> {
        if (backupCategories.isEmpty()) return emptyMap()

        return database.transactionWithResult {
            val dbCategories = getCategories.await()
            val restoredIds = mutableMapOf<Long, Long>()
            val currentCategories = dbCategories.toMutableList()
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val restoredCategories = backupCategories
                // KMK --> A two-level tree only needs roots before direct children.
                .sortedWith(compareBy<BackupCategory> { it.parentId != 0L }.thenBy { it.order })
                .mapNotNull { backupCategory ->
                    val name = backupCategory.name.trim()
                    if (name.isEmpty() || name.length > 50) return@mapNotNull null

                    val parentId = if (backupCategory.parentId == Category.UNCATEGORIZED_ID) {
                        Category.UNCATEGORIZED_ID
                    } else {
                        restoredIds[backupCategory.parentId] ?: return@mapNotNull null
                    }
                    val existing = currentCategories.firstOrNull {
                        it.parentId == parentId &&
                            CategoryTreeHandler.normalizeName(it.name) == CategoryTreeHandler.normalizeName(name)
                    }
                    if (existing != null) {
                        if (backupCategory.id != 0L) restoredIds[backupCategory.id] = existing.id
                        return@mapNotNull existing
                    }

                    val order = nextOrder++
                    val id = database.categoryQueries.insertReturningId(
                        name = name,
                        order = order,
                        flags = backupCategory.flags,
                        hidden = if (backupCategory.hidden) 1L else 0L,
                        parentId = parentId,
                        dateAdded = System.currentTimeMillis(),
                        dateModified = System.currentTimeMillis(),
                    ).awaitAsOne()
                    backupCategory.trackStatus?.let { status ->
                        database.categoryQueries.updateTrackStatus(status.toLong(), id, System.currentTimeMillis())
                    }
                    backupCategory.updateInterval?.let { interval ->
                        database.categoryQueries.updateUpdateInterval(interval.toLong(), id, System.currentTimeMillis())
                    }
                    val category = backupCategory.toCategory(id).copy(name = name, order = order, parentId = parentId)
                    currentCategories += category
                    if (backupCategory.id != 0L) restoredIds[backupCategory.id] = id
                    category
                }

            libraryPreferences.categorizedDisplaySettings().set(
                (dbCategories + restoredCategories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
            restoredIds
        }
    }
}
// KMK <--
