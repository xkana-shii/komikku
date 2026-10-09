package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap
import kotlin.time.Duration.Companion.seconds

class SourcesViewModel(
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
    // SY -->
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getSourceCategories: GetSourceCategories = Injekt.get(),
    private val getShowLatest: GetShowLatest = Injekt.get(),
    private val toggleExcludeFromDataSaver: ToggleExcludeFromDataSaver = Injekt.get(),
    private val setSourceCategories: SetSourceCategories = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    val smartSearchConfig: SourcesScreen.SmartSearchConfig?,
    // SY <--
) : ViewModel() {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    val useNewSourceNavigation by uiPreferences.useNewSourceNavigation().asState(viewModelScope)

    private val dialog = MutableStateFlow<Dialog?>(null)
    // KMK -->
    private val searchQuery = MutableStateFlow<String?>(null)
    private val nsfwOnly = MutableStateFlow(false)
    private val filters = combine(searchQuery, nsfwOnly, ::Pair)
        .distinctUntilChanged()
        .debounce(SEARCH_DEBOUNCE_MILLIS)
    // KMK <--

    private val sourceItems = combine(
        getEnabledSources.subscribe(),
        getSourceCategories.subscribe(),
        getShowLatest.subscribe(smartSearchConfig != null),
        filters,
    ) { sources, categories, showLatest, filters ->
        toSourceState(
            filters = filters,
            sources = sources,
            categories = categories,
            showLatest = showLatest,
            showPin = smartSearchConfig == null,
        )
    }
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(Event.FailedFetchingSources)
        }

    val state: StateFlow<State> = combine(
        sourceItems,
        dialog,
        // SY -->
        sourcePreferences.dataSaver().changes(),
    ) { state, dialog, dataSaver ->
        state.copy(dialog = dialog, dataSaverEnabled = dataSaver != DataSaver.NONE)
        // SY <--
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun toSourceState(
        // KMK -->
        filters: Pair<String?, Boolean>,
        sources: List<Source>,
        // KMK <--
        categories: List<String>,
        showLatest: Boolean,
        showPin: Boolean,
    ): State {
        // KMK -->
        val searchQuery = filters.first
        val nsfwOnly = filters.second
        val queryFilter: (String?) -> ((Source) -> Boolean) = { query ->
            filter@{ source ->
                if (query.isNullOrBlank()) return@filter true
                query.split(",").any {
                    val input = it.trim()
                    if (input.isEmpty()) return@any false
                    source.installedExtension?.name?.contains(input, ignoreCase = true) == true ||
                        source.name.contains(input, ignoreCase = true) ||
                        source.id == input.toLongOrNull()
                }
            }
        }
        val filteredSources = sources
            .filter { !nsfwOnly || it.installedExtension?.isNsfw != false }
            .filter(queryFilter(searchQuery))
        // KMK <--
        val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
            // Sources without a lang defined will be placed at the end
            when {
                d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                // SY -->
                d1.startsWith(CATEGORY_KEY_PREFIX) && !d2.startsWith(CATEGORY_KEY_PREFIX) -> -1
                d2.startsWith(CATEGORY_KEY_PREFIX) && !d1.startsWith(CATEGORY_KEY_PREFIX) -> 1
                // SY <--
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = filteredSources.groupByTo(map) {
            when {
                // SY -->
                it.category != null -> "$CATEGORY_KEY_PREFIX${it.category}"
                // SY <--
                it.isUsedLast -> LAST_USED_KEY
                Pin.Actual in it.pin -> PINNED_KEY
                else -> it.lang
            }
        }

        return State(
            isLoading = false,
            items = byLang
                .flatMap {
                    listOf(
                        SourceUiModel.Header(
                            it.key.removePrefix(CATEGORY_KEY_PREFIX),
                            it.value.firstOrNull()?.category != null,
                        ),
                        *it.value.map { source -> SourceUiModel.Item(source) }.toTypedArray(),
                    )
                }
                .toImmutableList(),
            // SY -->
            categories = categories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).toImmutableList(),
            showPin = showPin,
            showLatest = showLatest,
            // SY <--
            // KMK -->
            searchQuery = searchQuery,
            nsfwOnly = nsfwOnly,
            // KMK <--
        )
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    // SY -->
    fun toggleExcludeFromDataSaver(source: Source) {
        toggleExcludeFromDataSaver.await(source)
    }

    fun setSourceCategories(source: Source, categories: List<String>) {
        setSourceCategories.await(source, categories)
    }

    fun showSourceCategoriesDialog(source: Source) {
        dialog.update { Dialog.SourceCategories(source) }
    }
    // SY <--

    fun showSourceDialog(source: Source) {
        dialog.update { Dialog.SourceLongClick(source) }
    }

    fun closeDialog() {
        dialog.update { null }
    }

    // KMK -->
    fun search(query: String?) {
        searchQuery.update { query }
    }

    fun toggleNsfwOnly() {
        nsfwOnly.update { !it }
    }
    // KMK <--

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    sealed class Dialog {
        data class SourceLongClick(val source: Source) : Dialog()
        data class SourceCategories(val source: Source) : Dialog()
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<SourceUiModel> = persistentListOf(),
        // SY -->
        val categories: ImmutableList<String> = persistentListOf(),
        val showPin: Boolean = true,
        val showLatest: Boolean = false,
        val dataSaverEnabled: Boolean = false,
        // SY <--
        // KMK -->
        val searchQuery: String? = null,
        val nsfwOnly: Boolean = false,
        // KMK <--
    ) {
        val isEmpty = items.isEmpty()
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"

        // SY -->
        const val CATEGORY_KEY_PREFIX = "category-"
        // SY <--
    }
}
