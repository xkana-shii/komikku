package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTreeHandler
import tachiyomi.domain.manga.repository.MangaRepository

// KMK -->
/** Merges category membership and descendants into one selected category. */
class MergeCategories(
    private val categoryRepository: CategoryRepository,
    private val mangaRepository: MangaRepository,
    private val deleteCategory: DeleteCategory,
) {

    suspend fun await(targetId: Long, sourceIds: List<Long>) = withNonCancellableContext {
        val sources = sourceIds.filter { it > 0L && it != targetId }.distinct()
        if (targetId <= 0L || sources.isEmpty()) {
            return@withNonCancellableContext Result.InternalError(
                IllegalArgumentException("Invalid merge target or sources"),
            )
        }

        try {
            val categories = categoryRepository.getAll().filterNot(Category::isSystemCategory)
            val byId = categories.associateBy(Category::id)
            val target = byId[targetId]
                ?: return@withNonCancellableContext Result.InternalError(
                    IllegalArgumentException("Merge target does not exist"),
                )
            if (sources.any { it !in byId }) {
                return@withNonCancellableContext Result.InternalError(
                    IllegalArgumentException("A merge source does not exist"),
                )
            }

            val sourceChildren = sources.flatMap { CategoryTreeHandler.childrenOf(it, categories) }
                .filterNot { it.id == targetId || it.id in sources }
            if (target.parentId != Category.UNCATEGORIZED_ID && sourceChildren.isNotEmpty()) {
                return@withNonCancellableContext Result.InternalError(
                    IllegalArgumentException("Merge would create more than two category levels"),
                )
            }
            // KMK --> Moving children as part of a merge must obey the same sibling-name rule
            // as a manual or bulk reparenting operation.
            val sourceSet = sources.toSet()
            val targetChildNames = CategoryTreeHandler.childrenOf(targetId, categories)
                .filterNot { it.id in sourceSet }
                .map { CategoryTreeHandler.normalizeName(it.name) }
                .toSet()
            val movedChildNames = sourceChildren.map { CategoryTreeHandler.normalizeName(it.name) }
            if (movedChildNames.size != movedChildNames.distinct().size ||
                movedChildNames.any { it in targetChildNames }
            ) {
                return@withNonCancellableContext Result.InternalError(
                    IllegalArgumentException("Merge would create duplicate sibling names"),
                )
            }
            // KMK <--

            if (target.parentId in sourceSet) {
                val parent = byId[target.parentId]
                val newParentId = parent?.parentId ?: Category.UNCATEGORIZED_ID
                if (!CategoryTreeHandler.canMove(target.id, newParentId, categories)) {
                    return@withNonCancellableContext Result.InternalError(
                        IllegalArgumentException("Merge would create an invalid hierarchy"),
                    )
                }
                categoryRepository.updateParent(target.id, newParentId)
            }

            sourceChildren.forEach { child -> categoryRepository.updateParent(child.id, targetId) }

            val library = mangaRepository.getLibraryManga()
            library.forEach { item ->
                if (item.categories.any { it in sourceSet }) {
                    mangaRepository.setMangaCategories(
                        item.id,
                        (item.categories - sourceSet + targetId).distinct(),
                    )
                }
            }

            sources.forEach { sourceId ->
                when (val result = deleteCategory.await(sourceId)) {
                    is DeleteCategory.Result.InternalError -> throw result.error
                    DeleteCategory.Result.Success -> Unit
                }
            }
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to merge categories into $targetId" }
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
// KMK <--
