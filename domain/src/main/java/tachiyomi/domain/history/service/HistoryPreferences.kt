package tachiyomi.domain.history.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
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
}
