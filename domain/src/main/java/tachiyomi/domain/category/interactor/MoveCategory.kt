package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTreeHandler

// KMK -->
class MoveCategory(
    private val categoryRepository: CategoryRepository,
) {
    // KMK -->
    suspend fun await(categoryId: Long, parentId: Long, newIndex: Int? = null): Boolean {
        val categories = categoryRepository.getAll()
        if (!CategoryTreeHandler.canMove(categoryId, parentId, categories)) return false
        if (parentId != 0L && categories.none { it.id == parentId }) return false
        val index = newIndex ?: categories.count { it.parentId == parentId }
        val orderedIds = CategoryTreeHandler.reorderedIds(categoryId, parentId, index, categories) ?: return false
        categoryRepository.updateParent(categoryId, parentId)
        categoryRepository.updateAllOrders(orderedIds)
        return true
    }
    // KMK <--
}
// KMK <--
