package exh.pref

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class DelegateSourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    val delegateSources = preferenceStore.getBoolean("eh_delegate_sources", true)

    val useJapaneseTitle = preferenceStore.getBoolean("use_jp_title", false)
}
