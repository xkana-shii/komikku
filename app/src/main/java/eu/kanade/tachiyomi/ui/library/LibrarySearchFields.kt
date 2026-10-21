package eu.kanade.tachiyomi.ui.library

import mihon.domain.library.model.search.LibraryField
import mihon.domain.library.model.search.LibraryFieldQueryNode
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree
import tachiyomi.domain.track.model.Track

// KNS -->
/**
 * The values `category:`, `tracker:` and `status:` library search terms are matched against.
 *
 * - `category:` matches the entry's categories and their parents by name or id, so `category:Shounen`
 *   also finds manga filed in `Shounen / Reading`; an entry without categories is in the default one.
 * - `tracker:` matches the name of a tracker the entry is tracked with and its tracking status there.
 * - `status:` matches the publishing status by its code or its displayed name.
 */
class LibrarySearchFields(
    categories: List<Category>,
    private val defaultCategoryName: String,
    private val statusName: (Long) -> String?,
    private val trackerValues: (Track) -> List<String>,
) {
    private val categoriesById = categories.associateBy(Category::id)

    fun matches(node: LibraryFieldQueryNode, item: LibraryItem, tracks: List<Track>): Boolean = when (node.field) {
        LibraryField.CATEGORY -> {
            val assigned = item.libraryManga.categories.filterNot { it == Category.UNCATEGORIZED_ID }
            if (assigned.isEmpty()) {
                node.matches(listOf(Category.UNCATEGORIZED_ID), listOf(defaultCategoryName))
            } else {
                val ids = CategoryTree.withAncestors(assigned, categoriesById)
                node.matches(ids, ids.mapNotNull { categoriesById[it]?.name })
            }
        }
        LibraryField.TRACKER -> node.matches(emptyList(), tracks.flatMap(trackerValues))
        LibraryField.STATUS -> {
            val status = item.libraryManga.manga.status
            node.matches(listOf(status), listOfNotNull(statusName(status)))
        }
    }
}
// KNS <--
