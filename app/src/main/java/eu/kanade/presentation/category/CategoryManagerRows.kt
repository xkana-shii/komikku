package eu.kanade.presentation.category

import androidx.compose.runtime.Immutable
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree

// KNS -->
/** Persisted values of the category manager's sort; append new modes instead of renumbering. */
object CategoryManagerSort {
    const val MANUAL = 0
    const val AZ = 1
    const val ZA = 2
    const val MOST_ITEMS = 3
    const val LEAST_ITEMS = 4
    const val DATE_ADDED_NEWEST = 5
    const val DATE_ADDED_OLDEST = 6
    const val DATE_MODIFIED_NEWEST = 7
    const val DATE_MODIFIED_OLDEST = 8
}

@Immutable
data class CategoryManagerRow(
    val category: Category,
    val depth: Int,
    val childCount: Int,
    val collapsed: Boolean,
    /** Distinct manga in this category and all of its subcategories. */
    val mangaCount: Int,
    /** "Shounen / Reading" when a search shows a subcategory out of its tree context; else null. */
    val path: String?,
) {
    val hasChildren: Boolean get() = childCount > 0
}

/**
 * The rows of the category manager: a depth-first walk where each level is ordered by [sortMode]
 * (manual = the persisted order). Collapsed categories hide their subtree. A search ignores
 * collapsing and shows every match together with its ancestors, so a match is always seen in its
 * hierarchy; matches below the top level also carry their full path.
 */
fun buildCategoryManagerRows(
    categories: List<Category>,
    searchQuery: String,
    collapsedIds: Set<Long>,
    sortMode: Int,
    mangaIdsByCategory: Map<Long, Set<Long>>,
): List<CategoryManagerRow> {
    val user = categories.filterNot(Category::isSystemCategory)
    val byId = user.associateBy(Category::id)
    val childrenByParent = user.groupBy(Category::parentId)
    val counts = subtreeMangaCounts(user, mangaIdsByCategory)

    val query = searchQuery.trim().lowercase()
    val matchIds = if (query.isEmpty()) {
        emptySet()
    } else {
        user.filter { it.name.lowercase().contains(query) }.mapTo(mutableSetOf(), Category::id)
    }
    val shown: Set<Long>? = if (query.isEmpty()) null else CategoryTree.withAncestors(matchIds, byId)

    val rows = mutableListOf<CategoryManagerRow>()
    val visited = mutableSetOf<Long>()
    fun visit(category: Category, depth: Int) {
        if (!visited.add(category.id)) return
        if (shown != null && category.id !in shown) return
        val children = sortCategories(childrenByParent[category.id].orEmpty(), sortMode, counts)
        val collapsed = shown == null && category.id in collapsedIds
        rows += CategoryManagerRow(
            category = category,
            depth = depth,
            childCount = children.size,
            collapsed = collapsed && children.isNotEmpty(),
            mangaCount = counts[category.id] ?: 0,
            path = if (category.id in matchIds && depth > 0) {
                CategoryTree.path(category, byId).joinToString(" / ")
            } else {
                null
            },
        )
        if (!collapsed) children.forEach { visit(it, depth + 1) }
    }
    sortCategories(childrenByParent[Category.UNCATEGORIZED_ID].orEmpty(), sortMode, counts).forEach { visit(it, 0) }
    // Orphans and cycle members (not reachable from any root, independent of collapsing and
    // searching) are shown as roots rather than hidden.
    val unreachable = user.filterNot { reachableFromRoot(it, byId) }
    sortCategories(unreachable, sortMode, counts).forEach { visit(it, 0) }
    return rows
}

/** Following the parents ends at the top level (no missing parent, no cycle). */
private fun reachableFromRoot(category: Category, byId: Map<Long, Category>): Boolean {
    val seen = mutableSetOf(category.id)
    var parentId = category.parentId
    while (parentId != Category.UNCATEGORIZED_ID) {
        if (!seen.add(parentId)) return false
        parentId = byId[parentId]?.parentId ?: return false
    }
    return true
}

private fun subtreeMangaCounts(categories: List<Category>, mangaIdsByCategory: Map<Long, Set<Long>>): Map<Long, Int> {
    return categories.associate { category ->
        val ids = CategoryTree.descendantIds(category.id, categories) + category.id
        category.id to ids.flatMapTo(mutableSetOf()) { mangaIdsByCategory[it].orEmpty() }.size
    }
}

private fun sortCategories(categories: List<Category>, sortMode: Int, counts: Map<Long, Int>): List<Category> =
    when (sortMode) {
        CategoryManagerSort.AZ -> categories.sortedBy { it.name.lowercase() }
        CategoryManagerSort.ZA -> categories.sortedByDescending { it.name.lowercase() }
        CategoryManagerSort.MOST_ITEMS -> categories.sortedByDescending { counts[it.id] ?: 0 }
        CategoryManagerSort.LEAST_ITEMS -> categories.sortedBy { counts[it.id] ?: 0 }
        CategoryManagerSort.DATE_ADDED_NEWEST -> categories.sortedByDescending(Category::dateAdded)
        CategoryManagerSort.DATE_ADDED_OLDEST -> categories.sortedBy(Category::dateAdded)
        CategoryManagerSort.DATE_MODIFIED_NEWEST -> categories.sortedByDescending(Category::dateModified)
        CategoryManagerSort.DATE_MODIFIED_OLDEST -> categories.sortedBy(Category::dateModified)
        else -> categories.sortedBy(Category::order)
    }

/**
 * Where a row dragged within the manual order lands: drag-and-drop only reorders among siblings
 * (moving to another parent is the explicit Move action), so the new position is the number of the
 * moved category's siblings that end up above it.
 */
fun siblingIndexAfterDrop(rows: List<CategoryManagerRow>, movedId: Long): Int? {
    val moved = rows.firstOrNull { it.category.id == movedId } ?: return null
    return rows.takeWhile { it.category.id != movedId }
        .count { it.category.parentId == moved.category.parentId }
}
// KNS <--
