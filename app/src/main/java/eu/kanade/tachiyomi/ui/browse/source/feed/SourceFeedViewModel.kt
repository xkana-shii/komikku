package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.feed.MaxFeedItems
import exh.source.EH_PACKAGE
import exh.source.LOCAL_SOURCE_PACKAGE
import exh.source.getMainSource
import exh.source.isEhBasedSource
import exh.source.mangaDexSourceIds
import exh.util.nullIfBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.interactor.CountFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.source.local.isLocal
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import tachiyomi.domain.manga.model.Manga as DomainManga

@AssistedInject
class SourceFeedViewModel(
    @Assisted val sourceId: Long,
    uiPreferences: UiPreferences,
    private val sourceManager: SourceManager,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
    getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId,
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed,
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId,
    private val insertFeedSavedSearch: InsertFeedSavedSearch,
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById,
    private val getExhSavedSearch: GetExhSavedSearch,
    // KMK -->
    private val reorderFeed: ReorderFeed,
    getIncognitoState: GetIncognitoState,
    private val toggleIncognito: ToggleIncognito,
    private val extensionManager: ExtensionManager,
    sourcePreferences: SourcePreferences,
    // KMK <--
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): SourceFeedViewModel
    }

    val state: StateFlow<SourceFeedState>
        field = MutableStateFlow<SourceFeedState>(SourceFeedState())

    private val source: Source? get() = state.value.source

    val sourceIsMangaDex = sourceId in mangaDexSourceIds

    // KUN -->
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)
    // KUN <--

    val startExpanded by uiPreferences.expandFilters.asState(viewModelScope)

    // KMK -->
    var incognitoMode = mutableStateOf(false)
    // KMK <--

    init {
        viewModelScope.launchIO {
            val source = sourceManager.getOrStub(sourceId)
            state.update { it.copy(source = source) }

            setFilters(source.getFilterList())

            // KMK -->
            reloadSavedSearches()

            getFeedSavedSearchBySourceId.subscribe(source.id)
                .onEach {
                    val items = getSourcesToGetFeed(it)
                    state.update { state ->
                        state.copy(
                            items = items,
                        )
                    }
                    getFeed(items)
                }
                .launchIn(viewModelScope)
            // KMK <--
        }

        // KMK -->
        getIncognitoState.subscribe(sourceId)
            .onEach {
                if (!it) sourcePreferences.lastUsedSource.set(sourceId)
                incognitoMode.value = it
            }
            .launchIn(viewModelScope)
        // KMK <--
    }

    // KMK-->
    fun toggleIncognitoMode() {
        viewModelScope.launchIO {
            val packageName = when {
                source is StubSource -> null
                source?.isLocal() == true -> LOCAL_SOURCE_PACKAGE
                source?.isEhBasedSource() == true -> EH_PACKAGE
                else -> extensionManager.getExtensionPackage(sourceId)
            }
            packageName?.let {
                toggleIncognito.await(it, !incognitoMode.value)
            }
        }
    }

    fun resetFilters() {
        setFilters(source?.getFilterList() ?: FilterList())
        reloadSavedSearches()
    }
    // KMK <--

    fun setFilters(filters: FilterList) {
        state.update { it.copy(filters = filters) }
    }

    private suspend fun hasTooManyFeeds(): Boolean {
        val source = source ?: return false
        return countFeedSavedSearchBySourceId.await(source.id) > MaxFeedItems
    }

    fun createFeed(savedSearchId: Long) {
        viewModelScope.launchNonCancellable {
            val source = source ?: return@launchNonCancellable
            insertFeedSavedSearch.await(
                FeedSavedSearch(
                    id = -1,
                    source = source.id,
                    savedSearch = savedSearchId,
                    global = false,
                    feedOrder = 0,
                ),
            )
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        viewModelScope.launchNonCancellable {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    // KMK -->
    fun changeOrder(feed: FeedSavedSearch, newIndex: Int) {
        viewModelScope.launch {
            reorderFeed.changeOrder(feed, newIndex, false)
        }
    }
    // KMK <--

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): List<SourceFeedUI> {
        val source = source ?: return emptyList()
        val savedSearches = getSavedSearchBySourceIdFeed.await(source.id)
            .associateBy { it.id }

        return (
            listOfNotNull(
                if (source.supportsLatest) {
                    SourceFeedUI.Latest(null)
                } else {
                    null
                },
                SourceFeedUI.Browse(null),
            ) + feedSavedSearch
                .map { SourceFeedUI.SourceSavedSearch(it, savedSearches[it.savedSearch]!!, null) }
            )
            .toList()
    }

    // KMK -->
    private val hideInLibraryFeedItems = sourcePreferences.hideInLibraryFeedItems.get()
    // KMK <--

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<SourceFeedUI>) {
        viewModelScope.launch {
            val source = source ?: return@launch
            feedSavedSearch.map { sourceFeed ->
                async {
                    val page = try {
                        withContext(coroutineDispatcher) {
                            when (sourceFeed) {
                                is SourceFeedUI.Browse -> source.getPopularManga(1)
                                is SourceFeedUI.Latest -> source.getLatestUpdates(1)
                                is SourceFeedUI.SourceSavedSearch -> source.getSearchManga(
                                    page = 1,
                                    query = sourceFeed.savedSearch.query?.sanitize().orEmpty(),
                                    filters = getFilterList(sourceFeed.savedSearch, source),
                                )
                            }
                        }.mangas
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val titles = withIOContext {
                        page.map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }
                            // KMK -->
                            .filter { !hideInLibraryFeedItems || !it.favorite }
                        // KMK <--
                    }

                    state.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.id == sourceFeed.id) sourceFeed.withResults(titles) else item
                            }.toList(),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private val filterSerializer = FilterSerializer()

    private fun getFilterList(savedSearch: SavedSearch, source: Source): FilterList {
        val filters = savedSearch.filtersJson ?: return FilterList()
        return runCatching {
            val originalFilters = source.getFilterList()
            filterSerializer.deserialize(
                filters = originalFilters,
                json = Json.decodeFromString(filters),
            )
            originalFilters
        }.getOrElse { FilterList() }
    }

    @Composable
    fun getManga(initialManga: DomainManga): State<DomainManga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .collectLatest { manga ->
                    if (manga == null) return@collectLatest
                    value = manga
                }
        }
    }

    // KMK -->
    private fun reloadSavedSearches() {
        viewModelScope.launchIO {
            val searches = loadSearches()
            state.update { it.copy(savedSearches = searches) }
        }
    }
    // KMK <--

    private suspend fun loadSearches(): List<EXHSavedSearch> {
        val source = source ?: return emptyList()
        return getExhSavedSearch.await(source.id, source::getFilterList)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name))
            .toList()
    }

    fun onFilter(onBrowseClick: (query: String?, filters: String?) -> Unit) {
        viewModelScope.launchIO {
            val source = source ?: return@launchIO
            val allDefault = state.value.filters == source.getFilterList()
            dismissDialog()
            if (allDefault) {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    null,
                )
            } else {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    Json.encodeToString(filterSerializer.serialize(state.value.filters)),
                )
            }
        }
    }

    /** Open a saved search */
    fun onSavedSearch(
        // KMK -->
        loadedSearch: EXHSavedSearch,
        // KMK <--
        onBrowseClick: (query: String?, searchId: Long) -> Unit,
        onToast: (StringResource) -> Unit,
    ) {
        viewModelScope.launchIO {
            // KMK -->
            // KUN -->
            val source = source ?: return@launchIO
            // KUN <--
            val search = getExhSavedSearch.awaitOne(loadedSearch.id, source::getFilterList) ?: loadedSearch
            // KMK <--

            if (search.filterList == null && state.value.filters.isNotEmpty()) {
                withUIContext {
                    onToast(SYMR.strings.save_search_invalid)
                }
                return@launchIO
            }

            val allDefault = search.filterList != null && search.filterList == source.getFilterList()
            dismissDialog()

            if (!allDefault) {
                onBrowseClick(
                    state.value.searchQuery?.nullIfBlank(),
                    search.id,
                )
            }
        }
    }

    fun onSavedSearchAddToFeed(
        search: EXHSavedSearch,
        onToast: (StringResource) -> Unit,
    ) {
        viewModelScope.launchIO {
            if (hasTooManyFeeds()) {
                withUIContext {
                    onToast(KMR.strings.too_many_in_feed)
                }
                return@launchIO
            }
            openAddFeed(search.id, search.name)
        }
    }

    fun onMangaDexRandom(onRandomFound: (String) -> Unit) {
        viewModelScope.launchIO {
            val source = source ?: return@launchIO
            val random = source.getMainSource<MangaDex>()?.fetchRandomMangaUrl()
                ?: return@launchIO
            onRandomFound(random)
        }
    }

    fun search(query: String?) {
        state.update { it.copy(searchQuery = query) }
    }

    fun openFilterSheet() {
        state.update { it.copy(dialog = Dialog.Filter) }
    }

    fun openDeleteFeed(feed: FeedSavedSearch) {
        state.update { it.copy(dialog = Dialog.DeleteFeed(feed)) }
    }

    // KMK -->
    fun openActionsDialog(
        feed: SourceFeedUI.SourceSavedSearch,
    ) {
        viewModelScope.launchIO {
            state.update { state ->
                state.copy(
                    dialog = Dialog.FeedActions(
                        feedItem = feed,
                    ),
                )
            }
        }
    }

    fun showDialog(dialog: Dialog) {
        if (!state.value.isLoading) {
            state.update {
                it.copy(dialog = dialog)
            }
        }
    }
    // KMK <--

    private fun openAddFeed(feedId: Long, name: String) {
        state.update { it.copy(dialog = Dialog.AddFeed(feedId, name)) }
    }

    fun dismissDialog() {
        state.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        data object Filter : Dialog()
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()
        data class AddFeed(val feedId: Long, val name: String) : Dialog()

        // KMK -->
        data class FeedActions(
            val feedItem: SourceFeedUI.SourceSavedSearch,
        ) : Dialog()
        // KMK <--
    }

    // KUN -->
    // No onCleared() needed: Dispatchers.IO.limitedParallelism requires no explicit shutdown.
    // KUN <--
}

@Immutable
data class SourceFeedState(
    val searchQuery: String? = null,
    val source: Source? = null,
    val items: List<SourceFeedUI> = listOf(),
    val filters: FilterList = FilterList(),
    val savedSearches: List<EXHSavedSearch> = listOf(),
    val dialog: SourceFeedViewModel.Dialog? = null,
) {
    val isLoading
        get() = items.isEmpty()
}
