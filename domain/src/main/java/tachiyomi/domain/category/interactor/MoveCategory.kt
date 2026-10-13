package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTreeHandler

// KMK -->
class MoveCategory(
    private val categoryRepository: CategoryRepository,
) {
    suspend fun await(categoryId: Long, parentId: Long): Boolean {
        val categories = categoryRepository.getAll()
        if (!CategoryTreeHandler.canMove(categoryId, parentId, categories)) return false
        if (parentId != 0L && categories.none { it.id == parentId }) return false
        categoryRepository.updateParent(categoryId, parentId)
        return true
    }
}
// KMK <--
