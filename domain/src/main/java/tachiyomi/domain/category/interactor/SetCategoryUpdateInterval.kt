package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.repository.CategoryRepository

// KNS -->
/** Sets a category's library update interval in days, or null to follow the global schedule. */
@Inject
class SetCategoryUpdateInterval(
    private val categoryRepository: CategoryRepository,
) {
    suspend fun await(categoryId: Long, intervalDays: Int?): Boolean {
        if (categoryId <= 0L || (intervalDays != null && intervalDays <= 0)) return false
        return try {
            categoryRepository.updateUpdateInterval(categoryId, intervalDays)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }
}
// KNS <--
