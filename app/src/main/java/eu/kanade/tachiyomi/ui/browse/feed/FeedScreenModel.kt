@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.ui.browse.feed

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.util.fastAny
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.FeedItemUI
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.network.FreshNetworkRequests
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchGlobalFeed
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors
import tachiyomi.domain.manga.model.Manga as DomainManga

/**
 * Presenter of [feedTab]
 */
open class FeedScreenModel(
    val sourceManager: SourceManager = Injekt.get(),
    val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchGlobalFeed: GetSavedSearchGlobalFeed = Injekt.get(),
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal = Injekt.get(),
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId = Injekt.get(),
    private val insertFeedSavedSearch: InsertFeedSavedSearch = Injekt.get(),
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById = Injekt.get(),
    // KMK -->
    private val reorderFeed: ReorderFeed = Injekt.get(),
    private val coroutineDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher(),
    private val formatError: (Throwable) -> String = { with(Injekt.get<Application>()) { if (it is FeedSearch.InvalidSavedSearch) stringResource(KMR.strings.feed_saved_search_invalid) else it.formattedMessage } },
    // KMK <--
) : StateScreenModel<FeedScreenState>(FeedScreenState()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    var pushed: Boolean = false

    init {
        getFeedSavedSearchGlobal.subscribe()
            .distinctUntilChanged()
            .transformLatest { configured ->
                // Invalidate the previous configuration before awaiting its replacement metadata.
                mutableState.update { state -> state.copy(items = state.items?.map { it.copy(request = FeedRequest(), loading = true) }?.toImmutableList()) }
                sourceManager.isInitialized.first { it }
                val items = getSourcesToGetFeed(configured).map { (feed, savedSearch) ->
                    createCatalogueSearchItem(
                        feed = feed,
                        savedSearch = savedSearch,
                        source = sourceManager.get(feed.source),
                        results = state.value.items?.find { it.feed == feed && it.savedSearch == savedSearch }?.results,
                    )
                }
                mutableState.update { state ->
                    state.copy(
                        refreshing = false,
                        items = items
                            // KMK -->
                            .toImmutableList(),
                        // KMK <--
                    )
                }
                getFeed(items)
                emit(Unit)
            }
            .catch { _events.send(Event.FailedFetchingSources) }
            .launchIn(screenModelScope)
    }

    fun init() {
        pushed = false
        if (!state.value.isLoadingItems) refreshAll()
    }

    fun refreshAll() {
        val items = state.value.items?.takeIf { it.isNotEmpty() } ?: return
        mutableState.update { it.copy(refreshing = true) }
        getFeed(items, forceRefresh = true)
    }

    fun openAddDialog() {
        screenModelScope.launchIO {
            if (hasTooManyFeeds()) {
                _events.send(Event.TooManyFeeds)
                return@launchIO
            }
            mutableState.update { state ->
                state.copy(
                    dialog = Dialog.AddFeed(getEnabledSources()),
                )
            }
        }
    }

    fun openAddSearchDialog(source: Source) {
        screenModelScope.launchIO {
            mutableState.update { state ->
                state.copy(
                    dialog = Dialog.AddFeedSearch(
                        source,
                        (
                            // KMK -->
                            // (if (source.supportsLatest) persistentListOf(null) else persistentListOf()) +
                            persistentListOf(null) +
                                // KMK <-->
                                getSourceSavedSearches(source.id)
                            ).toImmutableList(),
                    ),
                )
            }
        }
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        screenModelScope.launchIO {
            mutableState.update { state ->
                state.copy(
                    dialog = Dialog.DeleteFeed(feed),
                )
            }
        }
    }

    // KMK -->
    fun openActionsDialog(
        feed: FeedItemUI,
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
    // KMK <--

    private suspend fun hasTooManyFeeds(): Boolean {
        return countFeedSavedSearchGlobal.await() > MaxFeedItems
    }

    private fun getEnabledSources(): ImmutableList<Source> {
        val languages = sourcePreferences.enabledLanguages().get()
        val pinnedSources = sourcePreferences.pinnedSources().get()
        val disabledSources = sourcePreferences.disabledSources().get()
            .mapNotNull { it.toLongOrNull() }

        val list = sourceManager.getVisibleSources()
            .filter { it.lang in languages }
            .filterNot { it.id in disabledSources }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { "(${it.lang}) ${it.name}" })

        return list.sortedBy { it.id.toString() !in pinnedSources }.toImmutableList()
    }

    private suspend fun getSourceSavedSearches(sourceId: Long): ImmutableList<SavedSearch> {
        return getSavedSearchBySourceId.await(sourceId).toImmutableList()
    }

    fun createFeed(source: Source, savedSearch: SavedSearch?) {
        screenModelScope.launchNonCancellable {
            insertFeedSavedSearch.await(
                FeedSavedSearch(
                    id = -1,
                    source = source.id,
                    savedSearch = savedSearch?.id,
                    global = true,
                    feedOrder = 0,
                ),
            )
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
            reorderFeed.changeOrder(feed, newIndex)
        }
    }
    // KMK <--

    private suspend fun getSourcesToGetFeed(feedSavedSearch: List<FeedSavedSearch>): List<Pair<FeedSavedSearch, SavedSearch?>> {
        val savedSearches = getSavedSearchGlobalFeed.await()
            .associateBy { it.id }
        return feedSavedSearch
            .map { it to savedSearches[it.savedSearch] }
    }

    /**
     * Creates a catalogue search item
     */
    private fun createCatalogueSearchItem(
        feed: FeedSavedSearch,
        savedSearch: SavedSearch?,
        source: Source?,
        results: List<DomainManga>?,
    ): FeedItemUI {
        return FeedItemUI(
            feed,
            savedSearch,
            source,
            savedSearch?.name ?: (source?.name ?: feed.source.toString()),
            if (savedSearch != null) {
                source?.name ?: feed.source.toString()
            } else {
                LocaleHelper.getLocalizedDisplayName(source?.lang)
            },
            results,
        )
    }

    // KMK -->
    private val hideInLibraryFeedItems = sourcePreferences.hideInLibraryFeedItems()
    // KMK <--

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<FeedItemUI>, forceRefresh: Boolean = false) {
        // Publish ownership before scheduling work, including refreshes while a row is loading.
        val requests = feedSavedSearch.map { it.copy(loading = true, error = null, request = FeedRequest()) }
        val byId = requests.associateBy { it.feed.id }
        mutableState.update { state -> state.copy(items = state.items?.map { byId[it.feed.id] ?: it }?.toImmutableList()) }
        screenModelScope.launch {
            requests.map { itemUI ->
                async {
                    val result = feedRequest(
                        onError = { e -> itemUI.copy(loading = false, error = formatError(e)) },
                    ) {
                        sourceManager.isInitialized.first { it }
                        val requestSource = sourceManager.get(itemUI.feed.source)
                            ?: throw tachiyomi.domain.source.model.SourceNotInstalledException()
                        val savedSearch = itemUI.feed.savedSearch?.let { id ->
                            getSavedSearchGlobalFeed.await().find { it.id == id && it.source == itemUI.feed.source }
                                ?: throw FeedSearch.InvalidSavedSearch()
                        }
                        val page = withContext(coroutineDispatcher + FreshNetworkRequests.context(forceRefresh)) {
                            if (savedSearch == null) {
                                // KMK -->
                                if (requestSource.supportsLatest) {
                                    // KMK <--
                                    requestSource.getLatestUpdates(1)
                                    // KMK -->
                                } else {
                                    requestSource.getPopularManga(1)
                                }
                                // KMK <--
                            } else {
                                FeedSearch.fetch(requestSource, savedSearch)
                            }
                        }.mangas

                        // Obsolete responses must not update card subscriptions through the database either.
                        if (state.value.items?.none { it.feed.id == itemUI.feed.id && it.request === itemUI.request } != false) return@feedRequest null
                        withContext(coroutineDispatcher) {
                            if (state.value.items?.none { it.feed.id == itemUI.feed.id && it.request === itemUI.request } != false) return@withContext null
                            itemUI.copy(
                                source = requestSource,
                                savedSearch = savedSearch,
                                loading = false,
                                error = null,
                                results = page
                                    .map { it.toDomainManga(requestSource.id) }
                                    .distinctBy { it.url }
                                    .let { networkToLocalManga(it) }
                                    // KMK -->
                                    .filter { !hideInLibraryFeedItems.get() || !it.favorite },
                                // KMK <--
                            )
                        }
                    } ?: return@async

                    mutableState.update { state ->
                        val items = state.items?.map { if (it.feed.id == itemUI.feed.id && it.request === itemUI.request) result else it }?.toImmutableList()
                        state.copy(items = items, refreshing = state.refreshing && items?.any { it.loading } == true)
                    }
                }
            }.awaitAll()
        }
    }

    fun retry(item: FeedItemUI) {
        val current = state.value.items?.find { it.feed.id == item.feed.id } ?: return
        if (current.loading) return
        getFeed(listOf(current), forceRefresh = true)
    }

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
    override fun onDispose() {
        super.onDispose()
        (coroutineDispatcher as? ExecutorCoroutineDispatcher)?.close()
    }

    // KMK -->
    fun showDialog(dialog: Dialog) {
        if (!state.value.isLoading) {
            mutableState.update {
                it.copy(dialog = dialog)
            }
        }
    }
    // KMK <--

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        data class AddFeed(val options: ImmutableList<Source>) : Dialog()
        data class AddFeedSearch(val source: Source, val options: ImmutableList<SavedSearch?>) : Dialog()
        data class DeleteFeed(val feed: FeedSavedSearch) : Dialog()

        // KMK -->
        data class FeedActions(
            val feedItem: FeedItemUI,
        ) : Dialog()
        // KMK <--
    }

    sealed class Event {
        data object FailedFetchingSources : Event()
        data object TooManyFeeds : Event()
    }
}

data class FeedScreenState(
    val dialog: FeedScreenModel.Dialog? = null,
    val items: ImmutableList<FeedItemUI>? = null,
    val refreshing: Boolean = false,
) {
    val isLoading
        get() = items == null

    val isEmpty
        get() = items.isNullOrEmpty()

    val isLoadingItems
        get() = items?.fastAny { it.loading } != false
}

const val MaxFeedItems = 20
