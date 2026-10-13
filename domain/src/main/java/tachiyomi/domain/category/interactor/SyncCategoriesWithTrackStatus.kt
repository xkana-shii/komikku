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
        val bound = categoryRepository.getAll().filter { it.trackStatus != null }
        if (mangaId <= 0L || bound.isEmpty()) return

        val current = categoryRepository.getCategoriesByMangaId(mangaId).mapTo(mutableSetOf()) { it.id }
        val original = current.toSet()
        bound.forEach { category ->
            if (category.trackStatus == trackStatus) current += category.id else current -= category.id
        }
        if (current != original) setMangaCategories.await(mangaId, current.toList())
    }

    companion object {
        fun updatedMembership(
            current: Set<Long>,
            boundCategories: Collection<Category>,
            trackStatus: Int?,
        ): Set<Long> = current.toMutableSet().apply {
            boundCategories.filter { it.trackStatus != null }.forEach { category ->
                if (category.trackStatus == trackStatus) add(category.id) else remove(category.id)
            }
        }
    }
}
// KMK <--
