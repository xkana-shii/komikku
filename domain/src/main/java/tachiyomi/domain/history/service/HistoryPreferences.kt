package tachiyomi.domain.history.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getLongArray
import tachiyomi.domain.updates.service.USE_PANORAMA_COVER_PREF

@Inject
@SingleIn(AppScope::class)
class HistoryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val filterUnfinishedManga = preferenceStore.getEnum(
        "pref_filter_history_unfinished_manga",
        TriState.DISABLED,
    )

    val filterUnfinishedChapter = preferenceStore.getEnum(
        "pref_filter_history_unfinished_chapter",
        TriState.DISABLED,
    )

    val filterNonLibraryManga = preferenceStore.getEnum(
        "pref_filter_history_non_library_manga",
        TriState.DISABLED,
    )

    val usePanoramaCover = preferenceStore.getBoolean(
        USE_PANORAMA_COVER_PREF,
        false,
    )

    // KUN -->
    /**
     * Local category ids (the ids of this device's `category` rows). App-state: the filter is kept on
     * this device only, never synced and never written to a backup, so a restore cannot touch it.
     */
    val filterIncludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        Preference.appStateKey(FILTER_INCLUDED_CATEGORIES_PREF_KEY),
        emptyList(),
    )

    /** Local category ids; see [filterIncludedCategories]. */
    val filterExcludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        Preference.appStateKey(FILTER_EXCLUDED_CATEGORIES_PREF_KEY),
        emptyList(),
    )

    /**
     * When true, a manga that explicitly matches an included category is shown even when it is also
     * in an excluded category. Off (the default) keeps the historical behaviour: exclusion wins.
     * An empty include list never rescues a manga, so exclusions still apply. App-state: it describes
     * the local category lists above, so it stays on this device with them.
     */
    val filterCategoryInclusionWins = preferenceStore.getBoolean(
        Preference.appStateKey(FILTER_INCLUSION_WINS_PREF_KEY),
        false,
    )

    /**
     * Clears both category lists by writing an ordinary empty value instead of unsetting the keys:
     * the reset is an explicit "no categories" selection, symmetric with the ordinary writes the
     * category-filter controls make, so the stored value keeps reflecting what the user chose.
     */
    fun clearCategoryFilter() {
        filterIncludedCategories.set(emptyList())
        filterExcludedCategories.set(emptyList())
    }
    // KUN <--

    // KUN -->
    companion object {
        const val FILTER_INCLUDED_CATEGORIES_PREF_KEY = "pref_filter_history_included_categories"
        const val FILTER_EXCLUDED_CATEGORIES_PREF_KEY = "pref_filter_history_excluded_categories"
        const val FILTER_INCLUSION_WINS_PREF_KEY = "pref_filter_history_category_inclusion_wins"
    }
    // KUN <--
}
