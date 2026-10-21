package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTree

// KNS -->
/**
 * Merges source categories into a target: every manga of a source joins the target (its other
 * memberships stay), the sources' child categories move below the target, and the sources are
 * deleted. Categories are matched by id, never by name, so a same-named category in another branch
 * is never involved.
 */
@Inject
class MergeCategories(
    private val categoryRepository: CategoryRepository,
    private val deleteCategory: DeleteCategory,
) {

    suspend fun await(targetId: Long, sourceIds: List<Long>): Result = withNonCancellableContext {
        val sources = sourceIds.filter { it > 0L && it != targetId }.distinct()
        if (targetId <= 0L || sources.isEmpty()) return@withNonCancellableContext Result.InvalidMerge

        try {
            val categories = categoryRepository.getAll()
            val byId = categories.associateBy(Category::id)
            val target = byId[targetId] ?: return@withNonCancellableContext Result.InvalidMerge
            if (sources.any { it !in byId }) return@withNonCancellableContext Result.InvalidMerge
            val sourceSet = sources.toSet()

            // A target inside a source's subtree moves up to the parent of the top-most such source.
            val topSource = CategoryTree.ancestorIds(targetId, byId).lastOrNull { it in sourceSet }
            val targetParent = topSource?.let { byId.getValue(it).parentId } ?: target.parentId

            val movingChildren = sources
                .flatMap { CategoryTree.childrenOf(it, categories) }
                .filterNot { it.id == targetId || it.id in sourceSet }
            val movingIds = movingChildren.mapTo(mutableSetOf(), Category::id)
            val keptNames = CategoryTree.childrenOf(targetId, categories)
                .filterNot { it.id in sourceSet }
                .map { CategoryTree.normalizeName(it.name) }
            val movingNames = movingChildren.map { CategoryTree.normalizeName(it.name) }
            if (movingNames.size != movingNames.distinct().size || movingNames.any { it in keptNames }) {
                return@withNonCancellableContext Result.DuplicateName
            }
            val remaining = categories.filterNot { it.id in sourceSet }
            if (targetParent != target.parentId &&
                CategoryTree.hasSiblingName(target.name, targetParent, remaining, excludingId = targetId)
            ) {
                return@withNonCancellableContext Result.DuplicateName
            }

            val updated = categories.map { category ->
                when (category.id) {
                    targetId -> category.copy(parentId = targetParent)
                    in movingIds -> category.copy(parentId = targetId)
                    else -> category
                }
            }
            categoryRepository.updateHierarchy(CategoryTree.flattened(updated))
            categoryRepository.copyMemberships(sources, targetId)

            sources.forEach { sourceId ->
                when (val result = deleteCategory.await(sourceId)) {
                    is DeleteCategory.Result.InternalError -> throw result.error
                    DeleteCategory.Result.DuplicateName -> return@withNonCancellableContext Result.DuplicateName
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
        data object InvalidMerge : Result
        data object DuplicateName : Result
        data class InternalError(val error: Throwable) : Result
    }
}
// KNS <--
