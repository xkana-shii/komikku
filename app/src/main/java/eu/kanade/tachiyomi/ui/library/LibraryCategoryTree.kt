package eu.kanade.tachiyomi.ui.library

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree

// KNS -->
/**
 * Library tabs for nested categories: one tab per top-level category (a category whose parent is
 * missing counts as top level) holding the items of its whole subtree, each item once. Items whose
 * only categories are hidden (directly or through a hidden ancestor) are left out unless hidden
 * categories are shown. The system category is a tab only when some item has no category.
 *
 * [memberships] are (item id, category ids) pairs in display order.
 */
fun groupByCategoryTree(
    memberships: List<Pair<Long, List<Long>>>,
    categories: List<Category>,
    showHiddenCategories: Boolean,
): Map<Category, List<Long>> {
    val byId = categories.associateBy(Category::id)
    fun isTopLevel(category: Category) =
        category.parentId == Category.UNCATEGORIZED_ID || category.parentId !in byId

    val tabOf = mutableMapOf<Long, Long?>()
    fun visibleTab(categoryId: Long): Long? = tabOf.getOrPut(categoryId) {
        if (categoryId == Category.UNCATEGORIZED_ID) return@getOrPut Category.UNCATEGORIZED_ID
        val category = byId[categoryId] ?: return@getOrPut null
        val chain = listOf(category) + CategoryTree.ancestorIds(categoryId, byId).mapNotNull(byId::get)
        if (!showHiddenCategories && chain.any(Category::hidden)) return@getOrPut null
        chain.firstOrNull(::isTopLevel)?.id
    }

    val groups = mutableMapOf<Long, LinkedHashSet<Long>>()
    memberships.forEach { (itemId, categoryIds) ->
        categoryIds.forEach { categoryId ->
            visibleTab(categoryId)?.let { tab -> groups.getOrPut(tab) { linkedSetOf() } += itemId }
        }
    }
    val showSystemCategory = groups.containsKey(Category.UNCATEGORIZED_ID)
    return categories
        .filter { category ->
            if (category.isSystemCategory) {
                showSystemCategory
            } else {
                isTopLevel(category) && (showHiddenCategories || !category.hidden)
            }
        }
        .associateWith { groups[it.id]?.toList().orEmpty() }
}
// KNS <--
