package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import mihon.domain.extension.model.ContentWarning
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import java.util.TreeMap
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class SourcesViewModel(
    @Assisted val smartSearchConfig: SourcesScreen.SmartSearchConfig?,
    private val getEnabledSources: GetEnabledSources,
    private val toggleSource: ToggleSource,
    private val toggleSourcePin: ToggleSourcePin,
    // SY -->
    private val uiPreferences: UiPreferences,
    private val getSourceCategories: GetSourceCategories,
    private val getShowLatest: GetShowLatest,
    private val toggleExcludeFromDataSaver: ToggleExcludeFromDataSaver,
    private val setSourceCategories: SetSourceCategories,
    private val sourcePreferences: SourcePreferences,
    // SY <--
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(smartSearchConfig: SourcesScreen.SmartSearchConfig?): SourcesViewModel
    }

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    val useNewSourceNavigation by uiPreferences.useNewSourceNavigation.asState(viewModelScope)

    private val dialog = MutableStateFlow<Dialog?>(null)

    // KMK -->
    private val searchQuery = MutableStateFlow<String?>(null)
    private val nsfwOnly = MutableStateFlow(false)
    // KMK <--

    private val enabledSources = combine(
        // KMK -->
        searchQuery,
        nsfwOnly,
        // KMK <--
        getEnabledSources.subscribe(),
    ) { query, nsfw, sources ->
        // KMK -->
        val queryFilter: (String?) -> ((Source) -> Boolean) = { searchQuery ->
            filter@{ source ->
                if (searchQuery.isNullOrBlank()) return@filter true
                searchQuery.split(",").any {
                    val input = it.trim()
                    if (input.isEmpty()) return@any false
                    source.installedExtension?.name?.contains(input, ignoreCase = true) == true ||
                        source.name.contains(input, ignoreCase = true) ||
                        source.id == input.toLongOrNull()
                }
            }
        }
        sources
            .filter { !nsfw || it.installedExtension?.contentWarning?.let { cw -> cw != ContentWarning.SAFE } != false }
            .filter(queryFilter(query))
        // KMK <--
    }
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(Event.FailedFetchingSources)
        }
        .map(::toSourceUiModels)
        .flowOn(Dispatchers.IO)

    private data class PartialState(
        val items: List<SourceUiModel>,
        val dialog: Dialog?,
        val categories: List<String>,
        val showLatest: Boolean,
        val dataSaverEnabled: Boolean,
    )

    private val partialState = combine(
        enabledSources,
        dialog,
        getSourceCategories.subscribe(),
        getShowLatest.subscribe(smartSearchConfig != null),
        sourcePreferences.dataSaver.changes(),
    ) { items, dialog, categories, showLatest, dataSaver ->
        PartialState(
            items = items,
            dialog = dialog,
            categories = categories,
            showLatest = showLatest,
            dataSaverEnabled = dataSaver != DataSaver.NONE,
        )
    }

    val state: StateFlow<State> = combine(
        partialState,
        // KMK -->
        searchQuery,
        nsfwOnly,
        // KMK <--
    ) { partial, query, nsfw ->
        State(
            dialog = partial.dialog,
            isLoading = false,
            items = partial.items.toList(),
            // SY -->
            categories = partial.categories
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                .toList(),
            showPin = smartSearchConfig == null,
            showLatest = partial.showLatest,
            dataSaverEnabled = partial.dataSaverEnabled,
            // SY <--
            // KMK -->
            searchQuery = query,
            nsfwOnly = nsfw,
            // KMK <--
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    private fun toSourceUiModels(sources: List<Source>): List<SourceUiModel> {
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
        val byLang = sources.groupByTo(map) {
            when {
                // SY -->
                it.category != null -> "$CATEGORY_KEY_PREFIX${it.category}"
                // SY <--
                it.isUsedLast -> LAST_USED_KEY
                Pin.Actual in it.pin -> PINNED_KEY
                else -> it.lang
            }
        }

        return byLang.flatMap {
            listOf(
                SourceUiModel.Header(
                    it.key.removePrefix(CATEGORY_KEY_PREFIX),
                    it.value.firstOrNull()?.category != null,
                ),
                *it.value.map { source ->
                    SourceUiModel.Item(source)
                }.toTypedArray(),
            )
        }
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
        searchQuery.value = query
    }

    fun toggleNsfwOnly() {
        nsfwOnly.value = !nsfwOnly.value
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
        val items: List<SourceUiModel> = listOf(),
        // SY -->
        val categories: List<String> = listOf(),
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
