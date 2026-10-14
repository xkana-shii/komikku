package eu.kanade.tachiyomi.ui.history

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.history.service.HistoryPreferences

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class HistorySettingsViewModel(
    val historyPreferences: HistoryPreferences,
) : ViewModel() {

    fun toggleFilter(preference: (HistoryPreferences) -> Preference<TriState>) {
        preference(historyPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleSwitch(preference: (HistoryPreferences) -> Preference<Boolean>) {
        preference(historyPreferences).getAndSet { !it }
    }
}
