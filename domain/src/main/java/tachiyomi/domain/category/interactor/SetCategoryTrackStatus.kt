package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

// KMK -->
class SetCategoryTrackStatus(
    private val categoryRepository: CategoryRepository,
) {
    suspend fun await(categoryId: Long, trackStatus: Int?) {
        if (categoryId != Category.UNCATEGORIZED_ID) {
            categoryRepository.updateTrackStatus(categoryId, trackStatus)
        }
    }
}
// KMK <--
