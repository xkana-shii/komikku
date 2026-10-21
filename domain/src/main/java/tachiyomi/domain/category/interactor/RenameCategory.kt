package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTree

@Inject
class RenameCategory(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long, name: String): Result {
        // KNS -->
        val categories = categoryRepository.getAll()
        val parentId = categories.firstOrNull { it.id == categoryId }?.parentId ?: Category.UNCATEGORIZED_ID
        if (CategoryTree.hasSiblingName(name, parentId, categories, excludingId = categoryId)) {
            return Result.DuplicateName
        }
        // KNS <--
        return try {
            categoryRepository.updateName(categoryId = categoryId, name = name)
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

        // KNS -->
        data object DuplicateName : Result
        // KNS <--
    }
}
