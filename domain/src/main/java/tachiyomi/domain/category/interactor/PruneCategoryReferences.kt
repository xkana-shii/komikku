package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import tachiyomi.domain.updates.service.UpdatesPreferences

/**
 * Removes the ids of deleted categories from every preference that stores a list of category ids:
 * the library and download update filters, the library filter tab and the History, Updates and
 * Upcoming tab filters. `LibraryPreferences.defaultCategory` is a single Int id and is reset by
 * [DeleteCategory], not by this list.
 *
 * Local deletions go through [DeleteCategory], but the non-v2 sync merge deletes merged-away category
 * rows directly, so the pruning has to be reachable from both. A stale id keeps filtering on a
 * category that no longer exists (with only stale include ids the History list comes up empty), and
 * because `category.id` is a rowid the same id can later belong to a different category.
 *
 * A preference is written only when it actually contains one of [prune]'s ids, so nothing emits when
 * there is nothing to prune.
 */
@Inject
class PruneCategoryReferences(
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val historyPreferences: HistoryPreferences,
    private val updatesPreferences: UpdatesPreferences,
    private val upcomingPreferences: UpcomingPreferences,
) {

    fun prune(categoryIds: Collection<Long>) {
        if (categoryIds.isEmpty()) return

        val categoryIdStrings = categoryIds.mapTo(mutableSetOf()) { it.toString() }
        listOf(
            libraryPreferences.updateCategories,
            libraryPreferences.updateCategoriesExclude,
            libraryPreferences.filterCategoriesInclude,
            libraryPreferences.filterCategoriesExclude,
            downloadPreferences.removeExcludeCategories,
            downloadPreferences.downloadNewChapterCategories,
            downloadPreferences.downloadNewChapterCategoriesExclude,
        ).forEach { preference ->
            val ids = preference.get()
            val remaining = ids - categoryIdStrings
            if (remaining.size != ids.size) preference.set(remaining)
        }

        val categoryIdSet = categoryIds.toSet()
        listOf(
            historyPreferences.filterIncludedCategories,
            historyPreferences.filterExcludedCategories,
            updatesPreferences.filterIncludedCategories,
            updatesPreferences.filterExcludedCategories,
            upcomingPreferences.filterIncludedCategories,
            upcomingPreferences.filterExcludedCategories,
        ).forEach { preference ->
            val ids = preference.get()
            val remaining = ids - categoryIdSet
            if (remaining.size != ids.size) preference.set(remaining)
        }
    }
}
