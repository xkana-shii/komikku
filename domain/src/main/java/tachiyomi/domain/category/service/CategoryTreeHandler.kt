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

    // KMK -->
    fun canMove(categoryId: Long, parentId: Long, categories: Iterable<Category>): Boolean =
        categories.toList().let { categoryList ->
            val category = categoryList.find { it.id == categoryId }
                ?: return@let false
            val parent = categoryList.find { it.id == parentId }
            categoryId != Category.UNCATEGORIZED_ID &&
                parentId != categoryId &&
                parentId !in descendantsOf(categoryId, categoryList) &&
                (parentId == Category.UNCATEGORIZED_ID || parent?.parentId == Category.UNCATEGORIZED_ID) &&
                (parentId == Category.UNCATEGORIZED_ID || childrenOf(category.id, categoryList).isEmpty())
        }

    /**
     * Returns the complete persisted order after moving [categoryId] among [newParentId]'s
     * children. The result keeps every parent followed by its children so the single global
     * SQLDelight order remains a stable representation of the two-level tree.
     */
    fun reorderedIds(
        categoryId: Long,
        newParentId: Long,
        newIndex: Int,
        categories: List<Category>,
    ): List<Long>? {
        if (!canMove(categoryId, newParentId, categories)) return null

        val moved = categories.first { it.id == categoryId }.copy(parentId = newParentId)
        val updated = categories.map { if (it.id == categoryId) moved else it }
        val roots = updated.filter { it.parentId == Category.UNCATEGORIZED_ID && it.id != categoryId }
            .sortedBy(Category::order)
            .toMutableList()
        val childrenByParent = updated
            .filter { it.parentId != Category.UNCATEGORIZED_ID && it.id != categoryId }
            .groupBy(Category::parentId)
            .mapValues { (_, children) -> children.sortedBy(Category::order).toMutableList() }
            .toMutableMap()

        if (newParentId == Category.UNCATEGORIZED_ID) {
            roots.add(newIndex.coerceIn(0, roots.size), moved)
        } else {
            val siblings = childrenByParent.getOrPut(newParentId) { mutableListOf() }
            siblings.add(newIndex.coerceIn(0, siblings.size), moved)
        }

        return buildList {
            roots.forEach { root ->
                add(root.id)
                childrenByParent[root.id].orEmpty().forEach { add(it.id) }
            }
            // Preserve malformed/orphaned legacy rows rather than dropping them from the order.
            updated.filter { category -> category.id !in this }.sortedBy(Category::order).forEach { add(it.id) }
        }
    }
    // KMK <--

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
