package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTreeHandler

class RenameCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long, name: String) = withNonCancellableContext {
        try {
            // KMK --> A rename can reuse a name in a different branch but never among siblings.
            val categories = categoryRepository.getAll()
            val category = categories.firstOrNull { it.id == categoryId }
                ?: return@withNonCancellableContext Result.InternalError(IllegalArgumentException("Missing category"))
            val normalizedName = name.trim()
            if (normalizedName.isEmpty() || normalizedName.length > 50 ||
                CategoryTreeHandler.hasSiblingName(normalizedName, category.parentId, categories, categoryId)
            ) {
                return@withNonCancellableContext Result.InternalError(IllegalArgumentException("Invalid category name"))
            }
            categoryRepository.updateName(categoryId = categoryId, name = normalizedName)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun await(category: Category, name: String) = await(category.id, name)

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
