package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.repository.CategoryRepository

// KNS -->
/** Binds a category to a unified tracker status (Smart Category), or clears the binding with null. */
@Inject
class SetCategoryTrackStatus(
    private val categoryRepository: CategoryRepository,
) {
    suspend fun await(categoryId: Long, trackStatus: Int?): Boolean {
        if (categoryId <= 0L) return false
        return try {
            categoryRepository.updateTrackStatus(categoryId, trackStatus)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }
}
// KNS <--
