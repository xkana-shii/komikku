package tachiyomi.domain.smartCategory.interactor

import tachiyomi.domain.smartCategory.repository.SmartCategoryRepository

class GetSmartCategory(
    private val smartCategoryRepository: SmartCategoryRepository,
) {
    suspend fun await() = smartCategoryRepository.getAll()

    suspend fun await(categoryId: Long) = smartCategoryRepository.getByCategoryId(categoryId)

    suspend fun subscribe() = smartCategoryRepository.subscribeGetAll()

    suspend fun subscribe(categoryId: Long) = smartCategoryRepository.subscribeGetByCategoryId(categoryId)
}
