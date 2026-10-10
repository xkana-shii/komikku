package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class SearchViewModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<State>(initialState)

    // Subclasses can't touch the backing field (Kotlin forbids a visibility modifier on one),
    // so state writes from them go through here.
    protected fun updateState(function: (State) -> State) {
        state.update(function)
    }

    // KMK -->
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)
    // KMK <--
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledSources().get()
    protected val pinnedSources = sourcePreferences.pinnedSources().get()
    // KMK --> Source category membership is shared with the Sources tab.
    protected val sourcesInCategories = sourcePreferences.sourcesTabSourcesInCategories().get()
    // KMK <--

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null
    // KMK --> A source category is an independent search scope and cache key.
    private var lastSourceCategory: String? = null
    private var searchGeneration = 0L
    // KMK <--

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        viewModelScope.launch {
            preferences.globalSearchFilterState().changes().collectLatest { onlyShowHasResults ->
                state.update { it.copy(onlyShowHasResults = onlyShowHasResults) }
            }
        }
        // KMK -->
        viewModelScope.launch {
            preferences.globalSearchPinnedState().changes().collectLatest { state ->
                this@SearchViewModel.state.update { it.copy(sourceFilter = state) }
            }
        }
        // KMK <--
        // KMK --> Keep the independent source-category scope restored across app restarts.
        viewModelScope.launch {
            preferences.globalSearchSourceCategory().changes().collectLatest { category ->
                state.update { it.copy(sourceCategory = category) }
            }
        }
        // KMK <--
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<Source> {
        return sourceManager.getVisibleSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    // KMK -->
    fun hasPinnedSources(): Boolean = getEnabledSources().any { "${it.id}" in pinnedSources }

    fun shouldPinnedSourcesHidden() {
        if (!hasPinnedSources()) {
            preferences.globalSearchPinnedState().set(SourceFilter.All)
        }
    }
    // KMK <--

    private fun getSelectedSources(): List<Source> {
        val enabledSources = getEnabledSources()

        val filteredByExtension = if (extensionFilter.isNullOrEmpty()) {
            enabledSources
        } else {
            // KMK KNS -->
            val filteredSourceIds = extensionManager.installedExtensionsFlow.value
                .filter { it.pkgName == extensionFilter }
                .flatMap { it.sources }
                .filterIsInstance<CatalogueSource>()
                .map { it.id }
            enabledSources.filter { it.id in filteredSourceIds }
            // KMK KNS <--
        }

        return filteredByExtension
    }

    fun updateSearchQuery(query: String?) {
        state.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: SourceFilter) {
        preferences.globalSearchPinnedState().set(filter)
        // KMK --> The preference flow can arrive after search reads the current filter.
        state.update { it.copy(sourceFilter = filter) }
        // KMK <--
        search()
    }

    // KMK --> This composes with Pinned Only and All rather than replacing either filter.
    fun setSourceCategory(category: String) {
        preferences.globalSearchSourceCategory().set(category)
        state.update { it.copy(sourceCategory = category) }
        search()
    }
    // KMK <--

    fun toggleFilterResults() {
        preferences.globalSearchFilterState().toggle()
    }

    fun search(query: String? = state.value.searchQuery) {
        val sourceFilter = state.value.sourceFilter
        // KMK --> Prevent results from an old category from being presented as current.
        val sourceCategory = state.value.sourceCategory
        // KMK <--

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter && this.lastSourceCategory == sourceCategory) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter
        this.lastSourceCategory = sourceCategory
        // KMK --> Each rerun invalidates in-flight results from an older scope.
        val generation = ++searchGeneration
        // KMK <--

        searchJob?.cancel()

        val sources = getSelectedSources()

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        // KMK -->
        searchJob = viewModelScope.launch {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is SearchItemResult.Loading) {
                        return@async
                    }

                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, query.sanitize(), source.getFilterList())
                        }

                        val titles = page.mangas
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }

                        // KMK --> A cancelled search may finish its request before cancellation reaches it.
                        if (isActive && generation == searchGeneration) {
                            updateItem(source, SearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        // KMK --> Keep errors scoped to the active query, source filter, and category.
                        if (isActive && generation == searchGeneration) {
                            updateItem(source, SearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
        // KMK <--
    }

    private fun updateItems(items: Map<Source, SearchItemResult>) {
        state.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: Source, result: SearchItemResult) {
        // KMK -->
        state.update { currentState ->
            val newItems = currentState.items + (source to result)
            currentState.copy(
                items = newItems.toSortedMap(sortComparator(newItems)).toPersistentMap(),
            )
        }
        // KMK <--
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        viewModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            state.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        state.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        // KMK --> Empty means no category restriction.
        val sourceCategory: String = "",
        // KMK <--
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<Source, SearchItemResult> = persistentMapOf(),
        val dialog: Dialog? = null,
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
            .toImmutableMap()
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

enum class SourceFilter {
    All,
    PinnedOnly,
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
