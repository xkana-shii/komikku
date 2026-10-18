
package eu.kanade.tachiyomi.ui.browse

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

// KMK -->
class DebouncedSearchQuery(
    debounceMillis: Long = 250L,
) {

    private val _value = MutableStateFlow<String?>(null)

    val value: StateFlow<String?> = _value.asStateFlow()

    @OptIn(FlowPreview::class)
    val debounced: Flow<String?> = _value
        .drop(1)
        .debounce(debounceMillis)
        .distinctUntilChanged()

    /**
     * Visible search input is always immediate.
     * Only the search operation is debounced.
     */
    fun update(query: String?) {
        _value.value = query
    }
}
// KMK <--
