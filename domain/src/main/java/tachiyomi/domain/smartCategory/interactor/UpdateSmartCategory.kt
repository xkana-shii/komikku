package tachiyomi.domain.smartCategory.interactor

import tachiyomi.domain.smartCategory.repository.SmartCategoryRepository

class UpdateSmartCategory(
    private val smartCategoryRepository: SmartCategoryRepository,
    private val getSmartCategory: GetSmartCategory,
) {
    suspend fun await(categoryId: Long, tags: List<String>): Result {
        if (hasDuplicates(tags)) {
            return Result.TagExists
        }

        smartCategoryRepository.update(
            categoryId = categoryId,
            tags = tags,
        )

        return Result.Success
    }

    suspend fun awaitAddTag(categoryId: Long, tag: String): Result {
        val smartCategory = getSmartCategory.await(categoryId) ?: return Result.InternalError
        return await(
            categoryId = categoryId,
            tags = smartCategory.tags + tag,
        )
    }

    suspend fun awaitRemoveTag(categoryId: Long, tag: String): Result {
        val smartCategory = getSmartCategory.await(categoryId) ?: return Result.InternalError
        return await(
            categoryId = categoryId,
            tags = smartCategory.tags.filter { it != tag },
        )
    }

    sealed class Result {
        data object InternalError : Result()
        data object TagExists : Result()
        data object Success : Result()
    }

    private fun hasDuplicates(tags: List<String>) = tags.toSet().size != tags.size
}
