package tachiyomi.domain.smartCategory.interactor

import tachiyomi.domain.smartCategory.repository.SmartCategoryRepository

class DeleteSmartCategory(private val smartCategoryRepository: SmartCategoryRepository) {
    suspend fun await(categoryId: Long) {
        smartCategoryRepository.delete(categoryId)
    }
}
