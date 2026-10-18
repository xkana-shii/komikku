
package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

// KMK -->
/**
 * Synchronizes tracker-bound categories without moving manga into
 * subcategories belonging to unrelated parent categories.
 *
 * Root-level tracker bindings retain their legacy global behavior.
 * Tracker-bound subcategories are managed only under parent branches
 * the manga already belongs to.
 *
 * Existing child memberships also imply their ancestor categories,
 * allowing older category assignments to be repaired on sync.
 */
class SyncCategoriesWithTrackStatus(
    private val categoryRepository: CategoryRepository,
    private val setMangaCategories: SetMangaCategories,
) {

    suspend fun await(mangaId: Long, trackStatus: Int?) {
        if (mangaId <= 0L) return

        val categories = categoryRepository.getAll()
        if (categories.none { it.trackStatus != null }) return

        val current = categoryRepository
            .getCategoriesByMangaId(mangaId)
            .mapTo(mutableSetOf()) { it.id }

        val updated = updatedMembership(
            current = current,
            categories = categories,
            trackStatus = trackStatus,
        )

        if (updated != current) {
            setMangaCategories.await(mangaId, updated.toList())
        }
    }

    companion object {

        /**
         * Calculates the updated category memberships.
         *
         * This function does not modify the supplied current set.
         */
        fun updatedMembership(
            current: Set<Long>,
            categories: Collection<Category>,
            trackStatus: Int?,
        ): Set<Long> {
            val boundCategories = categories.filter {
                it.trackStatus != null
            }

            if (boundCategories.isEmpty()) return current

            val categoriesById = categories.associateBy(Category::id)

            // Determine the active parent branches before applying
            // tracker-status changes.
            //
            // A manga assigned directly to Shounen > Reading is still
            // considered part of Shounen, even if the parent category
            // membership is absent from older stored data.
            val activeRoots = current.mapNotNull { categoryId ->
                categoryId.rootCategoryId(categoriesById)
            }.toSet()

            // Only tracker-bound children of currently active
            // parent branches are eligible for synchronization.
            val managedChildren = boundCategories.filter { category ->
                category.parentId != Category.UNCATEGORIZED_ID &&
                    category.id.rootCategoryId(categoriesById) in activeRoots
            }

            // Existing root-level status bindings predate hierarchical
            // categories. Retain their global behavior for compatibility.
            val legacyRootCategories = boundCategories.filter { category ->
                category.parentId == Category.UNCATEGORIZED_ID
            }

            return current.toMutableSet().apply {
                // Restore missing parent memberships inferred from
                // existing category assignments.
                //
                // This ensures:
                // Shounen > Reading -> Shounen > Completed
                //
                // produces both Shounen and Shounen > Completed.
                addAll(activeRoots)

                // Apply status changes only to eligible branches,
                // plus legacy root-level status categories.
                (legacyRootCategories + managedChildren).forEach { category ->
                    if (category.trackStatus == trackStatus) {
                        add(category.id)
                    } else {
                        remove(category.id)
                    }
                }
            }
        }

        /**
         * Resolves the root category belonging to this category ID.
         *
         * Returns null when the category is missing, its parent
         * cannot be found, or a cycle exists in the hierarchy.
         */
        private fun Long.rootCategoryId(
            categoriesById: Map<Long, Category>,
        ): Long? {
            var category = categoriesById[this] ?: return null
            val visited = mutableSetOf<Long>()

            while (true) {
                if (!visited.add(category.id)) {
                    return null
                }

                if (category.parentId == Category.UNCATEGORIZED_ID) {
                    return category.id
                }

                category = categoriesById[category.parentId]
                    ?: return null
            }
        }
    }
}
// KMK <--
