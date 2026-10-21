package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTree
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteCategory(
    private val categoryRepository: CategoryRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
    // SY -->
    /** Called with the deleted category's sync uid, so sync can send a tombstone. */
    private val onCategoryDeleted: (uid: Long) -> Unit = {},
    // SY <--
) {

    suspend fun await(categoryId: Long): Result {
        try {
            // SY -->
            val uid = categoryRepository.get(categoryId)?.uid
            // SY <--
            // KNS -->
            // Child categories (and their manga) move up one level instead of being orphaned;
            // the deleted category's own memberships go with it (foreign key cascade).
            val categories = categoryRepository.getAll()
            val parentId = categories.firstOrNull { it.id == categoryId }?.parentId ?: Category.UNCATEGORIZED_ID
            val children = CategoryTree.childrenOf(categoryId, categories)
            if (children.isNotEmpty()) {
                val childIds = children.mapTo(mutableSetOf(), Category::id)
                val destinationNames = CategoryTree.childrenOf(parentId, categories)
                    .filterNot { it.id == categoryId || it.id in childIds }
                    .map { CategoryTree.normalizeName(it.name) }
                val childNames = children.map { CategoryTree.normalizeName(it.name) }
                if (childNames.size != childNames.distinct().size || childNames.any { it in destinationNames }) {
                    return Result.DuplicateName
                }
                categoryRepository.updateHierarchy(
                    CategoryTree.flattened(
                        categories.map { if (it.id in childIds) it.copy(parentId = parentId) else it },
                    ),
                )
            }
            // KNS <--
            categoryRepository.delete(categoryId)
            // SY -->
            if (uid != null) onCategoryDeleted(uid)
            // SY <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return Result.InternalError(e)
        }

        val orderedIds = categoryRepository.getAll()
            .filterNot(Category::isSystemCategory)
            .map { it.id }

        val defaultCategory = libraryPreferences.defaultCategory.get()
        if (defaultCategory == categoryId.toInt()) {
            libraryPreferences.defaultCategory.delete()
        }

        val categoryPreferences = listOf(
            libraryPreferences.updateCategories,
            libraryPreferences.updateCategoriesExclude,
            downloadPreferences.removeExcludeCategories,
            downloadPreferences.downloadNewChapterCategories,
            downloadPreferences.downloadNewChapterCategoriesExclude,
            // KMK -->
            libraryPreferences.filterCategoriesInclude,
            libraryPreferences.filterCategoriesExclude,
            // KMK <--
        )
        val categoryIdString = categoryId.toString()
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            if (categoryIdString !in ids) return@forEach
            preference.set(ids.minus(categoryIdString))
        }

        return try {
            categoryRepository.updateAllOrders(orderedIds = orderedIds)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result

        // KNS -->
        /** Moving the children up would put two same-named categories under one parent. */
        data object DuplicateName : Result
        // KNS <--
    }
}
