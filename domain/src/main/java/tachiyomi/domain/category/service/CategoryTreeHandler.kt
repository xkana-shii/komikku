package tachiyomi.domain.category.service

import tachiyomi.domain.category.model.Category

// KMK -->
/** Pure tree operations shared by category management, restore validation, and scheduling. */
object CategoryTreeHandler {

    fun childrenOf(parentId: Long, categories: Iterable<Category>): List<Category> =
        categories.filter { it.parentId == parentId }

    fun descendantsOf(parentId: Long, categories: Iterable<Category>): Set<Long> {
        val childrenByParent = categories.groupBy(Category::parentId)
        val descendants = mutableSetOf<Long>()
        fun visit(id: Long) {
            childrenByParent[id].orEmpty().forEach { child ->
                if (descendants.add(child.id)) visit(child.id)
            }
        }
        visit(parentId)
        return descendants
    }

    fun canMove(categoryId: Long, parentId: Long, categories: Iterable<Category>): Boolean =
        categoryId != Category.UNCATEGORIZED_ID &&
            parentId != categoryId &&
            parentId !in descendantsOf(categoryId, categories)

    fun hasCycles(categories: Iterable<Category>): Boolean {
        val byId = categories.associateBy(Category::id)
        return byId.values.any { category ->
            val visited = mutableSetOf<Long>()
            var current = category
            while (current.parentId != Category.UNCATEGORIZED_ID) {
                if (!visited.add(current.id)) return@any true
                current = byId[current.parentId] ?: break
            }
            false
        }
    }
}
// KMK <--
