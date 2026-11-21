package tachiyomi.domain.category.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.category.service.CategoryIdPreferences

/**
 * Removes the ids of deleted categories from every preference that stores a list of category ids:
 * the library and download update filters, the library filter tab, the History, Updates and
 * Upcoming tab filters and the Discord RPC incognito set. `LibraryPreferences.defaultCategory`
 * stores a single id instead of a list, so it is reset here too: the sync deletion path does not go
 * through [DeleteCategory], and a stale default would keep targeting a category that no longer
 * exists.
 *
 * The inventory of pruned preferences is [CategoryIdPreferences], shared with the
 * `CategoryPreferenceRepairMigration` that repairs existing drift. Because the Discord set is part of
 * it, both deletion paths prune it in this one layer — no caller has to remember it.
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
    private val categoryIdPreferences: CategoryIdPreferences,
) {

    fun prune(categoryIds: Collection<Long>) {
        if (categoryIds.isEmpty()) return

        // -1 is `defaultCategory`'s "no default category" sentinel (`LibraryPreferences`), not an id,
        // so only a real id match deletes the preference. An unset preference writes nothing.
        val defaultCategory = categoryIdPreferences.defaultCategory.get()
        if (defaultCategory >= 0 && categoryIds.contains(defaultCategory.toLong())) {
            categoryIdPreferences.defaultCategory.delete()
        }

        val categoryIdStrings = categoryIds.mapTo(mutableSetOf()) { it.toString() }
        categoryIdPreferences.stringFilters.forEach { preference ->
            val ids = preference.get()
            val remaining = ids - categoryIdStrings
            if (remaining.size != ids.size) preference.set(remaining)
        }

        val categoryIdSet = categoryIds.toSet()
        categoryIdPreferences.longFilters.forEach { preference ->
            val ids = preference.get()
            val remaining = ids - categoryIdSet
            if (remaining.size != ids.size) preference.set(remaining)
        }
    }
}
