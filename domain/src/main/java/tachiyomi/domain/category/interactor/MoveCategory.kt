package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTree

// KNS -->
/**
 * Moves a category (with its whole subtree) below another parent and/or to another position among
 * its siblings. Cycles (moving a category below itself or a descendant) and duplicate sibling names
 * are rejected; content and memberships are untouched.
 */
@Inject
class MoveCategory(
    private val categoryRepository: CategoryRepository,
) {
    private val mutex = Mutex()

    suspend fun await(categoryId: Long, newParentId: Long, newIndex: Int): Result = withNonCancellableContext {
        mutex.withLock {
            val categories = categoryRepository.getAll()
            val category = categories.firstOrNull { it.id == categoryId } ?: return@withLock Result.InvalidMove
            if (!CategoryTree.canMove(categoryId, newParentId, categories)) return@withLock Result.InvalidMove
            if (category.parentId != newParentId &&
                CategoryTree.hasSiblingName(category.name, newParentId, categories, excludingId = categoryId)
            ) {
                return@withLock Result.DuplicateName
            }
            val moved = CategoryTree.moved(categoryId, newParentId, newIndex, categories)
                ?: return@withLock Result.InvalidMove
            val before = CategoryTree.flattened(categories).map { it.id to it.parentId }
            if (moved.map { it.id to it.parentId } == before) return@withLock Result.Unchanged
            try {
                categoryRepository.updateHierarchy(moved)
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
        data object InvalidMove : Result
        data object DuplicateName : Result
        data class InternalError(val error: Throwable) : Result
    }
}
// KNS <--
