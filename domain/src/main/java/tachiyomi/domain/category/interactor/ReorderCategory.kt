package tachiyomi.domain.category.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTreeHandler

class ReorderCategory(
    private val categoryRepository: CategoryRepository,
) {
    private val mutex = Mutex()

    suspend fun await(category: Category, newIndex: Int) = withNonCancellableContext {
        mutex.withLock {
            // KMK -->
            val categories = categoryRepository.getAll()
                .filterNot(Category::isSystemCategory)
            val orderedIds = CategoryTreeHandler.reorderedIds(
                categoryId = category.id,
                newParentId = category.parentId,
                newIndex = newIndex,
                categories = categories,
            )
            if (orderedIds == null) {
                return@withNonCancellableContext Result.Unchanged
            }
            // KMK <--

            try {
                // KMK -->
                categoryRepository.updateAllOrders(orderedIds = orderedIds)
                // KMK <--
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
