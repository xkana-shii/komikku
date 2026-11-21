package tachiyomi.domain.category.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.connections.service.ConnectionsPreferences
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import tachiyomi.domain.updates.service.UpdatesPreferences

/**
 * The typed inventory of every preference that stores local category ids. It lives in one place
 * because three readers need the same list — `PruneCategoryReferences` (the steady-state prune after
 * a deletion), `CategoryPreferenceRepairMigration` (the one-shot repair of existing drift) and the
 * backup `PreferenceRestorer`, which translates another device's ids by category name — and two
 * hand-maintained copies of the list had already started to drift.
 *
 * - [stringFilters] hold the ids as strings: the four library update/filter sets, the three download
 *   sets and the Discord RPC incognito set ([ConnectionsPreferences.discordRPCIncognitoCategories]).
 * - [longFilters] hold them as `List<Long>` (order kept): the History, Updates and Upcoming
 *   include/exclude pair each.
 * - [defaultCategory] is the single "add to library" default; `-1` means unset.
 *
 * The Discord RPC incognito set is in [stringFilters] because `ConnectionsPreferences` lives in
 * `:domain` too: while it was an app-level class the inventory could not carry it, so its prune ran
 * from the app layer and could be missed.
 *
 * The History filters are app-state keys, so they are never written to a backup and never restored
 * from one; they are in the inventory all the same, and the readers that do cover backups key off
 * [stringFilters] and [longFilters] rather than a second copy of the list.
 */
@Inject
@SingleIn(AppScope::class)
class CategoryIdPreferences(
    libraryPreferences: LibraryPreferences,
    downloadPreferences: DownloadPreferences,
    historyPreferences: HistoryPreferences,
    updatesPreferences: UpdatesPreferences,
    upcomingPreferences: UpcomingPreferences,
    connectionsPreferences: ConnectionsPreferences,
) {

    val stringFilters: List<Preference<Set<String>>> = listOf(
        libraryPreferences.updateCategories,
        libraryPreferences.updateCategoriesExclude,
        libraryPreferences.filterCategoriesInclude,
        libraryPreferences.filterCategoriesExclude,
        downloadPreferences.removeExcludeCategories,
        downloadPreferences.downloadNewChapterCategories,
        downloadPreferences.downloadNewChapterCategoriesExclude,
        connectionsPreferences.discordRPCIncognitoCategories,
    )

    val longFilters: List<Preference<List<Long>>> = listOf(
        historyPreferences.filterIncludedCategories,
        historyPreferences.filterExcludedCategories,
        updatesPreferences.filterIncludedCategories,
        updatesPreferences.filterExcludedCategories,
        upcomingPreferences.filterIncludedCategories,
        upcomingPreferences.filterExcludedCategories,
    )

    val defaultCategory: Preference<Int> = libraryPreferences.defaultCategory
}
