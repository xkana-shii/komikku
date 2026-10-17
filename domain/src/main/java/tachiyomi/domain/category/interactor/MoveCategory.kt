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
        // KMK --> Moving into a parent must preserve the sibling uniqueness invariant.
        val category = categories.firstOrNull { it.id == categoryId } ?: return false
        if (CategoryTreeHandler.hasSiblingName(category.name, parentId, categories, categoryId)) return false
        // KMK <--
        val index = newIndex ?: categories.count { it.parentId == parentId }
        val orderedIds = CategoryTreeHandler.reorderedIds(categoryId, parentId, index, categories) ?: return false
        categoryRepository.updateParent(categoryId, parentId)
        categoryRepository.updateAllOrders(orderedIds)
        return true
    }

    // KMK --> Validate a bulk reparent before mutating any category so two selected siblings
    // cannot be moved into a duplicate-name collision one at a time.
    suspend fun awaitAll(categoryIds: List<Long>, parentId: Long): Boolean {
        val ids = categoryIds.distinct()
        if (ids.isEmpty()) return true

        val categories = categoryRepository.getAll()
        val moving = categories.filter { it.id in ids }
        if (moving.size != ids.size || ids.any { it == parentId }) return false
        if (moving.any { !CategoryTreeHandler.canMove(it.id, parentId, categories) }) return false

        val destinationNames = categories
            .filter { it.parentId == parentId && it.id !in ids }
            .map { CategoryTreeHandler.normalizeName(it.name) }
            .toSet()
        val movingNames = moving.map { CategoryTreeHandler.normalizeName(it.name) }
        if (movingNames.size != movingNames.distinct().size || movingNames.any { it in destinationNames }) return false

        var nextIndex = categories.count { it.parentId == parentId && it.id !in ids }
        return ids.all { categoryId -> await(categoryId, parentId, nextIndex++) }
    }
    // KMK <--
    // KMK <--
}
// KMK <--
