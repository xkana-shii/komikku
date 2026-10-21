package tachiyomi.domain.category.service

import tachiyomi.domain.category.model.Category

// KNS -->
/**
 * Pure operations on the category hierarchy, shared by the category manager, assignment dialogs,
 * Smart Categories, restore and the library. Categories form a forest: `parentId == 0` is a root.
 *
 * A category's identity is its id (and its sync uid), never its name: names only have to be unique
 * among siblings, so `Shounen / Reading` and `Josei / Reading` are distinct.
 *
 * The persisted `order` column is global and unique, so the tree is stored as its depth-first
 * order (every parent followed by its subtree); [flattened] produces that order.
 */
object CategoryTree {

    /** One comparison policy for names everywhere (dialogs, moves, merges, restore). */
    fun normalizeName(name: String): String = name.trim().lowercase()

    fun hasSiblingName(
        name: String,
        parentId: Long,
        categories: Iterable<Category>,
        excludingId: Long? = null,
    ): Boolean {
        val normalized = normalizeName(name)
        return categories.any {
            it.id != excludingId && it.parentId == parentId && normalizeName(it.name) == normalized
        }
    }

    fun childrenOf(parentId: Long, categories: Iterable<Category>): List<Category> =
        categories.filter { it.parentId == parentId && it.id != parentId }.sortedBy(Category::order)

    /** All categories below [categoryId], at any depth. */
    fun descendantIds(categoryId: Long, categories: Iterable<Category>): Set<Long> {
        val childrenByParent = categories.groupBy(Category::parentId)
        val result = mutableSetOf<Long>()
        val stack = ArrayDeque(listOf(categoryId))
        while (stack.isNotEmpty()) {
            childrenByParent[stack.removeLast()].orEmpty().forEach { child ->
                if (child.id != categoryId && result.add(child.id)) stack.addLast(child.id)
            }
        }
        return result
    }

    /** Parent, grandparent, … of [categoryId] (nearest first). Stops at roots, missing ids and cycles. */
    fun ancestorIds(categoryId: Long, byId: Map<Long, Category>): List<Long> {
        val result = mutableListOf<Long>()
        val seen = mutableSetOf(categoryId)
        var parentId = byId[categoryId]?.parentId ?: return result
        while (parentId != Category.UNCATEGORIZED_ID) {
            if (!seen.add(parentId)) break
            val parent = byId[parentId] ?: break
            result += parent.id
            parentId = parent.parentId
        }
        return result
    }

    /** [ids] plus every ancestor of them. */
    fun withAncestors(ids: Iterable<Long>, byId: Map<Long, Category>): Set<Long> =
        ids.flatMapTo(mutableSetOf()) { listOf(it) + ancestorIds(it, byId) }

    fun depth(categoryId: Long, byId: Map<Long, Category>): Int = ancestorIds(categoryId, byId).size

    /** The names from the root down to [category], for display ("Shounen / Reading") and search. */
    fun path(category: Category, byId: Map<Long, Category>): List<String> =
        ancestorIds(category.id, byId).asReversed().mapNotNull { byId[it]?.name } + category.name

    /**
     * Whether [categoryId] may become a child of [newParentId]: not the system category, not below
     * itself (no cycles) and the parent exists (0 = root).
     */
    fun canMove(categoryId: Long, newParentId: Long, categories: Iterable<Category>): Boolean {
        val list = categories.toList()
        if (categoryId == Category.UNCATEGORIZED_ID) return false
        if (list.none { it.id == categoryId }) return false
        if (newParentId == Category.UNCATEGORIZED_ID) return true
        if (newParentId == categoryId) return false
        if (list.none { it.id == newParentId }) return false
        return newParentId !in descendantIds(categoryId, list)
    }

    /**
     * Depth-first order of the user categories: siblings by their current `order`, each followed by
     * its subtree. Rows whose parent is missing or that sit in a cycle are kept (as roots, at the
     * end) instead of being dropped, so no category ever disappears from the order.
     */
    fun flattened(categories: Iterable<Category>): List<Category> {
        val user = categories.filterNot(Category::isSystemCategory)
        val childrenByParent = user.groupBy(Category::parentId)
            .mapValues { (_, children) -> children.sortedBy(Category::order) }
        val result = mutableListOf<Category>()
        val visited = mutableSetOf<Long>()
        fun visit(category: Category) {
            if (!visited.add(category.id)) return
            result += category
            childrenByParent[category.id].orEmpty().forEach(::visit)
        }
        childrenByParent[Category.UNCATEGORIZED_ID].orEmpty().forEach(::visit)
        // Orphans (missing parent) and cycle members, with whatever subtree is not placed yet.
        user.filter { it.id !in visited }
            .sortedBy(Category::order)
            .forEach(::visit)
        return result
    }

    /**
     * The persisted order after moving [categoryId] to position [newIndex] among [newParentId]'s
     * children (its subtree moves with it). Null if the move is invalid.
     */
    fun moved(
        categoryId: Long,
        newParentId: Long,
        newIndex: Int,
        categories: List<Category>,
    ): List<Category>? {
        if (!canMove(categoryId, newParentId, categories)) return null
        val moving = categories.first { it.id == categoryId }
        val others = categories.filter { it.id != categoryId }
        val siblings = others.filter { it.parentId == newParentId }.sortedBy(Category::order).toMutableList()
        siblings.add(newIndex.coerceIn(0, siblings.size), moving.copy(parentId = newParentId))
        // Re-rank the new siblings and keep everybody else's relative order.
        val siblingRank = siblings.withIndex().associate { (index, category) -> category.id to index.toLong() }
        val reordered = (others.filter { it.parentId != newParentId } + siblings).map { category ->
            siblingRank[category.id]?.let { category.copy(order = it) } ?: category
        }
        return flattened(reordered).mapIndexed { index, category -> category.copy(order = index.toLong()) }
    }

    /** True when following parents from any category loops back to itself. */
    fun hasCycles(categories: Iterable<Category>): Boolean {
        val byId = categories.associateBy(Category::id)
        return byId.values.any { category ->
            val seen = mutableSetOf(category.id)
            var parentId = category.parentId
            while (parentId != Category.UNCATEGORIZED_ID) {
                if (!seen.add(parentId)) return@any true
                parentId = byId[parentId]?.parentId ?: break
            }
            false
        }
    }
}
// KNS <--
