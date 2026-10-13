package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository

// KMK -->
class SetCategoryUpdateInterval(
    private val categoryRepository: CategoryRepository,
) {
    suspend fun await(categoryId: Long, updateIntervalDays: Int?) {
        require(updateIntervalDays == null || updateIntervalDays > 0)
        if (categoryId != Category.UNCATEGORIZED_ID) {
            categoryRepository.updateUpdateInterval(categoryId, updateIntervalDays)
        }
    }
}
// KMK <--
