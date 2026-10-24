@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.FeedItemUI
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.SubscriberGate
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.ObserveSavedSearches
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import kotlin.time.Duration.Companion.seconds
import tachiyomi.domain.manga.model.Manga as DomainManga

/**
 * Presenter of [feedTab]
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class FeedViewModel(
    val sourceManager: SourceManager,
    val sourcePreferences: SourcePreferences,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
    getFeedSavedSearchGlobal: GetFeedSavedSearchGlobal,
    private val countFeedSavedSearchGlobal: CountFeedSavedSearchGlobal,
    private val getSavedSearchBySourceId: GetSavedSearchBySourceId,
    private val insertFeedSavedSearch: InsertFeedSavedSearch,
    private val deleteFeedSavedSearchById: DeleteFeedSavedSearchById,
    // KNS --> Rebuild feed items when a referenced saved search is overwritten in place.
    observeSavedSearches: ObserveSavedSearches,
    // KNS <--
    // KMK -->
    private val reorderFeed: ReorderFeed,
    // KMK <--
) : ViewModel() {

    private val subscriberGate = SubscriberGate()

    private val mutableState = MutableStateFlow<FeedScreenState>(FeedScreenState())

    // Keeps the feed while it's hidden, but holds back database updates until it's shown again
    val state: StateFlow<FeedScreenState> = mutableState
        .stateIn(viewModelScope + subscriberGate, subscriberGate.whileSubscribed(5.seconds), FeedScreenState())

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    // KUN -->
    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    // KUN <--
    // KNS --> Cancel superseded work and reject a late result even if a source ignores cancellation.
    private val feedJobs = mutableMapOf<Long, Job>()
    private val latestFeedResults = LatestFeedResults()
    // KNS <--
    var pushed: Boolean = false

    init {
        // KNS --> Observe effective configurations and reload only rows whose configuration changed.
        observeSavedSearches.resolve(getFeedSavedSearchGlobal.subscribe())
            .onEach { resolvedFeeds ->
                val previousItems = mutableState.value.items.orEmpty().associateBy { it.feed.id }
                val items = resolvedFeeds.map { resolved ->
                    val feed = resolved.feed
                    val savedSearch = resolved.savedSearch
                    val previous = previousItems[feed.id]
                    createCatalogueSearchItem(
                        feed = feed,
                        savedSearch = savedSearch,
                        source = sourceManager.get(feed.source),
                        results = if (previous?.feed == feed && previous.savedSearch == savedSearch) {
                            previous.results
                        } else {
                            null
                        },
                    )
                }
                val itemsToRefresh = items.filter { item ->
                    val previous = previousItems[item.feed.id]
                    previous == null || previous.feed != item.feed || previous.savedSearch != item.savedSearch
                }
                val currentIds = items.mapTo(mutableSetOf()) { it.feed.id }
                (feedJobs.keys - currentIds).forEach { removedId ->
                    feedJobs.remove(removedId)?.cancel()
                    latestFeedResults.forget(removedId)
                }
                mutableState.update { state ->
                    state.copy(
                        items = items
                            // KMK -->
                            .toList(),
                        // KMK <--
                    )
                }
                getFeed(itemsToRefresh)
            }
            .catch { _events.send(Event.FailedFetchingSources) }
            .launchIn(viewModelScope + subscriberGate)
        // KNS <--
    }

    fun init() {
        pushed = false
        // KNS --> Keep request bookkeeping on the ViewModel's main dispatcher.
        viewModelScope.launch {
            val newItems = mutableState.value.items?.map { it.copy(results = null) } ?: return@launch
            mutableState.update { state ->
                state.copy(
                    items = newItems
                        // KMK -->
                        .toList(),
                    // KMK <--
                )
            }
            getFeed(newItems)
        }
        // KNS <--
    }

    fun openAddDialog() {
        viewModelScope.launchIO {
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
        viewModelScope.launchIO {
            mutableState.update { state ->
                state.copy(
                    dialog = Dialog.AddFeedSearch(
                        source,
                        (
                            // KMK -->
                            // (if (source.supportsLatest) listOf(null) else listOf()) +
                            listOf(null) +
                                // KMK <-->
                                getSourceSavedSearches(source.id)
                            ).toList(),
                    ),
                )
            }
        }
    }

    fun openDeleteDialog(feed: FeedSavedSearch) {
        viewModelScope.launchIO {
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
        viewModelScope.launchIO {
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

    private suspend fun getEnabledSources(): List<Source> {
        val languages = sourcePreferences.enabledLanguages.get()
        val pinnedSources = sourcePreferences.pinnedSources.get()
        val disabledSources = sourcePreferences.disabledSources.get()
            .mapNotNull { it.toLongOrNull() }

        val list = sourceManager.getVisibleSources()
            .filter { it.lang in languages }
            .filterNot { it.id in disabledSources }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { "(${it.lang}) ${it.name}" })

        return list.sortedBy { it.id.toString() !in pinnedSources }.toList()
    }

    private suspend fun getSourceSavedSearches(sourceId: Long): List<SavedSearch> {
        return getSavedSearchBySourceId.await(sourceId).toList()
    }

    fun createFeed(source: Source, savedSearch: SavedSearch?) {
        viewModelScope.launchNonCancellable {
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
        viewModelScope.launchNonCancellable {
            deleteFeedSavedSearchById.await(feed.id)
        }
    }

    // KMK -->
    fun changeOrder(feed: FeedSavedSearch, newIndex: Int) {
        viewModelScope.launch {
            reorderFeed.changeOrder(feed, newIndex)
        }
    }
    // KMK <--

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
    private val hideInLibraryFeedItems = sourcePreferences.hideInLibraryFeedItems
    // KMK <--

    /**
     * Initiates get manga per feed.
     */
    private fun getFeed(feedSavedSearch: List<FeedItemUI>) {
        // KNS --> One cancellable, generation-checked job per effective feed configuration.
        feedSavedSearch.forEach { itemUI ->
            val configuration = FeedRequestConfiguration(
                sourceId = itemUI.feed.source,
                savedSearch = itemUI.savedSearch,
                kind = if (itemUI.savedSearch != null) {
                    FeedRequestConfiguration.Kind.SAVED_SEARCH
                } else if (itemUI.source?.supportsLatest == true) {
                    FeedRequestConfiguration.Kind.LATEST
                } else {
                    FeedRequestConfiguration.Kind.POPULAR
                },
            )
            val request = latestFeedResults.start(itemUI.feed.id, configuration)
            feedJobs.remove(itemUI.feed.id)?.cancel()
            feedJobs[itemUI.feed.id] = viewModelScope.launch {
                val page = try {
                    if (itemUI.source != null) {
                        withContext(coroutineDispatcher) {
                            if (itemUI.savedSearch == null) {
                                // KMK -->
                                if (itemUI.source.supportsLatest) {
                                    // KMK <--
                                    itemUI.source.getLatestUpdates(1)
                                    // KMK -->
                                } else {
                                    itemUI.source.getPopularManga(1)
                                }
                                // KMK <--
                            } else {
                                itemUI.source.getSearchManga(
                                    1,
                                    itemUI.savedSearch.query?.sanitize().orEmpty(),
                                    getFilterList(itemUI.savedSearch, itemUI.source),
                                )
                            }
                        }.mangas
                    } else {
                        emptyList()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }

                val result = withIOContext {
                    itemUI.copy(
                        results = page
                            .map { it.toDomainManga(itemUI.source!!.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }
                            // KMK -->
                            .filter { !hideInLibraryFeedItems.get() || !it.favorite },
                        // KMK <--
                    )
                }

                mutableState.update { state ->
                    val activeItem = state.items?.firstOrNull { it.feed.id == request.feedId }
                    val activeConfiguration = activeItem?.let {
                        FeedRequestConfiguration(
                            sourceId = it.feed.source,
                            savedSearch = it.savedSearch,
                            kind = if (it.savedSearch != null) {
                                FeedRequestConfiguration.Kind.SAVED_SEARCH
                            } else if (it.source?.supportsLatest == true) {
                                FeedRequestConfiguration.Kind.LATEST
                            } else {
                                FeedRequestConfiguration.Kind.POPULAR
                            },
                        )
                    }
                    val acceptedResult = latestFeedResults.accept(request, activeConfiguration, result)
                        ?: return@update state
                    state.copy(
                        items = state.items?.map {
                            if (
                                it.feed.id == acceptedResult.feed.id &&
                                it.feed == acceptedResult.feed &&
                                it.savedSearch == acceptedResult.savedSearch
                            ) {
                                acceptedResult
                            } else {
                                it
                            }
                        }
                            // KMK -->
                            ?.toList(),
                        // KMK <--
                    )
                }
            }
        }
        // KNS <--
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
    // KUN -->
    // No onCleared() needed: Dispatchers.IO.limitedParallelism requires no explicit shutdown.
    // KUN <--

    // KMK -->
    fun showDialog(dialog: Dialog) {
        if (!mutableState.value.isLoading) {
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
        data class AddFeed(val options: List<Source>) : Dialog()
        data class AddFeedSearch(val source: Source, val options: List<SavedSearch?>) : Dialog()
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
    val dialog: FeedViewModel.Dialog? = null,
    val items: List<FeedItemUI>? = null,
) {
    val isLoading
        get() = items == null

    val isEmpty
        get() = items.isNullOrEmpty()

    val isLoadingItems
        get() = items?.fastAny { it.results == null } != false
}

const val MaxFeedItems = 20
