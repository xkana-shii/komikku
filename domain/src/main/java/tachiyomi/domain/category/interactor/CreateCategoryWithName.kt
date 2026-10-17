package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTreeHandler
import tachiyomi.domain.library.service.LibraryPreferences

class CreateCategoryWithName(
    private val categoryRepository: CategoryRepository,
    private val preferences: LibraryPreferences,
) {

    private val initialFlags: Long
        get() {
            val sort = preferences.sortingMode().get()
            return sort.type.flag or sort.direction.flag
        }

    suspend fun await(name: String, parentId: Long = Category.UNCATEGORIZED_ID): Result = withNonCancellableContext {
        // KMK --> Categories only need unique names among siblings.
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || normalizedName.length > 50) {
            return@withNonCancellableContext Result.InternalError(IllegalArgumentException("Invalid category name"))
        }
        val categories = categoryRepository.getAll()
        if (parentId != Category.UNCATEGORIZED_ID) {
            val parent = categories.firstOrNull { it.id == parentId }
                ?: return@withNonCancellableContext Result.InternalError(IllegalArgumentException("Missing category parent"))
            if (parent.parentId != Category.UNCATEGORIZED_ID) {
                return@withNonCancellableContext Result.InternalError(IllegalArgumentException("Nested subcategories are not supported"))
            }
        }
        if (CategoryTreeHandler.hasSiblingName(normalizedName, parentId, categories)) {
            return@withNonCancellableContext Result.InternalError(IllegalArgumentException("Duplicate category name"))
        }
        val nextOrder = categories.maxOfOrNull { it.order }?.plus(1) ?: 0
        val newCategory = Category(
            id = 0,
            name = normalizedName,
            order = nextOrder,
            flags = initialFlags,
            // KMK -->
            hidden = false,
            parentId = parentId,
            // KMK <--
        )

        try {
            categoryRepository.insert(newCategory)
            Result.Success(/* SY --> */newCategory/* SY <-- */)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        // SY -->
        data class Success(val category: Category) : Result

        // SY <--
        data class InternalError(val error: Throwable) : Result
    }
}
