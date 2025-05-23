package tachiyomi.domain.smartCategory.interactor

import tachiyomi.domain.smartCategory.repository.SmartCategoryRepository

class CreateSmartCategory(
    private val smartCategoryRepository: SmartCategoryRepository,
    private val getSmartCategory: GetSmartCategory,
) {
    suspend fun await(categoryId: Long, tags: List<String> = emptyList()): Result {
        if (smartCategoryExists(categoryId)) {
            return Result.SmartCategoryExists
        }

        smartCategoryRepository.insert(
            categoryId = categoryId,
            tags = tags,
        )

        return Result.Success
    }

    sealed class Result {
        data object SmartCategoryExists : Result()
        data object Success : Result()
    }

    private suspend fun smartCategoryExists(categoryId: Long) = getSmartCategory.await(categoryId) != null
}
