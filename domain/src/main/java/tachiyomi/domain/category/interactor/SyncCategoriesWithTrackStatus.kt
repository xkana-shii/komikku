package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTree

// KNS -->
/**
 * Smart Categories: keeps a manga's tracker-status-bound categories in line with its tracker status.
 *
 * Only categories with a [Category.trackStatus] binding are ever added or removed, and only when
 * they are in scope (see [updatedMembership]); every other membership is left exactly as it was.
 */
@Inject
class SyncCategoriesWithTrackStatus(
    private val categoryRepository: CategoryRepository,
    private val setMangaCategories: SetMangaCategories,
) {

    /** [trackStatus] is the unified tracker status (`TrackStatus.int`); null when nothing usable is known. */
    suspend fun await(mangaId: Long, trackStatus: Int?) {
        if (mangaId <= 0L) return
        val categories = categoryRepository.getAll()
        if (categories.none { it.trackStatus != null }) return

        val current = categoryRepository.getCategoriesByMangaId(mangaId).mapTo(mutableSetOf(), Category::id)
        val updated = updatedMembership(current, categories, trackStatus)
        if (updated != current) setMangaCategories.await(mangaId, updated.toList())
    }

    companion object {
        /**
         * The membership after applying [trackStatus]:
         *
         * - A bound category **without a parent** is global: it is always in scope.
         * - A bound **subcategory** is in scope only when its parent is one of the manga's categories
         *   or an ancestor of one of them ("reachable"), at any depth. A manga filed only in
         *   `Shounen / Reading` reaches `Shounen`, so `Shounen / Completed` is in scope, while
         *   `Josei / Completed` is not.
         * - In-scope bound categories are added when their status matches and removed otherwise.
         *   Out-of-scope ones are never added, removed or otherwise touched.
         * - When a bound category is removed and nothing left in the membership still reaches its
         *   parent, the parent is kept as a membership so the manga stays in that branch (and the
         *   branch's other status categories stay in scope for the next change).
         * - A null status removes the in-scope bound categories (with the same parent anchoring).
         */
        fun updatedMembership(
            current: Set<Long>,
            categories: Collection<Category>,
            trackStatus: Int?,
        ): Set<Long> {
            val bound = categories.filter { it.trackStatus != null && !it.isSystemCategory }
            if (bound.isEmpty()) return current

            val byId = categories.associateBy(Category::id)
            val reachable = CategoryTree.withAncestors(current.filter { it in byId }, byId)
            val inScope = bound.filter { category ->
                category.parentId == Category.UNCATEGORIZED_ID || category.parentId in reachable
            }

            val result = current.toMutableSet()
            val removed = mutableListOf<Category>()
            inScope.forEach { category ->
                if (trackStatus != null && category.trackStatus == trackStatus) {
                    result += category.id
                } else if (result.remove(category.id)) {
                    removed += category
                }
            }

            removed.forEach { category ->
                val parentId = category.parentId
                if (parentId == Category.UNCATEGORIZED_ID) return@forEach
                val stillReached = CategoryTree.withAncestors(result.filter { it in byId }, byId)
                if (parentId !in stillReached) result += parentId
            }
            return result
        }
    }
}
// KNS <--
