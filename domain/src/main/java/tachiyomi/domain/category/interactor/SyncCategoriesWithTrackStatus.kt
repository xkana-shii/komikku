package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

// KMK -->
/** Applies only explicitly bound status categories; ordinary categories remain untouched. */
class SyncCategoriesWithTrackStatus(
    private val categoryRepository: CategoryRepository,
    private val setMangaCategories: SetMangaCategories,
) {
    suspend fun await(mangaId: Long, trackStatus: Int?) {
        if (mangaId <= 0L) return
        val categories = categoryRepository.getAll()
        if (categories.none { it.trackStatus != null }) return

        val current = categoryRepository.getCategoriesByMangaId(mangaId).mapTo(mutableSetOf()) { it.id }
        val updated = updatedMembership(current, categories, trackStatus)
        if (updated != current) setMangaCategories.await(mangaId, updated.toList())
    }

    companion object {
        fun updatedMembership(
            current: Set<Long>,
            categories: Collection<Category>,
            trackStatus: Int?,
        ): Set<Long> {
            val boundCategories = categories.filter { it.trackStatus != null }
            if (boundCategories.isEmpty()) return current

            val categoriesById = categories.associateBy(Category::id)
            val activeRoots = current.mapNotNull { categoryId ->
                categoryId.rootCategoryId(categoriesById)
            }.toSet()
            val managedChildren = boundCategories.filter { category ->
                category.parentId != Category.UNCATEGORIZED_ID &&
                    category.rootCategoryId(categoriesById) in activeRoots
            }
            val legacyRootCategories = boundCategories.filter {
                it.parentId == Category.UNCATEGORIZED_ID
            }

            return current.toMutableSet().apply {
                // Root bindings predate hierarchical categories and remain global for compatibility.
                // Their children are never implied or touched by this legacy path.
                (legacyRootCategories + managedChildren).forEach { category ->
                    if (category.trackStatus == trackStatus) add(category.id) else remove(category.id)
                }
            }
        }

        private fun Long.rootCategoryId(categoriesById: Map<Long, Category>): Long? {
            var category = categoriesById[this] ?: return null
            val visited = mutableSetOf<Long>()
            while (category.parentId != Category.UNCATEGORIZED_ID) {
                if (!visited.add(category.id)) return null
                category = categoriesById[category.parentId] ?: return null
            }
            return category.id
        }
    }
}
// KMK <--
