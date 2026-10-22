package tachiyomi.domain.updates.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getLongArray

@Inject
@SingleIn(AppScope::class)
class UpdatesPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val filterDownloaded = preferenceStore.getEnum(
        "pref_filter_updates_downloaded",
        TriState.DISABLED,
    )

    val filterUnread = preferenceStore.getEnum(
        "pref_filter_updates_unread",
        TriState.DISABLED,
    )

    val filterStarted = preferenceStore.getEnum(
        "pref_filter_updates_started",
        TriState.DISABLED,
    )

    val filterBookmarked = preferenceStore.getEnum(
        "pref_filter_updates_bookmarked",
        TriState.DISABLED,
    )

    // KNS --> Independent tri-state bookmark-color filters for Updates.
    val filterBookmarkColors = preferenceStore.getStringSet("pref_filter_updates_bookmark_colors", emptySet())
    val filterBookmarkColorsExclude = preferenceStore.getStringSet(
        "pref_filter_updates_bookmark_colors_exclude",
        emptySet(),
    )
    // KNS <--

    val filterExcludedScanlators = preferenceStore.getBoolean(
        "pref_filter_updates_hide_excluded_scanlators",
        false,
    )

    val filterIncludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        "pref_filter_updates_included_categories",
        emptyList(),
    )

    val filterExcludedCategories: Preference<List<Long>> = preferenceStore.getLongArray(
        "pref_filter_updates_excluded_categories",
        emptyList(),
    )

    // KMK -->
    val usePanoramaCover = preferenceStore.getBoolean(
        USE_PANORAMA_COVER_PREF,
        false,
    )
    // KMK <--
}

// KMK -->
const val USE_PANORAMA_COVER_PREF = "pref_updates_history_screen_use_panorama_cover"
// KMK <--
