// KUN -->
package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.service.CategoryIdPreferences

/**
 * Removes the ids of categories that no longer exist from the preferences that store category ids,
 * once, on upgrade. The covered preferences are:
 *
 * - the eight `Set<String>` filters: `updateCategories`, `updateCategoriesExclude`,
 *   `filterCategoriesInclude`, `filterCategoriesExclude` (library),
 *   `removeExcludeCategories`, `downloadNewChapterCategories`,
 *   `downloadNewChapterCategoriesExclude` (download) and `discordRPCIncognitoCategories`
 *   (Discord RPC incognito);
 * - the six `List<Long>` filters: the History, Updates and Upcoming include/exclude filters;
 * - `defaultCategory` (library), which stores a single id.
 *
 * Every group comes from [CategoryIdPreferences], the same inventory `PruneCategoryReferences` prunes
 * on deletion and the backup `PreferenceRestorer` translates on a restore, so the three cannot drift
 * apart.
 *
 * [CategoryPreferencesCleanupMigration] (73f) only covered the library/download `Set<String>` filters
 * and `defaultCategory`; the History, Updates and Upcoming `List<Long>` filters it skipped can still
 * hold the id of a category deleted before this fork pruned them on deletion. A stale id there keeps
 * filtering on a category that is gone — with only stale include ids the tab comes up empty — and
 * because `category.id` is a rowid the same id can later belong to a different category.
 *
 * Every value is intersected with the live ids, and a preference is written only when the
 * intersection actually differs, so the migration emits nothing on a store that is already clean and
 * can safely run twice. A fresh install never reaches it: `Migrator` seeds `applied` from the stored
 * version code, and `0` selects the [mihon.core.migration.InitialMigrationStrategy], which runs only
 * the `isAlways` migrations.
 *
 * This repairs existing drift; the steady state is pruning on deletion. `PruneCategoryReferences`
 * covers the whole [CategoryIdPreferences] inventory — the Discord incognito set included — from
 * `DeleteCategory` and the sync merge, so a category deleted after this migration does not leave its
 * id behind anywhere.
 *
 * Numbered `86f` because [ClearPagePreviewCacheMigration] took `85f`: `Migrator` runs only the
 * versions in `(applied + 1)..target`, so a second migration at a version an install already applied
 * would never run.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class CategoryPreferenceRepairMigration(
    private val categoryIdPreferences: CategoryIdPreferences,
    private val getCategories: GetCategories,
) : Migration {
    override val version: Float = 86f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        // The category table seeds the system category (id 0), so it is live like any other row.
        val liveCategoryIds = getCategories.await().mapTo(mutableSetOf()) { it.id }
        val liveCategoryIdStrings = liveCategoryIds.mapTo(mutableSetOf()) { it.toString() }

        categoryIdPreferences.stringFilters.forEach { preference ->
            val ids = preference.get()
            val remaining = ids.intersect(liveCategoryIdStrings)
            if (remaining != ids) preference.set(remaining)
        }

        categoryIdPreferences.longFilters.forEach { preference ->
            val ids = preference.get()
            // `filter` rather than `intersect`: the preference is a List and its order is kept.
            val remaining = ids.filter { it in liveCategoryIds }
            if (remaining != ids) preference.set(remaining)
        }

        // -1 is `defaultCategory`'s "no default category" sentinel, not an id.
        val defaultCategory = categoryIdPreferences.defaultCategory.get()
        if (defaultCategory >= 0 && defaultCategory.toLong() !in liveCategoryIds) {
            categoryIdPreferences.defaultCategory.delete()
        }

        return@withContext true
    }
}
// KUN <--
