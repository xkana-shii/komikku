package eu.kanade.tachiyomi.ui.library

import tachiyomi.domain.category.model.Category

// KMK -->
/** Precomputed category views consumed directly by Compose during pager transitions. */
internal data class LibraryCategoryCache<T>(
    val itemsByCategoryId: Map<Long, List<T>>,
    val itemsBySubcategoryId: Map<Long, List<T>>,
    val countsByCategoryId: Map<Long, Int>,
)

internal fun <T> buildLibraryCategoryCache(
    groupedIds: Map<Category, List<Long>>,
    directSubcategoryIds: Map<Long, List<Long>>,
    categories: List<Category>,
    itemsById: Map<Long, T>,
): LibraryCategoryCache<T> {
    val categoryIds = groupedIds.mapKeys { (category, _) -> category.id }
    val itemsByCategory = categoryIds.mapValues { (_, ids) -> ids.mapNotNull(itemsById::get) }
    val categoryById = categories.associateBy(Category::id)
    val itemsBySubcategory = directSubcategoryIds.mapValues { (subcategoryId, directIds) ->
        val parentId = categoryById[subcategoryId]?.parentId
        val directSet = directIds.toHashSet()
        categoryIds[parentId].orEmpty()
            .filter(directSet::contains)
            .mapNotNull(itemsById::get)
    }
    return LibraryCategoryCache(
        itemsByCategoryId = itemsByCategory,
        itemsBySubcategoryId = itemsBySubcategory,
        countsByCategoryId = itemsByCategory.mapValues { (_, items) -> items.size },
    )
}
// KMK <--
