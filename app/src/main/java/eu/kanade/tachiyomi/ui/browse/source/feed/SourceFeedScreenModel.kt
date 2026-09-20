package eu.kanade.tachiyomi.ui.browse.source.feed

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.FreshNetworkRequests
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.feed.FeedRequest
import eu.kanade.tachiyomi.ui.browse.feed.FeedSearch
import eu.kanade.tachiyomi.ui.browse.feed.MaxFeedItems
import eu.kanade.tachiyomi.ui.browse.feed.feedRequest
import exh.source.EH_PACKAGE
import exh.source.LOCAL_SOURCE_PACKAGE
import exh.source.getMainSource
import exh.source.isEhBasedSource
import exh.source.mangaDexSourceIds
import exh.util.nullIfBlank
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.interactor.CountFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.interactor.UpdateFeedSavedSearch
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearchUpdate
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.util.concurrent.Executors
import tachiyomi.domain.manga.model.Manga as DomainManga

open class SourceFeedScreenModel(
    val sourceId: Long,
    uiPreferences: UiPreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    getFeedSavedSearchBySourceId: GetFeedSavedSearchBySourceId = Injekt.get(),
    private val getSavedSearchBySourceIdFeed: GetSavedSearchBySourceIdFeed = Injekt.get(),
    private val countFeedSavedSearchBySourceId: CountFeedSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val updateFeedSavedSearch: UpdateFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    private val getExhSavedSearch: GetExhSavedSearch = Injekt.get(),
    // KMK -->
    private val reorderFeed: ReorderFeed = Injekt.get(),
    getIncognitoState: GetIncognitoState = Injekt.get(),
    private val toggleIncognito: ToggleIncognito = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val coroutineDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher(),
    private val formatError: (Throwable) -> String = { with(Injekt.get<Application>()) { if (it is FeedSearch.InvalidSavedSearch) stringResource(KMR.strings.feed_saved_search_invalid) else it.formattedMessage } },
    // KMK <--
) : StateScreenModel<SourceFeedState>(SourceFeedState()) {

    var source = sourceManager.getOrStub(sourceId)

    val sourceIsMangaDex = sourceId in mangaDexSourceIds

    val startExpanded by uiPreferences.expandFilters().asState(screenModelScope)

    // KMK -->
    var incognitoMode = mutableStateOf(getIncognitoState.await(source.id))
    // KMK <--

    private var filtersInitialized = false

    init {
        // KMK -->

        getIncognitoState.subscribe(sourceId)
            .onEach {
                if (!it) sourcePreferences.lastUsedSource().set(source.id)
                incognitoMode.value = it
            }
            .launchIn(screenModelScope)
        // KMK <--

        getFeedSavedSearchBySourceId.subscribe(source.id)
            .transformLatest { configured ->
                mutableState.update { state -> state.copy(items = state.items.map { it.withResults(it.results, loading = true, request = FeedRequest()) }.toImmutableList()) }
                sourceManager.isInitialized.first { it }
                source = sourceManager.getOrStub(sourceId)
                if (!filtersInitialized) {
                    val filters = FeedSearch.withSource(source) { FeedSearch.uiFilters(source) }
                    val searches = loadSearches()
                    mutableState.update { state -> state.copy(filters = filters, savedSearches = searches) }
                    filtersInitialized = true
                }
                val items = getSourcesToGetFeed(configured).map { item ->
                    item.withResults(state.value.items.find { it.id == item.id }?.results, loading = true)
                }.toImmutableList()
                mutableState.update { state ->
                    state.copy(
                        items = items,
                    )
                }
                getFeed(items)
                emit(Unit)
            }
            .launchIn(screenModelScope)
    }

    // KMK-->
    fun toggleIncognitoMode() {
        val packageName = when {
            source is StubSource -> null
            source.isLocal() -> LOCAL_SOURCE_PACKAGE
            source.isEhBasedSource() -> EH_PACKAGE
            else -> extensionManager.getExtensionPackage(sourceId)
        }
        packageName?.let {
            toggleIncognito.await(it, !incognitoMode.value)
        }
    }

    fun resetFilters() {
        screenModelScope.launch {
            setFilters(FeedSearch.withSource(source) { FeedSearch.uiFilters(source) })
            reloadSavedSearches()
        }
    }
    // KMK <--

    fun setFilters(filters: FilterList) {
        mutableState.update { it.copy(filters = filters) }
    }

    private suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchBySourceId.await(source.id) > MaxFeedItems
    }

    fun createFeed(savedSearchId: Long) {
        val sourceId = source.id

        screenModelScope.launchNonCancellable {
            val feeds: List<FeedSavedSearch> =
                Injekt.get<GetFeedSavedSearchBySourceId>().await(sourceId)

            val existing: FeedSavedSearch? = feeds.find { feed: FeedSavedSearch ->
                feed.savedSearch == savedSearchId && !feed.global
            }

            if (existing != null) {
                updateFeedSavedSearch.await(
                    FeedSavedSearchUpdate(
                        id = existing.id,
                        source = sourceId,
                        savedSearch = savedSearchId,
                        global = false,
                        feedOrder = existing.feedOrder,
                    ),
                )
            } else {
                insertFeedSavedSearch.await(
                    FeedSavedSearch(
                        id = -1,
                        source = sourceId,
                        savedSearch = savedSearchId,
                        global = false,
                        feedOrder = 0,
                    ),
                )
            }
        }
    }

    fun deleteFeed(feed: FeedSavedSearch) {
        screenModelScope.launchNonCancellable {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    // KMK -->
    fun changeOrder(feed: FeedSavedSearch, newIndex: Int) {
        screenModelScope.launch {
            reorderFeed.changeOrder(feed, newIndex, false)
        }
    }
    // KMK <--

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): ImmutableList<SourceFeedUI> {
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
                .mapNotNull { feed -> savedSearches[feed.savedSearch]?.let { SourceFeedUI.SourceSavedSearch(feed, it, null) } }
            )
            .toImmutableList()
    }

    // KMK -->
    private val hideInLibraryFeedItems = sourcePreferences.hideInLibraryFeedItems().get()
    // KMK <--

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<SourceFeedUI>, forceRefresh: Boolean = false) {
        val requests = feedSavedSearch.map { it.withResults(it.results, loading = true, request = FeedRequest()) }
        val byId = requests.associateBy { it.id }
        mutableState.update { state -> state.copy(items = state.items.map { byId[it.id] ?: it }.toImmutableList()) }
        screenModelScope.launch {
            requests.map { sourceFeed ->
                async {
                    val result = feedRequest(
                        onError = { e -> sourceFeed.withResults(sourceFeed.results, formatError(e)) },
                    ) {
                        sourceManager.isInitialized.first { it }
                        val requestSource = sourceManager.getOrStub(sourceId)
                        val search = if (sourceFeed is SourceFeedUI.SourceSavedSearch) {
                            getSavedSearchBySourceIdFeed.await(sourceId).find { it.id == sourceFeed.savedSearch.id }
                                ?: throw FeedSearch.InvalidSavedSearch()
                        } else {
                            null
                        }
                        val page = run {
                            withContext(coroutineDispatcher + FreshNetworkRequests.context(forceRefresh)) {
                                when (sourceFeed) {
                                    is SourceFeedUI.Browse -> requestSource.getPopularManga(1)
                                    is SourceFeedUI.Latest -> requestSource.getLatestUpdates(1)
                                    is SourceFeedUI.SourceSavedSearch -> FeedSearch.fetch(requestSource, requireNotNull(search))
                                }
                            }.mangas
                        }

                        if (state.value.items.none { it.id == sourceFeed.id && it.request === sourceFeed.request }) return@feedRequest null
                        val titles = withContext(coroutineDispatcher) {
                            if (state.value.items.none { it.id == sourceFeed.id && it.request === sourceFeed.request }) return@withContext null
                            page.map { it.toDomainManga(requestSource.id) }
                                .distinctBy { it.url }
                                .let { networkToLocalManga(it) }
                                // KMK -->
                                .filter { !hideInLibraryFeedItems || !it.favorite }
                            // KMK <--
                        }

                        sourceFeed.withResults(titles ?: return@feedRequest null)
                    } ?: return@async

                    mutableState.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.id == sourceFeed.id && item.request === sourceFeed.request) result else item
                            }.toImmutableList(),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    fun retry(item: SourceFeedUI) {
        val current = state.value.items.find { it.id == item.id } ?: return
        if (current.loading) return
        getFeed(listOf(current), forceRefresh = true)
    }

    fun refreshAll() {
        getFeed(state.value.items, forceRefresh = true)
    }

    private val filterSerializer = FilterSerializer()

    @Composable
    fun getManga(initialManga: DomainManga): State<DomainManga> {
        return produceState(initialValue = initialManga, key1 = initialManga) {
            value = initialManga
            getManga.subscribe(initialManga.url, initialManga.source)
                .collectLatest { manga ->
                    if (manga == null) return@collectLatest
                    value = manga
                }
        }
    }

    // KMK -->
    private fun reloadSavedSearches() {
        screenModelScope.launchIO {
            val searches = loadSearches()
            mutableState.update { it.copy(savedSearches = searches) }
        }
    }
    // KMK <--

    private suspend fun loadSearches() = FeedSearch.withSource(source) {
        getExhSavedSearch.await(source.id) { FeedSearch.uiFilters(source) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name))
            .toImmutableList()
    }

    fun onFilter(onBrowseClick: (query: String?, filters: String?) -> Unit) {
        screenModelScope.launchIO {
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
        screenModelScope.launchIO {
            // KMK -->
            val search = FeedSearch.withSource(source) {
                getExhSavedSearch.awaitOne(loadedSearch.id) { FeedSearch.uiFilters(source) }
            } ?: loadedSearch
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
        screenModelScope.launchIO {
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
        screenModelScope.launchIO {
            val random = source.getMainSource<MangaDex>()?.fetchRandomMangaUrl()
                ?: return@launchIO
            onRandomFound(random)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openFilterSheet() {
        mutableState.update { it.copy(dialog = Dialog.Filter) }
    }

    fun openDeleteFeed(feed: FeedSavedSearch) {
        mutableState.update { it.copy(dialog = Dialog.DeleteFeed(feed)) }
    }

    // KMK -->
    fun openActionsDialog(
        feed: SourceFeedUI.SourceSavedSearch,
    ) {
        screenModelScope.launchIO {
            mutableState.update { state ->
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
            mutableState.update {
                it.copy(dialog = dialog)
            }
        }
    }
    // KMK <--

    private fun openAddFeed(feedId: Long, name: String) {
        mutableState.update { it.copy(dialog = Dialog.AddFeed(feedId, name)) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
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

    override fun onDispose() {
        super.onDispose()
        (coroutineDispatcher as? ExecutorCoroutineDispatcher)?.close()
    }
}

@Immutable
data class SourceFeedState(
    val searchQuery: String? = null,
    val items: ImmutableList<SourceFeedUI> = persistentListOf(),
    val filters: FilterList = FilterList(),
    val savedSearches: ImmutableList<EXHSavedSearch> = persistentListOf(),
    val dialog: SourceFeedScreenModel.Dialog? = null,
) {
    val isLoading
        get() = items.isEmpty()
}
