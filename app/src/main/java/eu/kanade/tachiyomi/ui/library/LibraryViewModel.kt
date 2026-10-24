package eu.kanade.tachiyomi.ui.library

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.manga.interactor.SmartSearchMerge
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.sync.SyncStateBumper
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.workManager
import exh.favorites.FavoritesSyncHelper
import exh.log.xLogE
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.recs.batch.RecommendationSearchHelper
import exh.source.EH_SOURCE_ID
import exh.source.ExhPreferences
import exh.source.MANGADEX_IDS
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import exh.source.isMetadataSource
import exh.source.mangaDexSourceIds
import exh.source.nHentaiSourceIds
import exh.util.isLewd
import exh.util.nullIfBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mihon.core.common.utils.mutate
import mihon.core.metro.AppCoroutineScope
import mihon.domain.library.model.search.AndNode
import mihon.domain.library.model.search.ComparisonQueryNode
import mihon.domain.library.model.search.EmptyQueryNode
import mihon.domain.library.model.search.FieldQueryNode
import mihon.domain.library.model.search.GeneralQueryNode
import mihon.domain.library.model.search.LibraryFieldQueryNode
import mihon.domain.library.model.search.MetadataQueryNode
import mihon.domain.library.model.search.NotNode
import mihon.domain.library.model.search.OrNode
import mihon.domain.library.model.search.QueryNode
import mihon.feature.library.LibrarySearchMetadata
import mihon.feature.library.containsMetadataQuery
import mihon.feature.library.matches
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.SubscriberGate
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.pausedWhileUnsubscribed
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.Category.Companion.UNCATEGORIZED_ID
import tachiyomi.domain.category.service.CategoryTree
import tachiyomi.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetLibraryBookmarkColors
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.ChapterVisibility
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.SubcategoryUpdateScope
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.SetMangaUserInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.manga.model.toMangaUserInfoUpdate
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import tachiyomi.domain.source.model.Source as DomainSource

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class LibraryViewModel(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val getLibraryManga: GetLibraryManga,
    private val getCategories: GetCategories,
    private val getTracksPerManga: GetTracksPerManga,
    private val getNextChapters: GetNextChapters,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getBookmarkedChaptersByMangaId: GetBookmarkedChaptersByMangaId,
    private val setReadStatus: SetReadStatus,
    private val updateManga: UpdateManga,
    private val setMangaCategories: SetMangaCategories,
    private val preferences: BasePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val coverCache: CoverCache,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val trackerManager: TrackerManager,
    // SY -->
    private val exhPreferences: ExhPreferences,
    private val sourcePreferences: SourcePreferences,
    private val getMergedMangaById: GetMergedMangaById,
    private val getTracks: GetTracks,
    private val setMangaUserInfo: SetMangaUserInfo,
    // KUN -->
    private val getManga: GetManga,
    // KUN <--
    // KUN -->
    private val syncStateBumper: SyncStateBumper,
    // KUN <--
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    syncPreferences: SyncPreferences,
    val favoritesSync: FavoritesSyncHelper,
    val recommendationSearch: RecommendationSearchHelper,
    // SY <--
    // KMK -->
    private val smartSearchMerge: SmartSearchMerge,
    // KUN -->
    private val getFlatMetadataById: GetFlatMetadataById,
    // KUN <--
    private val getIdsOfFavoriteMangaWithMetadata: GetIdsOfFavoriteMangaWithMetadata,
    // KMK <--
    // KNS -->
    private val chapterVisibility: ChapterVisibility,
    private val libraryVisibleCounts: LibraryVisibleCounts,
    private val getLibraryBookmarkColors: GetLibraryBookmarkColors,
    // KNS <--
) : ViewModel() {

    // SY -->
    private var recommendationSearchJob: Job? = null
    // SY <--

    private val subscriberGate = SubscriberGate()

    private val searchQuery = MutableStateFlow<String?>(null)

    private val selection = MutableStateFlow(emptySet</* Manga */ Long>())

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val activeCategoryIndex = MutableStateFlow(libraryPreferences.lastUsedCategory.get())

    // KMK -->
    private val activeCategoryId = MutableStateFlow<Long?>(null)

    // KNS -->
    /** Per top-level tab, the subcategory whose subtree is shown (absent = the whole tab). */
    private val subcategorySelection = MutableStateFlow<Map<Long, Long>>(emptyMap())
    // KNS <--
    // KMK <--

    // SY -->
    private val groupType = MutableStateFlow(libraryPreferences.groupLibraryBy.get())
    private val showSyncExh = MutableStateFlow(false)
    private val isSyncEnabled = MutableStateFlow(false)
    // SY <--

    // KMK -->
    private val filterCategory = MutableStateFlow(false)
    private val includedCategories = MutableStateFlow(setOf<Long>())
    private val excludedCategories = MutableStateFlow(setOf<Long>())
    // KMK <--

    init {
        // SY -->
        combine(
            exhPreferences.isHentaiEnabled.changes(),
            sourcePreferences.disabledSources.changes(),
            exhPreferences.enableExhentai.changes(),
        ) { isHentaiEnabled, disabledSources, enableExhentai ->
            isHentaiEnabled && (EH_SOURCE_ID.toString() !in disabledSources || enableExhentai)
        }
            .distinctUntilChanged()
            .onEach { showSyncExh.value = it }
            .launchIn(viewModelScope)

        libraryPreferences.groupLibraryBy.changes()
            .onEach { groupType.value = it }
            .launchIn(viewModelScope)

        syncPreferences.syncService.changes()
            .distinctUntilChanged()
            .onEach { isSyncEnabled.value = it != SyncManager.SyncService.NONE.value }
            .launchIn(viewModelScope)
        // SY <--

        // KMK -->
        combine(
            libraryPreferences.filterCategories.changes(),
            libraryPreferences.filterCategoriesInclude.changes(),
            libraryPreferences.filterCategoriesExclude.changes(),
        ) { filter, included, excluded ->
            Triple(
                filter,
                included.mapNotNull(String::toLongOrNull).toSet(),
                excluded.mapNotNull(String::toLongOrNull).toSet(),
            )
        }
            .distinctUntilChanged()
            .onEach { (filter, included, excluded) ->
                filterCategory.value = filter
                includedCategories.value = included
                excludedCategories.value = excluded
            }
            .launchIn(viewModelScope)

        // The DMCA uuid list backs the `mangadex-dmca` search; without this it stays empty.
        viewModelScope.launchIO {
            if (mangaDexDmcaUuids.isEmpty()) {
                mangaDexDmcaUuids = loadMangaDexDmcaUuids(context = preferences.context)
            }
        }
        // KMK <--
    }

    private val displayPreferences = combine(
        libraryPreferences.categoryTabs.changes(),
        libraryPreferences.categoryNumberOfItems.changes(),
        libraryPreferences.showContinueReadingButton.changes(),
        ::DisplayPreferences,
    )

    // Tracks are only used by tracker filters and the tracker score sort, and loading every track is slow on
    // large libraries
    private val tracks = combine(getCategories.subscribe(), getTrackingFiltersFlow()) { categories, trackingFilters ->
        trackingFilters.values.any { it != TriState.DISABLED } ||
            categories.any { it.sort.type == LibrarySort.Type.TrackerMean }
    }
        .distinctUntilChanged()
        .flatMapLatest { needsTracks -> if (needsTracks) getTracksPerManga.subscribe() else flowOf(emptyMap()) }

    // KNS --> chapter colors are only read while the bookmark-color filter is in use
    private val bookmarkColors = combine(
        libraryPreferences.filterBookmarked.changes(),
        libraryPreferences.filterBookmarkColors.changes(),
        libraryPreferences.filterBookmarkColorsExclude.changes(),
    ) { bookmarked, included, excluded -> bookmarked == TriState.ENABLED_IS && (included.isNotEmpty() || excluded.isNotEmpty()) }
        .distinctUntilChanged()
        .flatMapLatest { needed -> if (needed) getLibraryBookmarkColors.subscribe() else flowOf(emptyMap()) }
    // KNS <--

    private val hasActiveFilters = combine(
        getLibraryItemPreferencesFlow(),
        getTrackingFiltersFlow(),
    ) { prefs, trackFilters ->
        listOf(
            prefs.filterDownloaded,
            prefs.filterUnread,
            prefs.filterStarted,
            prefs.filterBookmarked,
            prefs.filterCompleted,
            prefs.filterIntervalCustom,
            // SY -->
            prefs.filterLewd,
            // SY <--
            *trackFilters.values.toTypedArray(),
        )
            .fastAny { it != TriState.DISABLED } ||
            // KMK -->
            prefs.filterCategories ||
            // KMK <--
            // KNS -->
            prefs.filterBookmarkColors.isNotEmpty() || prefs.filterBookmarkColorsExclude.isNotEmpty()
        // KNS <--
    }
        .distinctUntilChanged()

    private val libraryData = combine(
        combine(
            searchQuery.debounce(0.25.seconds),
            getCategories.subscribe(),
            getFavoritesFlow(),
            ::Triple,
        ),
        // KNS -->
        combine(tracks, getTrackingFiltersFlow(), bookmarkColors, ::Triple),
        // KNS <--
        // KMK -->
        combine(includedCategories, excludedCategories, ::Pair),
        // KMK <--
        getLibraryItemPreferencesFlow(),
    ) { (searchQuery, categories, favorites), (tracksMap, trackingFilters, bookmarkColors), (includedCategories, excludedCategories), itemPreferences ->
        val filteredFavorites = favorites
            .applyFilters(
                tracksMap,
                trackingFilters,
                itemPreferences,
                // KMK -->
                includedCategories,
                excludedCategories,
                // KMK <--
                // KNS -->
                categories,
                bookmarkColors,
                // KNS <--
            )
            .let {
                if (searchQuery == null) {
                    // Don't do anything
                    it
                } else {
                    // Filter query
                    filterLibrary(
                        it,
                        searchQuery,
                        trackingFilters,
                        // KNS -->
                        categories,
                        // KNS <--
                    )
                }
            }

        LibraryData(
            isInitialized = true,
            categories = categories,
            favorites = filteredFavorites,
            tracksMap = tracksMap,
            loggedInTrackerIds = trackingFilters.keys,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    private val library = combine(
        libraryData,
        combine(
            groupType,
            // KMK -->
            filterCategory,
            includedCategories,
            // KMK <--
            ::Triple,
        ),
        combine(
            libraryPreferences.sortingMode.changes(),
            libraryPreferences.showHiddenCategories.changes(),
            libraryPreferences.showEmptyCategoriesSearch.changes(),
            ::Triple,
        ),
        combine(searchQuery, hasActiveFilters, ::Pair),
    ) { data, (groupType, filterCategory, includedCategories), (sort, showHiddenCategories, showEmptyCategoriesSearch), (searchQuery, hasActiveFilters) ->
        // KMK -->
        // Hide empty categories unless the setting is enabled or there are no active filters/search
        val noActiveFilterOrSearch = searchQuery.isNullOrBlank() && !hasActiveFilters
        Library(
            data = data,
            groupedFavorites = data.favorites
                .applyGrouping(
                    data.categories,
                    if (filterCategory && includedCategories.isNotEmpty()) {
                        LibraryGroup.UNGROUPED
                    } else {
                        groupType
                    },
                    showHiddenCategories,
                )
                .applySort(
                    data.favoritesById,
                    data.tracksMap,
                    data.loggedInTrackerIds,
                    // SY -->
                    sort.takeIf { groupType != LibraryGroup.BY_DEFAULT },
                    // SY <--
                )
                .filter {
                    showEmptyCategoriesSearch || noActiveFilterOrSearch || it.value.isNotEmpty()
                }
                .ifEmpty {
                    // Fall back to default category if no categories are present
                    mapOf(
                        Category(
                            0,
                            preferences.context.stringResource(MR.strings.default_category),
                            0,
                            0,
                            false,
                        ) to emptyList(),
                    )
                },
        )
        // KMK <--
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope + subscriberGate, SharingStarted.Eagerly, null)

    private data class DisplayState(
        val activeCategoryIndex: Int,
        val activeCategoryId: Long?,
        val display: DisplayPreferences,
        val hasActiveFilters: Boolean,
        // KNS -->
        val subcategorySelection: Map<Long, Long>,
        // KNS <--
    )

    private val displayState = combine(
        activeCategoryIndex,
        activeCategoryId,
        displayPreferences,
        hasActiveFilters,
        // KNS -->
        subcategorySelection,
        // KNS <--
    ) { index, id, display, filters, subcategories ->
        DisplayState(index, id, display, filters, subcategories)
    }

    val state: StateFlow<State> = combine(
        library,
        combine(searchQuery, selection, dialog, ::Triple),
        displayState,
        combine(groupType, showSyncExh, isSyncEnabled, ::Triple),
        // KMK -->
        combine(filterCategory, includedCategories, excludedCategories, ::Triple),
        // KMK <--
    ) { library, (searchQuery, selection, dialog), displayState, (groupType, showSyncExh, isSyncEnabled), (filterCategory, includedCategories, excludedCategories) ->
        State(
            isInitialized = library != null,
            isLoading = library == null,
            searchQuery = searchQuery,
            selection = selection,
            hasActiveFilters = displayState.hasActiveFilters,
            showCategoryTabs = displayState.display.showCategoryTabs,
            showMangaCount = displayState.display.showMangaCount,
            showMangaContinueButton = displayState.display.showMangaContinueButton,
            dialog = dialog,
            libraryData = library?.data ?: LibraryData(),
            activeCategoryIndex = displayState.activeCategoryIndex,
            // KMK -->
            activeCategoryId = displayState.activeCategoryId,
            // KMK <--
            // KNS -->
            subcategorySelection = displayState.subcategorySelection,
            // KNS <--
            groupedFavorites = library?.groupedFavorites.orEmpty(),
            // SY -->
            showSyncExh = showSyncExh,
            isSyncEnabled = isSyncEnabled,
            groupType = groupType,
            // SY <--
            // KMK -->
            filterCategory = filterCategory,
            includedCategories = includedCategories,
            excludedCategories = excludedCategories,
            // KMK <--
        )
    }
        .stateIn(viewModelScope + subscriberGate, subscriberGate.whileSubscribed(5.seconds), State())

    private data class DisplayPreferences(
        val showCategoryTabs: Boolean,
        val showMangaCount: Boolean,
        val showMangaContinueButton: Boolean,
    )

    private data class Library(
        val data: LibraryData,
        val groupedFavorites: Map<Category, List</* LibraryItem */ Long>>,
    )

    private suspend fun List<LibraryItem>.applyFilters(
        trackMap: Map<Long, List<Track>>,
        trackingFilter: Map<Long, TriState>,
        preferences: ItemPreferences,
        // KMK -->
        includedCategories: Set<Long>,
        excludedCategories: Set<Long>,
        // KMK <--
        // KNS -->
        categories: List<Category>,
        bookmarkColors: Map<Long, Set<Int>>,
        // KNS <--
    ): List<LibraryItem> {
        val downloadedOnly = preferences.globalFilterDownloaded
        val skipOutsideReleasePeriod = preferences.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else preferences.filterDownloaded
        val filterUnread = preferences.filterUnread
        val filterStarted = preferences.filterStarted
        val filterBookmarked = preferences.filterBookmarked
        val filterCompleted = preferences.filterCompleted
        val filterIntervalCustom = preferences.filterIntervalCustom
        val filterCategories = preferences.filterCategories

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        // SY -->
        val filterLewd = preferences.filterLewd
        // KUN --> already resolved without blocking on the extension scan, so reuse it here
        val sourceNames = associate { it.libraryManga.manga.source to it.sourceName }
        // KUN <--
        // SY <--

        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) { it.isLocal || it.downloadCount > 0 }
        }

        val filterFnUnread: (LibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryManga.unreadCount > 0 }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryManga.hasStarted }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryManga.hasBookmarks }
        }

        // KNS -->
        val filterBookmarkColors = preferences.filterBookmarkColors
        val filterBookmarkColorsExclude = preferences.filterBookmarkColorsExclude
        val filterFnBookmarkColors: (LibraryItem) -> Boolean = {
            filterBookmarked != TriState.ENABLED_IS ||
                BookmarkColor.matchesFilter(bookmarkColors[it.id].orEmpty(), filterBookmarkColors, filterBookmarkColorsExclude)
        }
        // KNS <--

        val filterFnCompleted: (LibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryManga.manga.status.toInt() == SManga.COMPLETED }
        }

        val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryManga.manga.fetchInterval < 0 }
            } else {
                true
            }
        }

        // SY -->
        val filterFnLewd: (LibraryItem) -> Boolean = {
            applyFilter(filterLewd) { it.libraryManga.manga.isLewd(sourceNames[it.libraryManga.manga.source]) }
        }
        // SY <--

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            // KMK -->
            val mangaTracks = trackMap[item.id].orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && mangaTracks.fastAny { it.trackerId in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || mangaTracks.fastAny { it.trackerId in includedTracks }
            // KMK <--

            !isExcluded && isIncluded
        }

        // KNS -->
        val categoriesById = categories.associateBy(Category::id)
        // KNS <--
        // KMK -->
        val filterFnCategories: (LibraryItem) -> Boolean = categories@{ item ->
            if (!filterCategories) return@categories true

            // KNS -->
            // A manga is in a category when it is in it or in one of its subcategories, so
            // include/exclude filters on a parent cover its whole branch. Ids, not names, so
            // same-named subcategories under other parents never match.
            val mangaCategories = CategoryTree.withAncestors(
                item.libraryManga.categories.fastFilterNot { it == 0L },
                categoriesById,
            )
            // KNS <--

            // Early return
            if (mangaCategories.isEmpty()) {
                return@categories includedCategories.isEmpty()
            }

            val isExcluded = excludedCategories.any { it in mangaCategories }
            val isIncluded = includedCategories.isEmpty() || includedCategories.all { it in mangaCategories }

            !isExcluded && isIncluded
        }
        // KMK <--

        return fastFilter {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                // KNS -->
                filterFnBookmarkColors(it) &&
                // KNS <--
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it) &&
                // SY -->
                filterFnLewd(it) &&
                // SY <--
                // KMK -->
                filterFnCategories(it)
            // KMK <--
        }
    }

    private suspend fun List<LibraryItem>.applyGrouping(
        categories: List<Category>,
        // KMK -->
        groupType: Int,
        showHiddenCategories: Boolean,
        // KMK <--
    ): Map<Category, List</* LibraryItem */ Long>> {
        // KMK -->
        when (groupType) {
            LibraryGroup.BY_DEFAULT -> {
                // KNS -->
                if (categories.any { it.parentId != UNCATEGORIZED_ID }) {
                    return groupByCategoryTree(categories, showHiddenCategories)
                }
                // KNS <--
                var showSystemCategory = false
                // KMK <--
                val groupCache = mutableMapOf</* Category.id */ Long, MutableList</* LibraryItem */ Long>>()
                forEach { item ->
                    item.libraryManga.categories.forEach { categoryId ->
                        // KMK -->
                        if (categoryId == UNCATEGORIZED_ID) {
                            showSystemCategory = true
                        }
                        // KMK <--
                        groupCache.getOrPut(categoryId) { mutableListOf() }.add(item.id)
                    }
                }
                return categories.fastFilter {
                    (showSystemCategory || !it.isSystemCategory) &&
                        // KMK -->
                        (showHiddenCategories || !it.hidden)
                    // KMK <--
                }
                    .associateWith {
                        groupCache[it.id]?.toList()
                            // KMK -->
                            ?.distinct()
                            // KMK <--
                            .orEmpty()
                    }
            }
            // KMK -->
            LibraryGroup.UNGROUPED -> {
                return mapOf(
                    Category(
                        0,
                        preferences.context.stringResource(SYMR.strings.ungrouped),
                        0,
                        0,
                        // KMK -->
                        false,
                        // KMK <--
                    ) to
                        map { it.id },
                )
            }

            else -> {
                return getGroupedMangaItems(
                    groupType = groupType,
                )
            }
        }
        // KMK <--
    }

    // KNS -->
    private fun List<LibraryItem>.groupByCategoryTree(
        categories: List<Category>,
        showHiddenCategories: Boolean,
    ): Map<Category, List</* LibraryItem */ Long>> =
        groupByCategoryTree(map { it.id to it.libraryManga.categories }, categories, showHiddenCategories)
    // KNS <--

    private fun Map<Category, List</* LibraryItem */ Long>>.applySort(
        favoritesById: Map<Long, LibraryItem>,
        trackMap: Map<Long, List<Track>>,
        loggedInTrackerIds: Set<Long>,
        // SY -->
        groupSort: LibrarySort? = null,
        // SY <--
    ): Map<Category, List</* LibraryItem */ Long>> {
        // SY -->
        val listOfTags by lazy {
            libraryPreferences.sortTagsForLibrary.get()
                .asSequence()
                .mapNotNull {
                    val list = it.split("|")
                    (list.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null) to
                        (list.getOrNull(1) ?: return@mapNotNull null)
                }
                .sortedBy { it.first }
                .map { it.second }
                .toList()
        }
        // SY <--

        // The collator already ignores case, and lowercasing on every comparison is slow on large libraries
        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { manga1, manga2 ->
            manga1.libraryManga.manga.title.compareToWithCollator(manga2.libraryManga.manga.title)
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { manga1, manga2 ->
            // SY -->
            val sort = groupSort ?: this
            // SY <--
            when (sort.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(manga1, manga2)
                }
                LibrarySort.Type.LastRead -> {
                    compareValues(manga1.libraryManga.lastRead, manga2.libraryManga.lastRead)
                }
                LibrarySort.Type.LastUpdate -> {
                    compareValues(manga1.libraryManga.manga.lastUpdate, manga2.libraryManga.manga.lastUpdate)
                }
                LibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    manga1.libraryManga.unreadCount == manga2.libraryManga.unreadCount -> 0
                    manga1.libraryManga.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                    manga2.libraryManga.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                    else -> manga1.libraryManga.unreadCount.compareTo(manga2.libraryManga.unreadCount)
                }
                LibrarySort.Type.TotalChapters -> {
                    manga1.libraryManga.totalChapters.compareTo(manga2.libraryManga.totalChapters)
                }
                LibrarySort.Type.LatestChapter -> {
                    compareValues(manga1.libraryManga.latestUpload, manga2.libraryManga.latestUpload)
                }
                LibrarySort.Type.ChapterFetchDate -> {
                    compareValues(manga1.libraryManga.chapterFetchedAt, manga2.libraryManga.chapterFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    compareValues(manga1.libraryManga.manga.dateAdded, manga2.libraryManga.manga.dateAdded)
                }
                LibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[manga1.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[manga2.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                LibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
                // SY -->
                LibrarySort.Type.TagList -> {
                    val manga1IndexOfTag = listOfTags.indexOfFirst {
                        manga1.libraryManga.manga.genre?.contains(it) ?: false
                    }
                    val manga2IndexOfTag = listOfTags.indexOfFirst {
                        manga2.libraryManga.manga.genre?.contains(it) ?: false
                    }
                    manga1IndexOfTag.compareTo(manga2IndexOfTag)
                }
                // SY <--
            }
        }

        return mapValues { (key, value) ->
            // SY -->
            val sort = groupSort ?: key.sort
            if (sort.type == LibrarySort.Type.Random) {
                // SY <--
                return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed.get()))
            }

            val manga = value.mapNotNull { favoritesById[it] }

            // SY -->
            val comparator = sort.comparator()
                // SY <--
                .let { if (/* SY --> */ sort.isAscending /* SY <-- */) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            manga.sortedWith(comparator).map { it.id }
        }
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge.changes(),
            libraryPreferences.unreadBadge.changes(),
            libraryPreferences.localBadge.changes(),
            libraryPreferences.languageBadge.changes(),
            libraryPreferences.autoUpdateMangaRestrictions.changes(),

            preferences.downloadedOnly.changes(),
            libraryPreferences.filterDownloaded.changes(),
            libraryPreferences.filterUnread.changes(),
            libraryPreferences.filterStarted.changes(),
            libraryPreferences.filterBookmarked.changes(),
            libraryPreferences.filterCompleted.changes(),
            libraryPreferences.filterIntervalCustom.changes(),
            // SY -->
            libraryPreferences.filterLewd.changes(),
            // SY <--
            // KMK -->
            libraryPreferences.sourceBadge.changes(),
            libraryPreferences.useLangIcon.changes(),
            libraryPreferences.filterCategories.changes(),
            // KMK <--
            // KNS -->
            libraryPreferences.filterBookmarkColors.changes(),
            libraryPreferences.filterBookmarkColorsExclude.changes(),
            // KNS <--
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                unreadBadge = it[1] as Boolean,
                localBadge = it[2] as Boolean,
                languageBadge = it[3] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in (it[4] as Set<*>),
                globalFilterDownloaded = it[5] as Boolean,
                filterDownloaded = it[6] as TriState,
                filterUnread = it[7] as TriState,
                filterStarted = it[8] as TriState,
                filterBookmarked = it[9] as TriState,
                filterCompleted = it[10] as TriState,
                filterIntervalCustom = it[11] as TriState,
                // SY -->
                filterLewd = it[12] as TriState,
                // SY <--
                // KMK -->
                sourceBadge = it[13] as Boolean,
                useLangIcon = it[14] as Boolean,
                filterCategories = it[15] as Boolean,
                // KMK <--
                // KNS -->
                filterBookmarkColors = BookmarkColor.fromNames((it[16] as Set<*>).filterIsInstance<String>()),
                filterBookmarkColorsExclude = BookmarkColor.fromNames((it[17] as Set<*>).filterIsInstance<String>()),
                // KNS <--
            )
        }
    }

    private fun getFavoritesFlow(): Flow<List<LibraryItem>> {
        return combine(
            getLibraryManga.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes.pausedWhileUnsubscribed(),
            // KUN -->
            // Recompute when the loaded sources change, so entries restored on a blank install stop
            // showing a stub source (and the warning badge) once their extension is installed.
            // `sources` emits an empty snapshot while the extensions load, and the resolution below never
            // suspends, so a slow or failing extension scan can't leave the library stuck on its spinner.
            sourceManager.sources,
            // KUN <--
            // KNS -->
            libraryPreferences.smartScanlatorMerge.changes(),
            // KNS <--
        ) { rawLibraryManga, preferences, _, loadedSources, globalSmartMerge ->
            // KNS -->
            val libraryManga = libraryVisibleCounts.apply(rawLibraryManga, globalSmartMerge)
            // KNS <--
            // KUN -->
            val loadedSourcesById = loadedSources.associateBy { it.id }
            val sources = libraryManga
                .mapTo(mutableSetOf()) { it.manga.source }
                .associateWith { sourceKey ->
                    loadedSourcesById[sourceKey]
                        ?: sourceManager.getOrStubOrNull(sourceKey)
                        ?: StubSource(sourceKey, lang = "", name = "")
                }
            // KUN <--
            val downloadCounts = downloadManager.getDownloadCounts(libraryManga.map { it.manga })
            libraryManga.map { manga ->
                // SY -->
                val downloadCount = if (manga.manga.source == MERGED_SOURCE_ID) {
                    getMergedMangaById.await(manga.manga.id).sumOf { downloadManager.getDownloadCount(it) }
                } else {
                    // KNS --> with a blacklist or Smart Merge, count only downloads of listed chapters
                    libraryVisibleCounts.visibleChapters(manga.id)
                        ?.takeIf { downloadCounts.getValue(manga.id) > 0 }
                        ?.let { downloadManager.getDownloadedChapterIds(it, manga.manga).size }
                        // KNS <--
                        // KUN --> counts are batched for the whole library, so reuse them here
                        ?: downloadCounts.getValue(manga.id)
                    // KUN <--
                }
                // SY <--
                val source = sources.getValue(manga.manga.source)
                LibraryItem(
                    libraryManga = manga,
                    // SY -->
                    downloadCount = downloadCount,
                    // SY <--
                    unreadCount = manga.unreadCount,
                    isLocal = manga.manga.isLocal(),
                    // KUN --> matches are case-insensitive, so store the lowercased name like Mihon
                    sourceName = source.name.lowercase(),
                    // KUN <--
                    // KUN --> unconditional: the AST `language:`/`lang:` search reads this field
                    // (the language badge below is separate and stays gated on the preference)
                    sourceLanguage = source.lang,
                    // KUN <--
                    badges = LibraryItem.Badges(
                        downloadCount = if (preferences.downloadBadge) {
                            // SY -->
                            downloadCount
                            // SY <--
                        } else {
                            0
                        },
                        unreadCount = if (preferences.unreadBadge) {
                            manga.unreadCount
                        } else {
                            0
                        },
                        isLocal = if (preferences.localBadge) {
                            manga.manga.isLocal()
                        } else {
                            false
                        },
                        sourceLanguage = if (preferences.languageBadge) {
                            source.lang
                        } else {
                            ""
                        },
                    ),
                    // KMK -->
                    useLangIcon = preferences.useLangIcon,
                    source = if (preferences.sourceBadge) {
                        DomainSource(
                            source.id,
                            source.lang,
                            source.name,
                            supportsLatest = false,
                            isStub = source is StubSource,
                        )
                    } else {
                        null
                    },
                    // KMK <--
                )
            }
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFiltersFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val filterFlows = loggedInTrackers.map { tracker ->
                    libraryPreferences.filterTracking(tracker.id.toInt()).changes().map { tracker.id to it }
                }
                combine(filterFlows) { it.toMap() }
            }
        }
    }

    // KUN -->
    // Ported from Mihon 2d1d2e4cab ("Rework database queries for the new schema"):
    // read an entry's categories from the loaded library instead of one query per manga,
    // falling back to the database only when the entry is not loaded.
    // KUN <--
    private suspend fun getCategoryIds(manga: Manga): Set<Long> {
        return state.value.libraryData.favoritesById[manga.id]
            ?.libraryManga
            ?.categories
            // The library reports no category as 0
            ?.filterTo(mutableSetOf()) { it != 0L }
            ?: getCategories.await(manga.id).mapTo(mutableSetOf()) { it.id }
    }

    suspend fun getNextUnreadChapter(manga: Manga): Chapter? {
        // SY -->
        val mergedManga = getMergedMangaById.await(manga.id).associateBy { it.id }
        // KNS --> merged entries are identified by their source, not their id
        return if (manga.source == MERGED_SOURCE_ID) {
            // KNS <--
            getMergedChaptersByMangaId.await(manga.id, applyFilter = true)
        } else {
            getChaptersByMangaId.await(manga.id, applyFilter = true)
        }
            // KNS -->
            .let { visibleChapters(manga, it) }
            // KNS <--
            .getNextUnread(manga, downloadManager, mergedManga)
        // SY <--
    }

    /**
     * Queues the amount specified of unread chapters from the list of selected manga
     */
    fun performDownloadAction(action: DownloadAction) {
        when (action) {
            DownloadAction.NEXT_1_CHAPTER -> downloadNextChapters(1)
            DownloadAction.NEXT_5_CHAPTERS -> downloadNextChapters(5)
            DownloadAction.NEXT_10_CHAPTERS -> downloadNextChapters(10)
            DownloadAction.NEXT_25_CHAPTERS -> downloadNextChapters(25)
            DownloadAction.UNREAD_CHAPTERS -> downloadNextChapters(null)
            DownloadAction.BOOKMARKED_CHAPTERS -> downloadBookmarkedChapters()
        }
        clearSelection()
    }

    private fun downloadNextChapters(amount: Int?) {
        val mangas = selectedManga
        appScope.launch {
            mangas.forEach { manga ->
                // SY -->
                if (manga.source == MERGED_SOURCE_ID) {
                    val mergedMangas = getMergedMangaById.await(manga.id)
                        .associateBy { it.id }
                    getNextChapters.await(manga.id)
                        .let { if (amount != null) it.take(amount) else it }
                        .groupBy { it.mangaId }
                        .forEach ab@{ (mangaId, chapters) ->
                            val mergedManga = mergedMangas[mangaId] ?: return@ab
                            val downloadChapters = chapters.fastFilterNot { chapter ->
                                downloadManager.queueState.value.fastAny { chapter.id == it.chapter.id } ||
                                    downloadManager.isChapterDownloaded(
                                        chapter.name,
                                        chapter.scanlator,
                                        chapter.url,
                                        mergedManga.ogTitle,
                                        mergedManga.source,
                                    )
                            }

                            downloadManager.downloadChapters(mergedManga, downloadChapters)
                        }

                    return@forEach
                }

                // SY <--
                val chapters = getNextChapters.await(manga.id)
                    .filterNotQueuedOrDownloaded(manga)
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    private fun downloadBookmarkedChapters() {
        val mangas = selectedManga
        appScope.launch {
            mangas.forEach { manga ->
                // SY -->
                if (manga.source == MERGED_SOURCE_ID) {
                    val mergedMangas = getMergedMangaById.await(manga.id)
                        .associateBy { it.id }
                    getBookmarkedChaptersByMangaId.await(manga.id)
                        // KNS -->
                        .let { visibleChapters(manga, it) }
                        // KNS <--
                        .groupBy { it.mangaId }
                        .forEach ab@{ (mangaId, chapters) ->
                            val mergedManga = mergedMangas[mangaId] ?: return@ab
                            val downloadChapters = chapters.fastFilterNot { chapter ->
                                downloadManager.queueState.value.fastAny { chapter.id == it.chapter.id } ||
                                    downloadManager.isChapterDownloaded(
                                        chapter.name,
                                        chapter.scanlator,
                                        chapter.url,
                                        mergedManga.ogTitle,
                                        mergedManga.source,
                                    )
                            }

                            downloadManager.downloadChapters(mergedManga, downloadChapters)
                        }

                    return@forEach
                }
                // SY <--

                val chapters = getBookmarkedChaptersByMangaId.await(manga.id)
                    // KNS -->
                    .let { visibleChapters(manga, it) }
                    // KNS <--
                    .filterNotQueuedOrDownloaded(manga)
                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    // KNS -->
    /** [chapters] (any subset) narrowed to the app-wide visible set of [manga]. */
    private suspend fun visibleChapters(manga: Manga, chapters: List<Chapter>): List<Chapter> {
        if (chapters.isEmpty() || !chapterVisibility.hasRules(manga)) return chapters
        val all = if (manga.source == MERGED_SOURCE_ID) {
            getMergedChaptersByMangaId.await(manga.id, applyFilter = false)
        } else {
            getChaptersByMangaId.await(manga.id, applyFilter = false)
        }
        val visibleIds = chapterVisibility.visibleChapters(manga, all).mapTo(HashSet(), Chapter::id)
        return chapters.filter { it.id in visibleIds }
    }
    // KNS <--

    // SY -->
    fun cleanTitles() {
        val regex1 = "\\[.*?]".toRegex()
        val regex2 = "\\(.*?\\)".toRegex()
        val regex3 = "\\{.*?\\}".toRegex()
        val regex4 = ".*\\|".toRegex()
        viewModelScope.launch(Dispatchers.IO) {
            val updates = state.value.selectedManga.fastFilter {
                it.isEhBasedManga() ||
                    it.source in nHentaiSourceIds
            }.mapNotNull { manga ->
                val editedTitle = manga.title
                    .replace(regex1, "").trim()
                    .replace(regex2, "").trim()
                    .replace(regex3, "").trim()
                    .let {
                        if (it.contains("|")) {
                            it.replace(regex4, "").trim()
                        } else {
                            it
                        }
                    }
                if (manga.title == editedTitle) return@mapNotNull null
                val mangaInfo = MangaUserInfoUpdate(
                    id = manga.id,
                    title = editedTitle.nullIfBlank(),
                    author = manga.author.takeUnless { it == manga.ogAuthor },
                    artist = manga.artist.takeUnless { it == manga.ogArtist },
                    thumbnailUrl = manga.thumbnailUrl.takeUnless { it == manga.ogThumbnailUrl },
                    description = manga.description.takeUnless { it == manga.ogDescription },
                    genre = manga.genre.takeUnless { it == manga.ogGenre },
                    status = manga.status.takeUnless { it == manga.ogStatus },
                )

                mangaInfo
            }
            // KUN -->
            // The write and the bump must survive leaving the screen right after the edit: the view
            // model scope is cancelled then, and a cancelled transaction rolls the whole batch back.
            // The version trigger does not watch the user_* columns, so bump each edited manga (gated
            // by sync settings), comparing against the stored row rather than the snapshot the edits
            // were computed from: a sync may have applied another device's edit in between.
            appScope.launchNonCancellable {
                val previous = updates.map { after -> getManga.await(after.id)?.toMangaUserInfoUpdate() to after }
                setMangaUserInfo.setAll(updates)
                previous.forEach { (before, after) ->
                    syncStateBumper.bumpForCustomInfoEdit(after.id, before, after)
                }
            }
            // KUN <--
            clearSelection()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun syncMangaToDex() {
        launchIO {
            MdUtil.getEnabledMangaDex(sourcePreferences, sourceManager)?.let { mdex ->
                state.value.selectedManga.fastFilter { it.source in mangaDexSourceIds }.fastForEach { manga ->
                    mdex.updateFollowStatus(MdUtil.getMangaId(manga.url), FollowStatus.READING)
                }
            }
            clearSelection()
        }
    }

    fun resetInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val updates = state.value.selectedManga.map { manga ->
                val mangaInfo = MangaUserInfoUpdate(
                    id = manga.id,
                    title = null,
                    author = null,
                    artist = null,
                    thumbnailUrl = null,
                    description = null,
                    genre = null,
                    status = null,
                )
                mangaInfo
            }
            // KUN -->
            // The write and the bump must survive leaving the screen right after the edit: the view
            // model scope is cancelled then, and a cancelled transaction rolls the whole batch back.
            appScope.launchNonCancellable {
                // Compare against the stored row, not the snapshot the edits were computed from.
                // KUN -->
                // The version trigger does not watch the user_* columns, so bump each edited manga
                // (gated by sync settings). Compare against the stored row, not the snapshot the
                // edits were computed from: a sync may have applied another device's edit in between.
                val previous = updates.map { after -> getManga.await(after.id)?.toMangaUserInfoUpdate() to after }
                setMangaUserInfo.setAll(updates)
                previous.forEach { (before, after) ->
                    syncStateBumper.bumpForCustomInfoEdit(after.id, before, after)
                }
                // KUN <--
            }
            // KUN <--
            clearSelection()
        }
    }
    // SY <--

    // KMK -->
    /**
     * Update Selected Mangas
     */
    suspend fun updateSelectedManga(): Boolean {
        val mangaIds = state.value.selection.toList()
        return LibraryUpdateWorker.startNow(
            context = preferences.context,
            mangaIds = mangaIds,
            target = LibraryUpdateWorker.Target.CHAPTERS,
        )
    }
    // KMK <--
    // KUN -->
    // Library merging lives in [smartSearchMerge] below: Komikku shipped that action before this
    // upstream commit existed (it says "Partially taken from Komikku"), so the SY copy of the
    // action and its LibraryMergeResult are not ported — they would add a second merge button.
    // KUN <--

    private fun List<Chapter>.filterNotQueuedOrDownloaded(manga: Manga): List<Chapter> {
        val queuedDownloads = downloadManager.getQueuedDownloadsByChapterId()
        val downloadedChapterIds = downloadManager.getDownloadedChapterIds(this, manga)
        return fastFilterNot { it.id in queuedDownloads || it.id in downloadedChapterIds }
    }

    /**
     * Marks mangas' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val selection = selectedManga
        appScope.launch {
            selection.forEach { manga ->
                setReadStatus.await(
                    manga = manga,
                    read = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangas the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangas: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        appScope.launch {
            if (deleteFromLibrary) {
                val toDelete = mangas
                    .distinctBy { it.id }
                    .map {
                        it.removeCovers(coverCache)
                        MangaUpdate(it.id) {
                            favorite = false
                        }
                    }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangas.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        if (source is MergedSource) {
                            val mergedMangas = getMergedMangaById.await(manga.id)
                            val sources = mergedMangas.distinctBy {
                                it.source
                            }.map { sourceManager.getOrStub(it.source) }
                            mergedMangas.forEach merge@{ mergedManga ->
                                val mergedSource =
                                    sources.firstOrNull { mergedManga.source == it.id } as? HttpSource ?: return@merge
                                downloadManager.deleteManga(mergedManga, mergedSource)
                            }
                        } else {
                            downloadManager.deleteManga(manga, source)
                        }
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        appScope.launch {
            mangaList.forEach { manga ->
                val categoryIds = getCategoryIds(manga)
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setMangaCategories.await(manga.id, categoryIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode.asState(viewModelScope)
    }

    fun getColumnsForOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns else libraryPreferences.portraitColumns)
            .asState(viewModelScope)
    }

    fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        val state = state.value
        return state.getItemsForCategoryId(state.activeCategory?.id).randomOrNull()
    }

    fun showSettingsDialog() {
        dialog.update { Dialog.SettingsSheet }
    }

    // SY -->
    fun showRecommendationSearchDialog() {
        val mangaList = state.value.selectedManga
        dialog.update { Dialog.RecommendationSearchSheet(mangaList) }
    }

    // KUN -->
    // Replaces TachiyomiSY's custom search engine with Mihon's AST-based library search, while
    // keeping two small supplements: tracker name / localized status matching and the TachiyomiSY
    // EH/NHentai metadata namespace search (see below).
    private suspend fun filterLibrary(
        unfiltered: List<LibraryItem>,
        query: String?,
        loggedInTrackServices: Map<Long, TriState>,
        // KNS -->
        categories: List<Category>,
        // KNS <--
    ): List<LibraryItem> {
        if (unfiltered.isEmpty() || query.isNullOrBlank()) return unfiltered

        // AZ -->
        if (query.trim().lowercase() == "mangadex-dmca") {
            // Special easter egg query
            return unfiltered.fastFilter {
                it.libraryManga.manga.source in MANGADEX_IDS &&
                    it.libraryManga.manga.url.removePrefix("/manga/").lowercase() in mangaDexDmcaUuids
            }
        }
        // AZ <--

        // Parse the query once, then reuse the node for every item
        val queryNode = QueryNode.from(query)

        // Tracks are only needed for the tracker supplement, and only when a tracker is logged in
        val tracks = if (loggedInTrackServices.isNotEmpty()) {
            getTracks.await().groupBy { it.mangaId }
        } else {
            emptyMap()
        }
        val context = preferences.context

        // The EH/NHentai metadata supplement needs the flat metadata, which is expensive to load.
        // Only do so when the query actually contains a metadata namespace term. `fastFilter`'s
        // lambda is not suspend, so resolve the metadata in a suspend loop up front and look it up
        // by manga id inside the filter.
        val hasMetadataQuery = queryNode.containsMetadataQuery()
        val metadataById: Map<Long, LibrarySearchMetadata?> = if (hasMetadataQuery) {
            val metadataIds = getIdsOfFavoriteMangaWithMetadata.await().toHashSet()
            val resolved = mutableMapOf<Long, LibrarySearchMetadata?>()
            for (item in unfiltered) {
                val mangaId = item.libraryManga.manga.id
                if (resolved.containsKey(mangaId)) continue
                resolved[mangaId] = if (mangaId in metadataIds &&
                    isMetadataSource(item.libraryManga.manga.source)
                ) {
                    getFlatMetadataById.await(mangaId)?.let { flat ->
                        LibrarySearchMetadata(uploader = flat.metadata.uploader, tags = flat.tags)
                    }
                } else {
                    null
                }
            }
            resolved
        } else {
            emptyMap()
        }

        // KNS -->
        val fields = LibrarySearchFields(
            categories = categories,
            defaultCategoryName = context.stringResource(MR.strings.label_default),
            statusName = { status -> statusMap[status]?.first?.let { context.stringResource(it) } },
            trackerValues = { track ->
                val tracker = trackerManager.get(track.trackerId)
                listOfNotNull(
                    tracker?.name,
                    tracker?.getStatus(track.status)?.let { context.stringResource(it) },
                )
            },
        )
        // KNS <--

        return unfiltered.fastFilter { item ->
            queryNode.matchesWithSupplements(
                item = item,
                tracks = tracks[item.libraryManga.manga.id].orEmpty(),
                context = context,
                metadata = if (hasMetadataQuery) metadataById[item.libraryManga.manga.id] else null,
                // KNS -->
                fields = fields,
                // KNS <--
            )
        }
    }

    /**
     * KMK supplements on top of Mihon's AST search.
     *
     * The AST ([mihon.feature.library.matches]) only inspects manga fields, so:
     *  - a plain-text general term additionally matches a logged-in tracker's name or the localized
     *    string of the entry's tracking status (via [filterTracks]) — e.g. `AniList` or `reading`;
     *  - a TachiyomiSY metadata namespace term ([MetadataQueryNode], e.g. `artist:foo`, `g:circle`)
     *    is resolved against the entry's EH/NHentai flat metadata instead of an unrelated manga field.
     *
     * `field:` and comparison terms stay AST-only.
     *
     * Semantics: the AST node types are mirrored. A non-negated general term matches when either the
     * AST matches or a track matches; a negated general term matches only when neither matches
     * (so `-reading` also excludes entries whose tracker status is "reading").
     */
    private fun QueryNode.matchesWithSupplements(
        item: LibraryItem,
        tracks: List<Track>,
        context: Context,
        metadata: LibrarySearchMetadata?,
        // KNS -->
        fields: LibrarySearchFields,
        // KNS <--
    ): Boolean = when (this) {
        is AndNode -> children.all { it.matchesWithSupplements(item, tracks, context, metadata, fields) }
        is OrNode -> children.any { it.matchesWithSupplements(item, tracks, context, metadata, fields) }
        is NotNode -> !child.matchesWithSupplements(item, tracks, context, metadata, fields)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> {
            val trackerMatch = tracks.isNotEmpty() && filterTracks(value, tracks, context)
            if (negated) matches(item) && !trackerMatch else matches(item) || trackerMatch
        }
        is FieldQueryNode, is ComparisonQueryNode -> matches(item)
        is MetadataQueryNode -> matches(item, metadata)
        // KNS -->
        is LibraryFieldQueryNode -> fields.matches(this, item, tracks)
        // KNS <--
    }
    // KUN <--

    private fun filterTracks(constraint: String, tracks: List<Track>, context: Context): Boolean {
        return tracks.fastAny { track ->
            val trackService = trackerManager.get(track.trackerId)
            if (trackService != null) {
                val status = trackService.getStatus(track.status)?.let {
                    context.stringResource(it)
                }
                val name = trackerManager.get(track.trackerId)?.name
                status?.contains(constraint, true) == true || name?.contains(constraint, true) == true
            } else {
                false
            }
        }
    }
    // SY <--

    private data class LastSelection(val categoryId: Long, val mangaId: Long)

    private var lastSelection: LastSelection? = null

    /**
     * Reads from [selection] rather than [state], which is derived asynchronously and can still
     * hold the previous selection immediately after a toggle.
     */
    private val selectedManga: List<Manga>
        get() {
            val favoritesById = state.value.libraryData.favoritesById
            return selection.value.mapNotNull { favoritesById[it]?.libraryManga?.manga }
        }

    fun clearSelection() {
        lastSelection = null
        selection.update { setOf() }
    }

    fun toggleSelection(category: Category, manga: LibraryManga) {
        selection.update { selection ->
            val newSelection = selection.mutate { set ->
                if (!set.remove(manga.id)) set.add(manga.id)
            }
            lastSelection = LastSelection(category.id, manga.id).takeIf { manga.id in newSelection }
            newSelection
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(category: Category, manga: LibraryManga) {
        val state = state.value
        selection.update { selection ->
            val newSelection = selection.mutate { list ->
                val items = state.getItemsForCategoryId(category.id).fastMap { it.id }
                val lastMangaIndex = lastSelection
                    ?.takeIf { it.categoryId == category.id && it.mangaId in list }
                    ?.let { items.indexOf(it.mangaId) }
                    ?: -1
                val curMangaIndex = items.indexOf(manga.id)
                if (lastMangaIndex == -1 || curMangaIndex == -1) {
                    list.add(manga.id)
                    return@mutate
                }

                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> lastMangaIndex..curMangaIndex
                    else -> curMangaIndex..lastMangaIndex
                }
                list.addAll(items.subList(selectionRange.first, selectionRange.last + 1))
            }
            lastSelection = LastSelection(category.id, manga.id)
            newSelection
        }
    }

    fun selectAll() {
        lastSelection = null
        val state = state.value
        selection.update { selection ->
            selection.mutate { list ->
                state.getItemsForCategoryId(state.activeCategory?.id).fastMap { it.id }.let(list::addAll)
            }
        }
    }

    fun invertSelection() {
        lastSelection = null
        val state = state.value
        selection.update { selection ->
            selection.mutate { list ->
                val itemIds = state.getItemsForCategoryId(state.activeCategory?.id).fastMap { it.id }
                val (toRemove, toAdd) = itemIds.partition { it in list }
                list.removeAll(toRemove.toSet())
                list.addAll(toAdd)
            }
        }
    }

    // KNS -->
    // KNS -->
    /**
     * The category a refresh of [category]'s tab updates: the selected subcategory when the refresh
     * scope is set to the selected subcategory, else the tab's category (with all its subcategories).
     */
    fun refreshCategory(category: Category?): Category? {
        if (category == null || libraryPreferences.subcategoryUpdateScope.get() != SubcategoryUpdateScope.SUBCATEGORY_ONLY) {
            return category
        }
        val selected = subcategorySelection.value[category.id] ?: return category
        return state.value.libraryData.categories.find { it.id == selected } ?: category
    }
    // KNS <--

    /** Shows [subcategoryId]'s subtree in [rootId]'s tab, or the whole tab with null. */
    fun selectSubcategory(rootId: Long, subcategoryId: Long?) {
        subcategorySelection.update { current ->
            if (subcategoryId == null || subcategoryId == rootId) current - rootId else current + (rootId to subcategoryId)
        }
    }
    // KNS <--

    fun search(query: String?) {
        searchQuery.update { query }
    }

    fun updateActiveCategoryIndex(
        index: Int,
        // KUN -->
        categoryId: Long,
        // KUN <--
    ) {
        activeCategoryIndex.update { index }
        // KMK -->
        // KUN -->
        // The caller resolves the id from the list it rendered. Reading it back off [state] raced
        // with category list updates (e.g. during a sync restore) and could latch the id of the
        // category that used to be at this index.
        activeCategoryId.update { categoryId }
        // KUN <--
        // KMK <--
        // KUN -->
        // The index comes from the pager via the rendered category list, so it is never negative.
        // [State.coercedActiveCategoryIndex] coerces it on read if the list later shrank.
        libraryPreferences.lastUsedCategory.set(index)
        // KUN <--
    }

    fun openChangeCategoryDialog() {
        viewModelScope.launch {
            // Create a copy of selected manga
            val mangaList = selectedManga

            // Hide the default category because it has a different behavior than the ones from db.
            // KMK -->
            val categories = state.value.libraryData.categories.fastFilter { it.id != 0L }
            // KMK <--

            // KUN -->
            // Ported from Mihon 2d1d2e4cab ("Rework database queries for the new schema"):
            // common/mix are now sets of category ids read from memory, not per-manga queries.
            // KUN <--
            val mangaCategoryIds = mangaList.map { getCategoryIds(it) }
            val common = mangaCategoryIds.reduceOrNull { set1, set2 -> set1 intersect set2 }.orEmpty()
            val mix = mangaCategoryIds.flatten().toSet() - common
            val preselected = categories
                .fastMap {
                    when (it.id) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toList()

            dialog.update { Dialog.ChangeCategory(mangaList, preselected) }
        }
    }

    fun openDeleteMangaDialog() {
        dialog.update { Dialog.DeleteManga(selectedManga) }
    }

    fun closeDialog() {
        dialog.update { null }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val manga: List<Manga>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteManga(val manga: List<Manga>) : Dialog

        // SY -->
        data object SyncFavoritesWarning : Dialog
        data object SyncFavoritesConfirm : Dialog
        data class RecommendationSearchSheet(val manga: List<Manga>) : Dialog
        // SY <--
    }

    // SY -->
    private suspend fun List<LibraryItem>.getGroupedMangaItems(
        groupType: Int,
    ): Map<Category, List</* LibraryItem */ Long>> {
        val context = preferences.context
        return when (groupType) {
            LibraryGroup.BY_TRACK_STATUS -> {
                val tracks = runBlocking { getTracks.await() }.groupBy { it.mangaId }
                // KMK -->
                val groupCache = mutableMapOf</* Track.status */ Int, MutableList</* LibraryItem */ Long>>()
                forEach { item ->
                    val statuses = tracks[item.libraryManga.manga.id]?.fastMapNotNull { track ->
                        TrackStatus.parseTrackerStatus(trackerManager, track.trackerId, track.status)
                    }
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf(TrackStatus.OTHER)
                    statuses.forEach { status ->
                        groupCache.getOrPut(status.int) { mutableListOf() }.add(item.id)
                    }
                }
                // KMK <--
                groupCache.mapKeys { (id) ->
                    // KMK -->
                    val trackStatus = TrackStatus.entries.find { it.int == id } ?: TrackStatus.OTHER
                    // KMK <--
                    Category(
                        id = id.toLong(),
                        // KMK -->
                        name = context.stringResource(trackStatus.res),
                        order = trackStatus.ordinal.toLong(),
                        // KMK <--
                        flags = 0,
                        // KMK -->
                        hidden = false,
                        // KMK <--
                    )
                }
                    // KMK -->
                    .mapValues { (_, values) -> values.distinct() }
                // KMK <--
            }
            LibraryGroup.BY_SOURCE -> {
                // KMK -->
                val groupCache = mutableMapOf</* Source.id */ Long, MutableList</* LibraryItem */ Long>>()
                forEach { item ->
                    groupCache.getOrPut(item.libraryManga.manga.source) { mutableListOf() }.add(item.id)
                }
                // KUN -->
                // Use the non-suspending snapshot: this runs while the library is being built, so awaiting
                // the extension scan here would keep the whole library on its spinner.
                val sources = groupCache.keys
                    .map { sourceId ->
                        val knownName = sourceManager.getOrStubOrNull(sourceId)?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: firstOrNull { it.libraryManga.manga.source == sourceId }?.sourceName.orEmpty()
                        sourceId to knownName
                    }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { (id, name) -> name.ifBlank { id.toString() } })
                val sourceOrderMap = sources.withIndex().associate { (index, source) -> source.first to index.toLong() }

                sources.associate { (sourceId, sourceName) ->
                    val category = Category(
                        id = sourceId,
                        name = if (sourceId == LocalSource.ID) {
                            context.stringResource(MR.strings.local_source)
                        } else {
                            sourceName.ifBlank { sourceId.toString() }
                        },
                        order = sourceOrderMap[sourceId] ?: Long.MAX_VALUE,
                        flags = 0,
                        // KMK -->
                        hidden = false,
                        // KMK <--
                    )
                    category to groupCache[sourceId].orEmpty()
                }
                // KUN <--
                // KMK <--
            }
            LibraryGroup.BY_STATUS -> {
                groupBy { item ->
                    item.libraryManga.manga.status
                }.mapKeys {
                    // KMK -->
                    val (nameRes, order) = statusMap[it.key] ?: (MR.strings.unknown to 7L)
                    // KMK <--
                    Category(
                        id = it.key + 1,
                        name = context.stringResource(nameRes),
                        order = order,
                        flags = 0,
                        // KMK -->
                        hidden = false,
                        // KMK <--
                    )
                }
                    // KMK -->
                    .mapValues { (_, libraryItem) -> libraryItem.fastMap { it.id } }
                // KMK <--
            }
            else -> emptyMap()
        }.toSortedMap(compareBy { it.order })
    }

    // KMK -->
    private val statusMap = mapOf(
        SManga.ONGOING.toLong() to (MR.strings.ongoing to 1L),
        SManga.COMPLETED.toLong() to (MR.strings.completed to 2L),
        SManga.PUBLISHING_FINISHED.toLong() to (MR.strings.publishing_finished to 3L),
        SManga.LICENSED.toLong() to (MR.strings.licensed to 4L),
        SManga.ON_HIATUS.toLong() to (MR.strings.on_hiatus to 5L),
        SManga.CANCELLED.toLong() to (MR.strings.cancelled to 6L),
    )
    // KMK <--

    fun runRecommendationSearch(selection: List<Manga>) {
        recommendationSearch.runSearch(viewModelScope, selection)?.let {
            recommendationSearchJob = it
        }
    }

    fun cancelRecommendationSearch() {
        recommendationSearchJob?.cancel()
    }

    fun runSync() {
        favoritesSync.runSync(viewModelScope)
    }

    fun onAcceptSyncWarning() {
        exhPreferences.exhShowSyncIntro.set(false)
    }

    fun openFavoritesSyncDialog() {
        dialog.update {
            if (exhPreferences.exhShowSyncIntro.get()) {
                Dialog.SyncFavoritesWarning
            } else {
                Dialog.SyncFavoritesConfirm
            }
        }
    }
    // SY <--

    // KMK -->
    /**
     * Will get first merged manga in the list as target merging.
     * If there is no merged manga, then it will use the first one in list to create a new target.
     */
    suspend fun smartSearchMerge(selectedMangas: List<Manga>): Long? {
        val mergedManga = selectedMangas.firstOrNull { it.source == MERGED_SOURCE_ID }?.let { listOf(it) }
            ?: emptyList()
        val mergingMangas = selectedMangas.fastFilterNot { it.source == MERGED_SOURCE_ID }
        val toMergeMangas = mergedManga + mergingMangas
        if (toMergeMangas.size <= 1) return null

        var mergingMangaId = toMergeMangas.first().id
        for (manga in toMergeMangas.drop(1)) {
            mergingMangaId = smartSearchMerge.smartSearchMerge(manga, mergingMangaId).id
        }
        return mergingMangaId
    }
    // KMK <--

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        // KMK -->
        val useLangIcon: Boolean,
        val sourceBadge: Boolean,
        // KMK <--
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
        // SY -->
        val filterLewd: TriState,
        // SY <--
        // KMK -->
        val filterCategories: Boolean,
        // KMK <--
        // KNS -->
        val filterBookmarkColors: Set<BookmarkColor> = emptySet(),
        val filterBookmarkColorsExclude: Set<BookmarkColor> = emptySet(),
        // KNS <--
    )

    @Immutable
    data class LibraryData(
        val isInitialized: Boolean = false,
        val categories: List<Category> = emptyList(),
        val favorites: List<LibraryItem> = emptyList(),
        val tracksMap: Map</* Manga */ Long, List<Track>> = emptyMap(),
        val loggedInTrackerIds: Set<Long> = emptySet(),
    ) {
        val favoritesById by lazy { favorites.associateBy { it.id } }
    }

    @Immutable
    data class State(
        val isInitialized: Boolean = false,
        val isLoading: Boolean = true,
        val searchQuery: String? = null,
        val selection: Set</* Manga */ Long> = setOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        val libraryData: LibraryData = LibraryData(),
        private val activeCategoryIndex: Int = 0,
        // KMK -->
        private val activeCategoryId: Long? = null,
        // KMK <--
        // KNS -->
        private val subcategorySelection: Map<Long, Long> = emptyMap(),
        // KNS <--
        private val groupedFavorites: Map<Category, List</* LibraryItem */ Long>> = emptyMap(),
        // SY -->
        val showSyncExh: Boolean = false,
        val isSyncEnabled: Boolean = false,
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        // SY <--
        // KMK -->
        val filterCategory: Boolean = false,
        val includedCategories: Set<Long> = setOf(),
        val excludedCategories: Set<Long> = setOf(),
        // KMK <--
    ) {
        /**
         * The grouped tabs which is displayed above the library screen.
         * They can be actual [Category] or [Source], [Track]...
         */
        val displayedCategories: List<Category> = groupedFavorites.keys.toList()

        val coercedActiveCategoryIndex = /* KMK --> */ displayedCategories.indexOfFirst { it.id == activeCategoryId }
            .takeIf { it != -1 } ?: activeCategoryIndex
            // KMK <--
            .coerceIn(
                minimumValue = 0,
                maximumValue = displayedCategories.lastIndex.coerceAtLeast(0),
            )

        val activeCategory: Category? = displayedCategories.getOrNull(coercedActiveCategoryIndex)

        val isLibraryEmpty = libraryData.favorites.isEmpty()

        val selectionMode = selection.isNotEmpty()

        val selectedManga by lazy { selection.mapNotNull { libraryData.favoritesById[it]?.libraryManga?.manga } }

        // SY -->
        val showCleanTitles: Boolean by lazy {
            selectedManga.fastAny {
                it.isEhBasedManga() ||
                    it.source in nHentaiSourceIds
            }
        }

        val showAddToMangadex: Boolean by lazy {
            selectedManga.fastAny { it.source in mangaDexSourceIds }
        }

        val showResetInfo: Boolean by lazy {
            selectedManga.fastAny { manga ->
                manga.title != manga.ogTitle ||
                    manga.author != manga.ogAuthor ||
                    manga.artist != manga.ogArtist ||
                    manga.thumbnailUrl != manga.ogThumbnailUrl ||
                    manga.description != manga.ogDescription ||
                    manga.genre != manga.ogGenre ||
                    manga.status != manga.ogStatus
            }
        }
        // SY <--

        fun getItemsForCategoryId(categoryId: Long?): List<LibraryItem> {
            if (categoryId == null) return emptyList()
            val category = displayedCategories.find { it.id == categoryId } ?: return emptyList()
            return getItemsForCategory(category)
        }

        fun getItemsForCategory(category: Category): List<LibraryItem> {
            // KNS -->
            val items = groupedFavorites[category].orEmpty().fastMapNotNull { libraryData.favoritesById[it] }
            val subcategoryIds = selectedSubtree(category) ?: return items
            return items.filter { item -> item.libraryManga.categories.any { it in subcategoryIds } }
            // KNS <--
        }

        // KNS -->
        private val categoriesById by lazy { libraryData.categories.associateBy(Category::id) }

        /**
         * The selected subcategory of [category]'s tab and everything below it; null = the whole tab.
         * Only category tabs have subcategories; a selection that was deleted or moved out of the tab
         * falls back to the whole tab.
         */
        private fun selectedSubtree(category: Category): Set<Long>? {
            if (groupType != LibraryGroup.BY_DEFAULT || category.isSystemCategory) return null
            val selectedId = subcategorySelection[category.id] ?: return null
            if (selectedId !in categoriesById) return null
            if (category.id !in CategoryTree.ancestorIds(selectedId, categoriesById)) return null
            return CategoryTree.descendantIds(selectedId, libraryData.categories) + selectedId
        }

        /**
         * The subcategory chips of a top-level tab: the selected path (outermost first) and the
         * children of the deepest selected category; null when the tab has no subcategories.
         */
        fun subcategoryNavigation(category: Category): SubcategoryNavigation? {
            if (groupType != LibraryGroup.BY_DEFAULT || category.isSystemCategory) return null
            fun childrenOf(id: Long) = CategoryTree.childrenOf(id, libraryData.categories).filterNot { it.hidden }
            if (childrenOf(category.id).isEmpty()) return null
            val selected = subcategorySelection[category.id]?.let(categoriesById::get)
            val path = selected?.let { sel ->
                (CategoryTree.ancestorIds(sel.id, categoriesById).asReversed() + sel.id)
                    .dropWhile { it != category.id }
                    .drop(1)
                    .mapNotNull(categoriesById::get)
            }.orEmpty()
            return SubcategoryNavigation(
                root = category,
                path = path,
                children = childrenOf(path.lastOrNull()?.id ?: category.id),
            )
        }
        // KNS <--

        fun getItemCountForCategory(category: Category): Int? {
            if (!showMangaCount && searchQuery.isNullOrEmpty()) return null
            // KNS -->
            // Each tab counts what it shows: its own selected subcategory, whether or not it is active.
            if (selectedSubtree(category) != null) return getItemsForCategory(category).size
            // KNS <--
            return groupedFavorites[category]?.size
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = displayedCategories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showMangaCount -> null
                !showCategoryTabs -> getItemCountForCategory(category)
                // Whole library count
                else -> libraryData.favorites.size
            }
            return LibraryToolbarTitle(title, count)
        }
    }

    // KMK -->
    companion object {
        /** List of MangaDex UUIDs subject to DMCA takedowns */
        @Volatile
        private var mangaDexDmcaUuids = hashSetOf<String>()

        /**
         * Loads the list of MangaDex UUIDs subject to DMCA takedowns from an external file.
         * The file should be placed at res/raw/mangadex_dmca_uuids.txt, one UUID per line.
         */
        private suspend fun loadMangaDexDmcaUuids(context: Context): HashSet<String> = withIOContext {
            try {
                val inputStream = context.resources.openRawResource(
                    eu.kanade.tachiyomi.R.raw.mangadex_dmca_uuids,
                )
                inputStream.bufferedReader().useLines { lines ->
                    lines.map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toHashSet()
                }
            } catch (e: Exception) {
                // Log the error and return an empty set if the file cannot be read.
                xLogE("Error loading MangaDex DMCA UUIDs", e)
                hashSetOf()
            }
        }
    }
    // KMK <--
}
