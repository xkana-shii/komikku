package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupSmartCategory
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.smartCategory.interactor.GetSmartCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getSmartCategory: GetSmartCategory = Injekt.get(),
) {

    suspend operator fun invoke(
        backupCategories: List<BackupCategory>,
        // SY -->
        backupSmartCategories: List<BackupSmartCategory>?,
        // SY <--
    ) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val categories = backupCategories
                .sortedBy { it.order }
                .map {
                    val dbCategory = dbCategoriesByName[it.name]
                    if (dbCategory != null) return@map dbCategory
                    val order = nextOrder++
                    handler.awaitOneExecutable {
                        categoriesQueries.insert(
                            it.name,
                            order,
                            it.flags,
                            // KMK -->
                            hidden = if (it.hidden) 1L else 0L,
                            // KMK <--
                        )
                        categoriesQueries.selectLastInsertedRowId()
                    }
                        .let { id -> it.toCategory(id).copy(order = order) }
                }

            libraryPreferences.categorizedDisplaySettings().set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
            // SY -->
            if (!backupSmartCategories.isNullOrEmpty()) {
                val categoriesMap = categories.associateBy { it.name }
                val dbSmartCategoriesMap = getSmartCategory.await().associateBy { it.categoryName }

                backupSmartCategories
                    .filter { it.categoryName in categoriesMap }
                    .forEach { smartCategory ->
                        categoriesMap[smartCategory.categoryName]?.let { category ->
                            val mergedTags = dbSmartCategoriesMap[smartCategory.categoryName]?.let { dbSmartCategory ->
                                (dbSmartCategory.tags + smartCategory.tags).toSet().toList()
                            } ?: smartCategory.tags

                            handler.await {
                                smart_categoryQueries.upsert(category.id, mergedTags)
                            }
                        }
                    }
            }
            // SY <--
        }
    }
}
