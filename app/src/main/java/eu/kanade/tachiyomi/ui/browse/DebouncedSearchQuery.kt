package eu.kanade.tachiyomi.ui.browse

import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce

// KMK --> Keep text-field state synchronous while delaying only the expensive result filtering.
internal class DebouncedSearchQuery {
    private val mutableValue = MutableStateFlow<String?>(null)

    val value: StateFlow<String?> = mutableValue.asStateFlow()
    val debounced: Flow<String?> = value.debounce(SEARCH_DEBOUNCE_MILLIS)

    fun update(query: String?) {
        mutableValue.value = query
    }
}
// KMK <--
