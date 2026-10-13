package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.async.coroutines.awaitAsOne
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.Database
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

class CategoriesRestorer(
    private val database: Database = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            database.transaction {
                val dbCategories = getCategories.await()
                val dbCategoriesByName = dbCategories.associateBy { it.name }
                // KMK --> Backup ids are device-local. Keep an explicit mapping while roots are
                // restored before their children so membership and parent links never rely on a
                // matching database id.
                val restoredIds = mutableMapOf<Long, Long>()
                // KMK <--
                var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

                val categories = backupCategories
                    // KMK --> Parents must exist before a child can safely receive its parent id.
                    .sortedWith(compareBy<BackupCategory> { it.parentId != 0L }.thenBy { it.order })
                    // KMK <--
                    .map {
                        val dbCategory = dbCategoriesByName[it.name]
                        if (dbCategory != null) {
                            if (it.id != 0L) restoredIds[it.id] = dbCategory.id
                            return@map dbCategory
                        }
                        val order = nextOrder++
                        // KMK --> An invalid/missing parent falls back to the root. This retains
                        // the category and its manga membership instead of creating an orphan.
                        val parentId = restoredIds[it.parentId] ?: 0L
                        // KMK <--
                        database.categoryQueries.insertReturningId(
                            it.name,
                            order,
                            it.flags,
                            // KMK -->
                            hidden = if (it.hidden) 1L else 0L,
                            parentId = parentId,
                            dateAdded = System.currentTimeMillis(),
                            dateModified = System.currentTimeMillis(),
                            // KMK <--
                        ).awaitAsOne()
                            .let { id ->
                                if (it.id != 0L) restoredIds[it.id] = id
                                // KMK --> These columns use NULL as a meaningful value, so only
                                // write a backup's explicit override.
                                it.trackStatus?.let { status -> database.categoryQueries.updateTrackStatus(status.toLong(), id, System.currentTimeMillis()) }
                                it.updateInterval?.let { interval -> database.categoryQueries.updateUpdateInterval(interval.toLong(), id, System.currentTimeMillis()) }
                                it.toCategory(id).copy(order = order, parentId = parentId)
                                // KMK <--
                            }
                    }

                libraryPreferences.categorizedDisplaySettings().set(
                    (dbCategories + categories)
                        .distinctBy { it.flags }
                        .size > 1,
                )
            }
        }
    }
}
// KMK <--
