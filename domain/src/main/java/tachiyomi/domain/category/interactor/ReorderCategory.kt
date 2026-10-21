package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTree

@Inject
class ReorderCategory(
    private val categoryRepository: CategoryRepository,
) {
    private val mutex = Mutex()

    // KNS -->
    /** Moves [category] to [newIndex] among its siblings (same parent); its subtree moves with it. */
    // KNS <--
    suspend fun await(category: Category, newIndex: Int) = mutex.withLock {
        val categories = categoryRepository.getAll()
            .filterNot(Category::isSystemCategory)
            .toMutableList()

        val currentIndex = categories.indexOfFirst { it.id == category.id }
        if (currentIndex == -1) {
            return@withLock Result.Unchanged
        }

        try {
            // KNS -->
            val parentId = categories[currentIndex].parentId
            val reordered = CategoryTree.moved(category.id, parentId, newIndex, categories)
                ?: return@withLock Result.Unchanged
            categoryRepository.updateHierarchy(reordered)
            // KNS <--
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
