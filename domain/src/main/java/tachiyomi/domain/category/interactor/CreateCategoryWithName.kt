package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryTree
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class CreateCategoryWithName(
    private val categoryRepository: CategoryRepository,
    private val preferences: LibraryPreferences,
) {

    private val initialFlags: Long
        get() {
            val sort = preferences.sortingMode.get()
            return sort.type.flag or sort.direction.flag
        }

    suspend fun await(
        name: String,
        // KNS -->
        parentId: Long = Category.UNCATEGORIZED_ID,
        // KNS <--
    ): Result {
        val categories = categoryRepository.getAll()
        // KNS -->
        // Names are unique among siblings only; the same name under another parent is fine.
        CategoryTree.childrenOf(parentId, categories)
            .firstOrNull { CategoryTree.normalizeName(it.name) == CategoryTree.normalizeName(name) }
            ?.let { return Result.DuplicateName(it) }
        val parent = categories.firstOrNull { it.id == parentId }
        if (parentId != Category.UNCATEGORIZED_ID && parent == null) {
            return Result.InternalError(IllegalArgumentException("Parent category $parentId does not exist"))
        }
        // KNS <--
        val nextOrder = categories.maxOfOrNull { it.order }?.plus(1) ?: 0
        val newCategory = Category(
            id = 0,
            name = name,
            order = nextOrder,
            flags = initialFlags,
            // KMK -->
            hidden = false,
            // KMK <--
            // KNS -->
            parentId = parentId,
            uid = parent?.let { Category.childUid(it.uid, name) } ?: 0L,
            // KNS <--
        )

        return try {
            // KNS -->
            val id = categoryRepository.insert(newCategory)
            if (parent != null && id != null) {
                // Appended last globally; the depth-first order puts it at the end of its parent's children.
                categoryRepository.updateHierarchy(
                    CategoryTree.flattened(categories + newCategory.copy(id = id)),
                )
            }
            // KNS <--
            Result.Success(/* SY --> */newCategory/* SY <-- */)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        // SY -->
        data class Success(val category: Category) : Result

        // SY <--
        data class InternalError(val error: Throwable) : Result

        // KNS -->
        /** A sibling with the same (normalized) name already exists. */
        data class DuplicateName(val existing: Category) : Result
        // KNS <--
    }
}
