package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.interactor.GetSequelPrequel
import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.SmartSearchMerge
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.PagePreview
import eu.kanade.domain.manga.model.chaptersFiltered
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncStateBumper
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft
import eu.kanade.presentation.manga.components.ScanlatorStats
import eu.kanade.presentation.manga.components.scanlatorStatsOf
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.manga.RelatedManga.Companion.isLoading
import eu.kanade.tachiyomi.ui.manga.RelatedManga.Companion.removeDuplicates
import eu.kanade.tachiyomi.ui.manga.RelatedManga.Companion.sorted
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toast
import exh.debug.DebugToggles
import exh.eh.EHentaiUpdateHelper
import exh.log.xLogD
import exh.log.xLogE
import exh.md.utils.FollowStatus
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.FlatMetadata
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedManga
import exh.source.mangaDexSourceIds
import exh.util.nullIfEmpty
import exh.util.trimOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.manga.model.toDomainManga
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.SubscriberGate
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.pausedWhileUnsubscribed
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.DeleteChapters
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.chapter.model.ScanlatorRangeRule
import tachiyomi.domain.chapter.model.chapterListVisibility
import tachiyomi.domain.chapter.model.encodeScanlatorRangeRule
import tachiyomi.domain.chapter.model.parseScanlatorRangeRule
import tachiyomi.domain.chapter.model.smartScanlatorMergeEnabled
import tachiyomi.domain.chapter.service.ChapterBookmarks
import tachiyomi.domain.chapter.service.bookmarkTarget
import tachiyomi.domain.chapter.service.calculateChapterGap
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.chapter.service.hybridChapterBlacklistKey
import tachiyomi.domain.chapter.service.isChapterBlacklisted
import tachiyomi.domain.chapter.service.missingChaptersCount
import tachiyomi.domain.chapter.service.resolveChapterBlacklistKey
import tachiyomi.domain.chapter.service.resolveChapterBlacklistMatches
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.libraryUpdateError.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.manga.interactor.DeleteMergeById
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.interactor.SetMangaUserInfo
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.manga.model.toMangaUserInfoUpdate
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.State as RuntimeState

@AssistedInject
class MangaViewModel(
    @Assisted private val mangaId: Long,
    // SY -->
    /** If it is opened from Source then it will auto expand the manga description */
    @Assisted private val isFromSource: Boolean,
    @Assisted private val smartSearched: Boolean,
    // SY <--
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val context: Context,
    private val libraryPreferences: LibraryPreferences,
    private val trackPreferences: TrackPreferences,
    private val readerPreferences: ReaderPreferences,
    private val trackerManager: TrackerManager,
    private val trackChapter: TrackChapter,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val getMangaAndChapters: GetMangaWithChapters,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getAvailableScanlators: GetAvailableScanlators,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val setExcludedScanlators: SetExcludedScanlators,
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val setReadStatus: SetReadStatus,
    private val updateChapter: UpdateChapter,
    private val updateManga: UpdateManga,
    private val getCategories: GetCategories,
    private val getTracks: GetTracks,
    private val addTracks: AddTracks,
    private val setMangaCategories: SetMangaCategories,
    private val filterChaptersForDownload: FilterChaptersForDownload,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val sourceManager: SourceManager,
    private val refreshTracks: RefreshTracks,
    private val coverCache: CoverCache,
    private val uiPreferences: UiPreferences,
    // KMK -->
    private val sourcePreferences: SourcePreferences,
    private val downloadProvider: DownloadProvider,
    // KMK <--
    // SY -->
    private val updateHelper: EHentaiUpdateHelper,
    // SY <--
    private val getManga: GetManga,
    // KNS -->
    private val scanlatorPreferenceWriter: ScanlatorPreferenceWriter,
    // KNS <--
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    private val getMergedMangaById: GetMergedMangaById,
    private val getMergedReferencesById: GetMergedReferencesById,
    private val smartSearchMerge: SmartSearchMerge,
    private val updateMergedSettings: UpdateMergedSettings,
    private val networkToLocalManga: NetworkToLocalManga,
    private val deleteMergeById: DeleteMergeById,
    private val getFlatMetadata: GetFlatMetadataById,
    private val getPagePreviews: GetPagePreviews,
    private val upsertTrack: UpsertTrack,
    private val setMangaUserInfo: SetMangaUserInfo,
    // KUN -->
    private val syncStateBumper: SyncStateBumper,
    // KUN <--
    private val deleteLibraryUpdateErrors: DeleteLibraryUpdateErrors,
    private val insertLibraryUpdateErrors: InsertLibraryUpdateErrors,
    private val insertLibraryUpdateErrorMessages: InsertLibraryUpdateErrorMessages,
    private val deleteChaptersFromDb: DeleteChapters,
    // KNS -->
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getSequelPrequel: GetSequelPrequel,
    // KNS <--
) : ViewModel() {

    private val subscriberGate = SubscriberGate()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long, isFromSource: Boolean, smartSearched: Boolean): MangaViewModel
    }

    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    private val successState: State.Success?
        get() = state.value as? State.Success

    // KMK -->
    val useNewSourceNavigation by uiPreferences.useNewSourceNavigation.asState(viewModelScope)
    val themeCoverBased = uiPreferences.themeCoverBased.get()
    // KMK <--

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = manga?.favorite ?: false

    private val allChapters: List<ChapterList.Item>?
        get() = successState?.chapters

    private val filteredChapters: List<ChapterList.Item>?
        get() = successState?.processedChapters

    val chapterSwipeStartAction = libraryPreferences.swipeToEndAction.get()
    val chapterSwipeEndAction = libraryPreferences.swipeToStartAction.get()
    private var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead.get()

    private val skipFiltered by readerPreferences.skipFiltered.asState(viewModelScope)

    val isUpdateIntervalEnabled =
        LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateMangaRestrictions.get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedChapterIds = MutableStateFlow(emptySet<Long>())

    internal var showTrackDialogAfterCategorySelection: Boolean = false

    internal val autoOpenTrack: Boolean
        get() = successState?.hasLoggedInTrackers == true && trackPreferences.trackOnAddingToLibrary.get()

    // EXH -->
    val redirectFlow: MutableSharedFlow<EXHRedirect> = MutableSharedFlow()

    data class EXHRedirect(val mangaId: Long)
    // EXH <--

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val isRefreshingData = MutableStateFlow(false)

    private val downloadStates = MutableStateFlow(emptyMap<Long, Download.State>())

    // Progress ticks often, so only the download indicators that read it recompose instead of every tick rebuilding
    // the chapter list
    private val downloadProgress = mutableStateMapOf<Long, Int>()

    // A finished chapter leaves the queue, and its last status change can be lost with it, so its state is left to
    // the queried item once it's no longer queued
    private val queuedDownloadStates = combine(downloadStates, downloadManager.queueState) { states, queue ->
        val queuedChapterIds = queue.mapTo(HashSet()) { it.chapter.id }
        states.filterKeys { it in queuedChapterIds }
    }

    private val hideMissingChapters = libraryPreferences.hideMissingChapters.get()

    private var hasPromptedToAddBefore = false

    // SY -->
    private val pagePreviewsState = MutableStateFlow<PagePreviewState>(PagePreviewState.Loading)
    private val relatedMangaCollection = MutableStateFlow<List<RelatedManga>?>(null)
    private val isRelatedMangasFetched = MutableStateFlow<Boolean?>(null)
    private val seedColor = MutableStateFlow<Color?>(null)

    private val mergedData = flow {
        emitAll(
            combine(
                getMergedMangaById.subscribe(mangaId)
                    .distinctUntilChanged(),
                getMergedReferencesById.subscribe(mangaId)
                    .distinctUntilChanged(),
            ) { manga, references ->
                if (manga.isNotEmpty()) {
                    MergedMangaData(
                        references,
                        manga.associateBy { it.id },
                        references.map { it.mangaSourceId }.distinct()
                            .map { sourceManager.getOrStub(it) },
                    )
                } else {
                    null
                }
            },
        )
    }
    // SY <--

    // KNS --> read once per opened entry, so changing them while it is open starts nothing
    private val autoRefreshOnOpen = libraryPreferences.autoRefreshOpenedManga.get()
    private val autoResetFlagsOnOpen = libraryPreferences.autoResetChapterFlagsOnRefresh.get()
    // KNS <--

    private val defaultChapterFlagsJob = viewModelScope.launchIO {
        val manga = getMangaAndChapters.awaitManga(mangaId)
        // KNS --> also when an auto-refreshed entry resets its flags (once, when it is opened)
        if (OpenRefresh.of(manga, hasChapters = true, autoRefreshOnOpen, autoResetFlagsOnOpen).resetChapterFlags) {
            // KNS <--
            setMangaDefaultChapterFlags.await(manga)
        }
    }

    private val baseMangaAndChapters: Flow<Pair<Manga, List<Chapter>>> = combine(
        flow<Pair<Manga, List<Chapter>>> {
            // So an entry outside the library isn't shown with its old chapter settings first
            defaultChapterFlagsJob.join()
            emitAll(getMangaAndChapters.subscribe(mangaId, applyFilter = true))
        }
            .distinctUntilChanged(),
        // SY -->
        flow<List<Chapter>> {
            emitAll(getMergedChaptersByMangaId.subscribe(mangaId, true, applyFilter = true))
        }
            .distinctUntilChanged(),
        // SY <--
    ) { (manga, chapters), mergedChapters ->
        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            manga to mergedChapters
        } else {
            // SY <--
            manga to chapters
            // SY -->
        }
        // SY <--
    }
        // EXH -->
        .onEach { (manga, chapters) ->
            if (chapters.isNotEmpty() &&
                manga.isEhBasedManga() &&
                DebugToggles.ENABLE_EXH_ROOT_REDIRECT.enabled
            ) {
                // Check for gallery in library and accept manga with lowest id
                // Find chapters sharing same root
                // KUN -->
                viewModelScope.launchIO {
                    // KUN <--
                    try {
                        val (acceptedChain) = updateHelper.findAcceptedRootAndDiscardOthers(manga.source, chapters)
                        // Redirect if we are not the accepted root
                        if (manga.id != acceptedChain.manga.id && acceptedChain.manga.favorite) {
                            // Update if any of our chapters are not in accepted manga's chapters
                            xLogD("Found accepted manga %s", manga.url)
                            redirectFlow.emit(
                                EXHRedirect(acceptedChain.manga.id),
                            )
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Error loading accepted chapter chain" }
                    }
                }
            }
        }
    // EXH <--

    // KNS -->
    private val visibilitySettings = combine(
        libraryPreferences.smartScanlatorMerge.changes(),
        libraryPreferences.hideBlacklistedChapters(mangaId).changes(),
        combine(
            libraryPreferences.bookmarkColorFilter(mangaId).changes(),
            libraryPreferences.bookmarkColorFilterExclude(mangaId).changes(),
        ) { included, excluded -> BookmarkColor.fromNames(included) to BookmarkColor.fromNames(excluded) },
        ::Triple,
    ).distinctUntilChanged()
    // KNS <--

    private val mangaAndChapters = combine(
        baseMangaAndChapters,
        getFlatMetadata.subscribe(mangaId)
            .distinctUntilChanged(),
        mergedData,
        combine(
            downloadCache.changes.pausedWhileUnsubscribed(),
            downloadManager.queueState.pausedWhileUnsubscribed(),
        ) { _, _ -> Unit },
        // KNS -->
        visibilitySettings,
        // KNS <--
    ) { (manga, chapters), flatMetadata, mergedData, _, (globalSmartMerge, hideBlacklisted, colorFilters) ->
        // The same visibility rule as the reader, History, downloads and library counts; revealed
        // blacklisted chapters are listed but flagged.
        val visibility = chapters.chapterListVisibility(
            manga = manga,
            smartScanlatorMergeEnabled = manga.smartScanlatorMergeEnabled(globalSmartMerge),
            hideBlacklistedChapters = hideBlacklisted,
        )
        // KNS <--
        MangaAndChapters(
            manga = manga,
            source = sourceManager.getOrStub(manga.source),
            chapters = visibility.chapters.toChapterListItems(
                manga,
                mergedData,
                // KNS -->
                visibility.blacklistedChapterIds,
                // KNS <--
            ),
            flatMetadata = flatMetadata,
            mergedData = mergedData,
            // KNS -->
            blacklistedCount = visibility.blacklistedChapterIds.size,
            hideBlacklistedChapters = hideBlacklisted,
            bookmarkColorFilter = colorFilters.first,
            bookmarkColorFilterExclude = colorFilters.second,
            // KNS <--
        )
    }

    private val scanlators = combine(
        getAvailableScanlators.subscribe(mangaId).distinctUntilChanged(),
        getExcludedScanlators.subscribe(mangaId).distinctUntilChanged(),
        // SY -->
        flow { emit(getMangaAndChapters.awaitManga(mangaId).source) }
            .distinctUntilChanged()
            .flatMapLatest { source ->
                if (source == MERGED_SOURCE_ID) {
                    getAvailableScanlators.subscribeMerge(mangaId)
                } else {
                    flowOf(emptySet())
                }
            },
        // SY <--
    ) { mangaScanlators, excludedScanlators, mergeScanlators ->
        (mangaScanlators + mergeScanlators) to excludedScanlators
    }

    private val trackers = flow {
        val manga = getMangaAndChapters.awaitManga(mangaId)
        emit(manga to sourceManager.getOrStub(manga.source))
    }
        .flatMapLatest { (manga, source) ->
            combine(
                getTracks.subscribe(mangaId).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
                // SY -->
                mergedData,
                // SY <--
            ) { mangaTracks, loggedInTrackers, mergedData ->
                // Show only if the service supports this manga's source
                // KMK -->
                val supportedTrackers = if (source is MergedSource) {
                    val sources = mergedData?.sources ?: emptyList()
                    loggedInTrackers.filter { (it as? EnhancedTracker)?.accept(sources) ?: true }
                } else {
                    loggedInTrackers.filter { (it as? EnhancedTracker)?.accept(source) ?: true }
                }
                // KMK <--
                val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
                val supportedTrackerTracks = mangaTracks.filter { it.trackerId in supportedTrackerIds }
                // SY -->
                val tracks = if (manga.source in mangaDexSourceIds ||
                    mergedData?.manga?.values.orEmpty().any {
                        it.source in mangaDexSourceIds
                    }
                ) {
                    val mdTrack = supportedTrackers.firstOrNull { it is MdList }
                    when {
                        mdTrack == null -> supportedTrackerTracks
                        // KMK: auto track MangaDex
                        mdTrack.id !in supportedTrackerTracks.map { it.trackerId } -> {
                            createMdListTrack(manga, mergedData)?.let { supportedTrackerTracks + it }
                                ?: supportedTrackerTracks
                        }
                        else -> supportedTrackerTracks
                    }
                } else {
                    supportedTrackerTracks
                }
                tracks
                    .filter {
                        it.trackerId != trackerManager.mdList.id ||
                            it.status != FollowStatus.UNFOLLOWED.long
                    }
                    .size to supportedTrackers.isNotEmpty()
                // SY <--
            }
                .distinctUntilChanged()
        }
        .onStart { emit(0 to false) }

    // KNS --> related titles from the trackers; refetched when the bindings or the preferred tracker change
    private val sequelPrequel = uiPreferences.showSequelPrequel.changes()
        .flatMapLatest { enabled ->
            if (!enabled) return@flatMapLatest flowOf(emptyList())
            combine(
                flow { emit(getMangaAndChapters.awaitManga(mangaId)) },
                getTracks.subscribe(mangaId).distinctUntilChanged { old, new ->
                    old.map { it.trackerId to it.remoteId } == new.map { it.trackerId to it.remoteId }
                },
                trackPreferences.preferredTrackerForManga.changes(),
                trackPreferences.priorityTrackerId.changes(),
                trackerManager.loggedInTrackersFlow().map { trackers -> trackers.map { it.id } },
            ) { manga, tracks, _, _, _ -> manga to tracks }
                .mapLatest { (manga, tracks) ->
                    try {
                        getSequelPrequel.await(manga, tracks)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Failed to load related titles" }
                        emptyList()
                    }
                }
        }
        .catch { emit(emptyList()) }
    // KNS <--

    private val uiExtras = combine(
        pagePreviewsState,
        relatedMangaCollection,
        isRelatedMangasFetched,
        seedColor,
        // KNS -->
        sequelPrequel,
        // KNS <--
    ) { pagePreviewsState, relatedMangaCollection, isRelatedMangasFetched, seedColor, sequelPrequel ->
        UiExtras(
            pagePreviewsState = pagePreviewsState,
            relatedMangaCollection = relatedMangaCollection,
            isRelatedMangasFetched = isRelatedMangasFetched,
            seedColor = seedColor,
            // KNS -->
            sequelPrequel = sequelPrequel,
            // KNS <--
        )
    }

    val state: StateFlow<State> = combine(
        mangaAndChapters,
        scanlators,
        trackers,
        combine(selectedChapterIds, queuedDownloadStates.pausedWhileUnsubscribed(), ::Pair),
        combine(dialog, isRefreshingData, uiExtras) { dialog, isRefreshingData, extras ->
            DialogAndExtras(dialog, isRefreshingData, extras)
        },
    ) {
            mangaAndChapters,
            (availableScanlators, excludedScanlators),
            (trackingCount, hasLoggedInTrackers),
            (selectedIds, downloads),
            (dialog, isRefreshingData, extras),
        ->
        State.Success(
            manga = mangaAndChapters.manga,
            source = mangaAndChapters.source,
            isFromSource = isFromSource,
            chapters = mangaAndChapters.chapters.map { item ->
                val downloadState = downloads[item.id]
                val selected = item.id in selectedIds
                if (downloadState == null && !selected) return@map item
                item.copy(
                    downloadState = downloadState ?: item.downloadState,
                    selected = selected,
                )
            },
            availableScanlators = availableScanlators,
            excludedScanlators = excludedScanlators,
            trackingCount = trackingCount,
            hasLoggedInTrackers = hasLoggedInTrackers,
            isRefreshingData = isRefreshingData,
            dialog = dialog,
            hideMissingChapters = hideMissingChapters,
            // SY -->
            meta = raiseMetadata(mangaAndChapters.flatMetadata, mangaAndChapters.source),
            mergedData = mangaAndChapters.mergedData,
            showRecommendationsInOverflow = uiPreferences.recommendsInOverflow.get(),
            showMergeInOverflow = uiPreferences.mergeInOverflow.get(),
            showMergeWithAnother = smartSearched,
            pagePreviewsState = extras.pagePreviewsState,
            alwaysShowReadingProgress =
            readerPreferences.preserveReadingPosition.get() && mangaAndChapters.manga.isEhBasedManga(),
            previewsRowCount = uiPreferences.previewsRowCount.get(),
            // SY <--
            // KMK -->
            isRelatedMangasFetched = extras.isRelatedMangasFetched,
            relatedMangaCollection = extras.relatedMangaCollection,
            seedColor = extras.seedColor ?: mangaAndChapters.manga.asMangaCover().vibrantCoverColor?.let { Color(it) },
            // KMK <--
            // KNS -->
            blacklistedChapterCount = mangaAndChapters.blacklistedCount,
            hideBlacklistedChapters = mangaAndChapters.hideBlacklistedChapters,
            bookmarkColorFilter = mangaAndChapters.bookmarkColorFilter,
            sequelPrequel = extras.sequelPrequel,
            // KNS <--
        )
    }
        // Composition would otherwise build these on first read, on the main thread, for every new state
        .onEach {
            it.processedChapters
            it.chapterListItems
            it.isAnySelected
            it.selectedChapters
            it.hasUnreadChapters
            it.hasReadChapters
            it.missingChapterCount
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope + subscriberGate, subscriberGate.whileSubscribed(5.seconds), State.Loading)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            downloadManager.statusFlow()
                // SY: a merged entry's chapters span several mangas, so downloads aren't filtered by manga
                // id here; the combined state maps them back by chapter id.
                .catch { logcat(LogPriority.ERROR, it) }
                .collect(::updateDownloadState)
        }
        viewModelScope.launch(Dispatchers.Default) {
            // Each tick rebuilds the shown chapter items, so cap how often that happens
            downloadManager.progressFlow().sample(200.milliseconds)
                // SY: same as above, downloads aren't filtered by manga id for merged entries.
                .catch { logcat(LogPriority.ERROR, it) }
                .collect { downloadProgress[it.chapter.id] = it.progress }
        }

        viewModelScope.launchIO {
            // The state loads the entry and its chapters anyway, so they aren't queried separately
            val loadedState = state.filterIsInstance<State.Success>().first()
            val manga = loadedState.manga
            val source = loadedState.source

            // SY -->
            if (source.getMainSource() is PagePreviewSource) {
                getPagePreviews(manga, source)
            } else {
                pagePreviewsState.value = PagePreviewState.Unused
            }
            // SY <--

            // KNS --> an opened entry is refreshed when "Automatically refresh opened manga" is on
            val refresh = OpenRefresh.of(
                manga = manga,
                hasChapters = loadedState.chapters.isNotEmpty(),
                autoRefresh = autoRefreshOnOpen,
                autoResetFlags = autoResetFlagsOnOpen,
            )
            val needRefreshInfo = refresh.fetchDetails
            val needRefreshChapter = refresh.fetchChapters
            // KNS <--

            // Fetch info-chapters when needed
            if (needRefreshInfo || needRefreshChapter) {
                isRefreshingData.value = true
                fetchAllFromSource(
                    manga = manga,
                    manualFetch = false,
                    fetchDetails = needRefreshInfo,
                    fetchChapters = needRefreshChapter,
                )
                isRefreshingData.value = false
            }

            // KMK -->
            launch { syncTrackers() }
            launch { fetchRelatedMangasFromSource() }
            // KMK <--
        }
    }

    // KMK -->
    /**
     * Get the color of the manga cover by loading cover with ImageRequest directly from network.
     */
    fun setPaletteColor(model: Any) {
        if (model is ImageRequest && model.defined.sizeResolver != null) return

        val imageRequestBuilder = if (model is ImageRequest) {
            model.newBuilder()
        } else {
            ImageRequest.Builder(context).data(model)
        }
            .allowHardware(false)

        val generatePalette: (Image) -> Unit = { image ->
            val bitmap = image.asDrawable(context.resources).getBitmapOrNull()
            if (bitmap != null) {
                Palette.from(bitmap).generate {
                    viewModelScope.launchIO {
                        if (it == null) return@launchIO
                        val mangaCover = when (model) {
                            is Manga -> model.asMangaCover()
                            is MangaCover -> model
                            else -> return@launchIO
                        }
                        if (mangaCover.isMangaFavorite) {
                            it.dominantSwatch?.let { swatch ->
                                mangaCover.dominantCoverColors = swatch.rgb to swatch.titleTextColor
                            }
                        }
                        val vibrantColor = it.getBestColor() ?: return@launchIO
                        mangaCover.vibrantCoverColor = vibrantColor
                        seedColor.value = Color(vibrantColor)
                    }
                }
            }
        }

        context.imageLoader.enqueue(
            imageRequestBuilder
                .target(
                    onSuccess = generatePalette,
                    onError = {
                        // TODO: handle error
                        // val file = coverCache.getCoverFile(manga!!)
                        // if (file.exists()) {
                        //     file.delete()
                        //     setPaletteColor()
                        // }
                    },
                )
                .build(),
        )
    }

    private suspend fun syncTrackers() {
        if (!trackPreferences.autoSyncProgressFromTrackers.get()) return
        refreshTrackers(enhancedTrackersOnly = false)
    }
    // KMK <--

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        viewModelScope.launch {
            isRefreshingData.value = true
            fetchAllFromSource(
                manga = getMangaAndChapters.awaitManga(mangaId),
                manualFetch = manualFetch,
                fetchDetails = true,
                fetchChapters = true,
            )
            // KMK -->
            launch { syncTrackers() }
            // KMK <--
            isRefreshingData.value = false
        }
    }

    private suspend fun fetchAllFromSource(
        manga: Manga,
        manualFetch: Boolean,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) {
        try {
            withIOContext {
                val update = updateMangaFromRemote(
                    source = sourceManager.getOrStub(manga.source),
                    manga = manga,
                    fetchDetails = fetchDetails,
                    fetchChapters = fetchChapters,
                    manualFetch = manualFetch,
                )
                    .getOrThrow()

                // KMK -->
                clearErrorFromDB(manga.id)
                // KMK <--

                if (manualFetch) {
                    downloadNewChapters(update.newChapters)
                }
            }
        } catch (_: CancellationException) {
            // ignore
        } catch (e: Exception) {
            val message = if (e is NoChaptersException) {
                context.stringResource(MR.strings.no_chapters_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            viewModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            // KMK -->
            writeErrorToDB(manga to with(context) { e.formattedMessage })
            // KMK <--
        }
    }

    // KMK -->
    private suspend fun clearErrorFromDB(mangaId: Long) {
        deleteLibraryUpdateErrors.deleteMangaError(mangaIds = listOf(mangaId))
    }

    private suspend fun writeErrorToDB(error: Pair<Manga, String?>) {
        val errorMessage = error.second ?: context.stringResource(MR.strings.unknown_error)
        val errorMessageId = insertLibraryUpdateErrorMessages.insert(
            libraryUpdateErrorMessage = LibraryUpdateErrorMessage(-1L, errorMessage),
        )

        insertLibraryUpdateErrors.upsert(
            LibraryUpdateError(id = -1L, mangaId = error.first.id, messageId = errorMessageId),
        )
    }
    // KMK <--

    // SY -->
    private fun raiseMetadata(flatMetadata: FlatMetadata?, source: Source): RaisedSearchMetadata? {
        return if (flatMetadata != null) {
            val metaClass = source.getMainSource<MetadataSource<*, *>>()?.metaClass
            if (metaClass != null) flatMetadata.raise(metaClass) else null
        } else {
            null
        }
    }

    fun updateMangaInfo(
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) {
        val state = successState ?: return
        var manga = state.manga
        if (state.manga.isLocal()) {
            val newTitle = if (title.isNullOrBlank()) manga.url else title.trim()
            val newAuthor = author?.trimOrNull()
            val newArtist = artist?.trimOrNull()
            val newThumbnailUrl = thumbnailUrl?.trimOrNull()
            val newDesc = description?.trimOrNull()
            manga = manga.copy(
                ogTitle = newTitle,
                ogAuthor = author?.trimOrNull(),
                ogArtist = artist?.trimOrNull(),
                ogThumbnailUrl = thumbnailUrl?.trimOrNull(),
                ogDescription = description?.trimOrNull(),
                ogGenre = tags?.nullIfEmpty(),
                ogStatus = status ?: 0,
                lastUpdate = manga.lastUpdate?.plus(1.milliseconds) ?: Clock.System.now(),
            )
            viewModelScope.launchNonCancellable {
                val localSource = sourceManager.get(LocalSource.ID) as LocalSource
                localSource.updateMangaInfo(manga.toSManga())
                updateManga.awaitRemote(
                    MangaRemoteUpdate(
                        id = manga.id,
                        title = newTitle,
                        author = newAuthor,
                        artist = newArtist,
                        thumbnailUrl = newThumbnailUrl,
                        description = newDesc,
                        genre = tags,
                        status = status ?: manga.ogStatus,
                        updateStrategy = manga.updateStrategy,
                        memo = manga.memo,
                        initialized = manga.initialized,
                        coverLastModified = null,
                    ),
                )
            }
        } else {
            val genre = if (!tags.isNullOrEmpty() && tags != state.manga.ogGenre) {
                tags
            } else {
                null
            }
            val customInfo = MangaUserInfoUpdate(
                state.manga.id,
                title?.trimOrNull(),
                author?.trimOrNull(),
                artist?.trimOrNull(),
                thumbnailUrl?.trimOrNull(),
                description?.trimOrNull(),
                genre,
                status.takeUnless { it == state.manga.ogStatus },
            )
            // KUN -->
            // The edit must survive leaving the screen right after it: the view model scope is
            // cancelled then. The app-wide, non-cancellable launch still runs off the UI thread, and
            // it runs the write and the sync bump in order. The value the row held before the write
            // is the one the edit is compared against.
            val previousCustomInfo = state.manga.toMangaUserInfoUpdate()
            appScope.launchNonCancellable {
                setMangaUserInfo.setAll(listOf(customInfo))
                syncStateBumper.bumpForCustomInfoEdit(customInfo.id, previousCustomInfo, customInfo)
            }
            // KUN <--
        }
    }

    // KMK -->
    @Composable
    fun getManga(initialManga: Manga): RuntimeState<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    suspend fun smartSearchMerge(manga: Manga, originalMangaId: Long): Manga {
        return smartSearchMerge.smartSearchMerge(manga, originalMangaId)
    }
    // KMK <--

    fun updateMergeSettings(mergedMangaReferences: List<MergedMangaReference>) {
        viewModelScope.launchNonCancellable {
            if (mergedMangaReferences.isNotEmpty()) {
                updateMergedSettings.awaitAll(
                    mergedMangaReferences.map {
                        MergeMangaSettingsUpdate(
                            id = it.id,
                            isInfoManga = it.isInfoManga,
                            getChapterUpdates = it.getChapterUpdates,
                            chapterPriority = it.chapterPriority,
                            downloadChapters = it.downloadChapters,
                            chapterSortMode = it.chapterSortMode,
                        )
                    },
                )
            }
        }
    }

    fun deleteMerge(reference: MergedMangaReference) {
        viewModelScope.launchNonCancellable {
            deleteMergeById.await(reference.id)
        }
    }
    // SY <--

    // Manga info - start

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                viewModelScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.stringResource(MR.strings.delete_downloads_for_manga),
                        actionLabel = context.stringResource(MR.strings.action_delete),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        deleteDownloads()
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val manga = state.manga

            if (isFavorited) {
                // Remove from library
                if (updateManga.awaitUpdateFavorite(manga.id, false)) {
                    manga.removeCovers(coverCache)
                    onRemoved()
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicates = getDuplicateLibraryManga(manga)

                    if (duplicates.isNotEmpty()) {
                        dialog.value = Dialog.DuplicateManga(manga, duplicates)
                        return@launch
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launch
                        moveMangaToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(manga.id, true)
                        if (!result) return@launch
                        moveMangaToCategory(null)
                    }

                    // Choose a category
                    else -> {
                        showTrackDialogAfterCategorySelection = true
                        showChangeCategoryDialog()
                    }
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(manga, state.source)
                if (autoOpenTrack && !showTrackDialogAfterCategorySelection) {
                    showTrackDialog()
                }
            }
        }
    }

    fun showRemoveFromLibraryDialog() {
        if (successState?.manga?.favorite == true) dialog.value = Dialog.RemoveFromLibrary
    }

    fun showChangeCategoryDialog() {
        val manga = successState?.manga ?: return
        viewModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            dialog.value = Dialog.ChangeCategory(
                manga = manga,
                initialSelection = categories.mapAsCheckboxState { it.id in selection }.toList(),
            )
        }
    }

    fun showSetFetchIntervalDialog() {
        val manga = successState?.manga ?: return
        dialog.value = Dialog.SetFetchInterval(manga)
    }

    fun setFetchInterval(manga: Manga, interval: Int) {
        viewModelScope.launch {
            updateManga.awaitUpdateFetchInterval(
                // Custom intervals are negative
                manga.copy(fetchInterval = -interval),
            )
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val manga = successState?.manga ?: return false
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    private suspend fun deleteDownloads() {
        val state = successState ?: return
        // SY -->
        if (state.source is MergedSource) {
            val mergedManga = state.mergedData?.manga?.map { it.value to sourceManager.getOrStub(it.value.source) }
            mergedManga?.forEach { (manga, source) ->
                downloadManager.deleteManga(manga, source)
            }
        } else {
            /* SY <-- */ downloadManager.deleteManga(state.manga, state.source)
        }
    }

    /**
     * Opens manga folder with the system's file manager.
     */
    fun openMangaFolder(currentSource: Source?, currentManga: Manga?) {
        viewModelScope.launch {
            try {
                if (currentManga == null || currentSource == null || currentSource is StubSource) return@launch

                val mangaDir = downloadProvider.findMangaDir(/* SY --> */ currentManga.ogTitle /* SY <-- */, currentSource) ?: return@launch
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(mangaDir.uri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                context.toast(e.message ?: context.stringResource(KMR.strings.error_opening_folder))
            }
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(categories)
        if (manga.favorite) return

        viewModelScope.launch {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    /**
     * Move the given manga to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveMangaToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveMangaToCategory(categoryIds)
    }

    private fun moveMangaToCategory(categoryIds: List<Long>) {
        viewModelScope.launch {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    /**
     * Move the given manga to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveMangaToCategory(category: Category?) {
        moveMangaToCategories(listOfNotNull(category))
    }

    // Manga info - end

    // Chapters list - start

    private fun updateDownloadState(download: Download) {
        val chapterId = download.chapter.id
        downloadStates.update {
            // Terminal states are derived by the queried item itself, so drop the override instead
            // of letting it outlive reality, e.g. showing a since deleted chapter as downloaded.
            if (download.status == Download.State.NOT_DOWNLOADED || download.status == Download.State.DOWNLOADED) {
                it - chapterId
            } else {
                it + (chapterId to download.status)
            }
        }
        if (download.status == Download.State.DOWNLOADING) {
            downloadProgress[chapterId] = download.progress
        } else {
            downloadProgress.remove(chapterId)
        }
    }

    fun getDownloadProgress(chapterId: Long): Int {
        return downloadProgress[chapterId] ?: 0
    }

    private fun List<Chapter>.toChapterListItems(
        manga: Manga,
        // SY -->
        mergedData: MergedMangaData?,
        // SY <--
        // KNS -->
        blacklistedIds: Set<Long> = emptySet(),
        // KNS <--
    ): List<ChapterList.Item> {
        val isLocal = manga.isLocal()
        // SY -->
        val isExhManga = manga.isEhBasedManga()
        // SY <--
        val queuedDownloads = if (isLocal) emptyMap() else downloadManager.getQueuedDownloadsByChapterId()
        // SY -->
        // A merged manga's chapters can belong to different mangas
        val downloadedChapterIds = if (isLocal) {
            emptySet()
        } else {
            groupBy { chapter -> mergedData?.manga?.get(chapter.mangaId) ?: manga }
                .flatMap { (chapterManga, chapters) ->
                    if (chapterManga.isLocal()) {
                        chapters.map { it.id }
                    } else {
                        downloadManager.getDownloadedChapterIds(chapters, chapterManga)
                    }
                }
                .toSet()
        }
        // SY <--
        return map { chapter ->
            val activeDownload = queuedDownloads[chapter.id]

            // SY -->
            @Suppress("NAME_SHADOWING")
            val manga = mergedData?.manga?.get(chapter.mangaId) ?: manga
            val source = mergedData?.sources?.find { manga.source == it.id }?.takeIf { mergedData.sources.size > 2 }
            // SY <--
            val downloaded = manga.isLocal() || chapter.id in downloadedChapterIds
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }

            ChapterList.Item(
                chapter = chapter,
                downloadState = downloadState,
                // SY -->
                sourceName = source?.getNameForMangaInfo(),
                showScanlator = !isExhManga,
                // SY <--
                // KNS -->
                blacklisted = chapter.id in blacklistedIds,
                // KNS <--
            )
        }
    }

    // SY -->
    private fun getPagePreviews(manga: Manga, source: Source) {
        viewModelScope.launchIO {
            when (val result = getPagePreviews.await(manga, source, 1)) {
                is GetPagePreviews.Result.Error -> pagePreviewsState.value = PagePreviewState.Error(result.error)
                is GetPagePreviews.Result.Success ->
                    pagePreviewsState.value = PagePreviewState.Success(result.pagePreviews)
                GetPagePreviews.Result.Unused -> pagePreviewsState.value = PagePreviewState.Unused
            }
        }
    }
    // SY <--

    // KMK -->
    /**
     * Set the fetching related mangas status.
     * @param state
     * - false: started & fetching
     * - true: finished
     */
    private fun setRelatedMangasFetchedStatus(state: Boolean) {
        isRelatedMangasFetched.value = state
    }

    /**
     * Requests an list of related mangas from the source.
     */
    internal suspend fun fetchRelatedMangasFromSource(onDemand: Boolean = false, onFinish: (() -> Unit)? = null) {
        val expandRelatedMangas = uiPreferences.expandRelatedMangas.get()
        if ((!onDemand && !expandRelatedMangas) || manga?.source == MERGED_SOURCE_ID) return

        // start fetching related mangas
        setRelatedMangasFetchedStatus(false)

        fun exceptionHandler(e: Throwable) {
            logcat(LogPriority.ERROR, e)
            val message = with(context) { e.formattedMessage }

            viewModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
        }
        val state = successState ?: return
        val relatedMangasEnabled = sourcePreferences.relatedMangas.get()

        try {
            if (state.source !is StubSource && relatedMangasEnabled) {
                state.source.getRelatedMangaList(state.manga.toSManga(), { e -> exceptionHandler(e) }) { pair, _ ->
                    /* Push found related mangas into collection */
                    val relatedManga = RelatedManga.Success.fromPair(pair) { mangaList ->
                        mangaList
                            .map { it.toDomainManga(state.source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(manga = it, updateInfo = false) }
                    }

                    relatedMangaCollection.update { it.orEmpty() + relatedManga }
                }
            }
        } catch (e: Exception) {
            exceptionHandler(e)
        } finally {
            if (onFinish != null) {
                onFinish()
            } else {
                setRelatedMangasFetchedStatus(true)
            }
        }
    }
    // KMK <--

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterItem: ChapterList.Item, swipeAction: LibraryPreferences.ChapterSwipeAction) {
        viewModelScope.launch {
            executeChapterSwipeAction(chapterItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun executeChapterSwipeAction(
        chapterItem: ChapterList.Item,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val chapter = chapterItem.chapter
        when (swipeAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markChaptersRead(listOf(chapter), !chapter.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction: ChapterDownloadAction = when (chapterItem.downloadState) {
                    Download.State.ERROR,
                    Download.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    Download.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(chapterItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): Chapter? {
        val successState = successState ?: return null
        return successState.chapters.getNextUnread(successState.manga)
    }

    private fun getUnreadChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> !chapter.read && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun getUnreadChaptersSorted(): List<Chapter> {
        val manga = successState?.manga ?: return emptyList()
        val chaptersSorted = getUnreadChapters().sortedWith(getChapterSort(manga))
            // SY -->
            .let {
                if (manga.isEhBasedManga()) it.reversed() else it
            }
        // SY <--
        return if (manga.sortDescending()) chaptersSorted.reversed() else chaptersSorted
    }

    private fun getBookmarkedChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> chapter.bookmark && dlStatus == Download.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
    ) {
        if (successState == null) return
        val startNowId = if (startNow) chapters.singleOrNull()?.id ?: return else null

        viewModelScope.launch {
            // The queueing outlives the screen, the add to library prompt doesn't
            appScope.launch {
                if (startNowId != null) {
                    downloadManager.startDownloadNow(startNowId)
                } else {
                    downloadChapters(chapters)
                }
            }.join()
            if (startNowId == null) toggleAllSelection(false)

            if (!isFavorited && !hasPromptedToAddBefore) {
                hasPromptedToAddBefore = true
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(MR.strings.snack_add_to_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runChapterDownloadActions(
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
    ) {
        when (action) {
            ChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false)
                if (items.any { it.downloadState == Download.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            ChapterDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true)
            }
            ChapterDownloadAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.id ?: return
                cancelDownload(chapterId)
            }
            ChapterDownloadAction.DELETE -> {
                deleteChapters(items.map { it.chapter })
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_CHAPTERS -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_CHAPTERS -> getUnreadChaptersSorted().take(10)
            DownloadAction.NEXT_25_CHAPTERS -> getUnreadChaptersSorted().take(25)
            DownloadAction.UNREAD_CHAPTERS -> getUnreadChapters()
            DownloadAction.BOOKMARKED_CHAPTERS -> getBookmarkedChapters()
        }
        if (chaptersToDownload.isNotEmpty()) {
            startDownload(chaptersToDownload, false)
        }
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        viewModelScope.launch {
            downloadManager.cancelQueuedDownloads(listOf(activeDownload))
            updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
        }
    }

    fun markPreviousChapterRead(pointer: Chapter) {
        val manga = successState?.manga ?: return
        val chapters = filteredChapters.orEmpty().map { it.chapter }
        val prevChapters = if (manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        toggleAllSelection(false)
        if (chapters.isEmpty()) return
        viewModelScope.launch {
            // The read state is written even if the screen closes, the tracking prompt isn't
            appScope.launch {
                setReadStatus.await(
                    read = read,
                    chapters = chapters.toTypedArray(),
                )
            }.join()

            if (!read || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launch
            }

            refreshTrackers()

            val tracks = getTracks.await(mangaId)
            val maxChapterNumber = chapters.maxOf { it.chapterNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxChapterNumber > track.lastChapterRead }

            if (!shouldPromptTrackingUpdate) return@launch
            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackChapter.await(context, mangaId, maxChapterNumber)
                context.toast(context.stringResource(MR.strings.trackers_updated_summary, maxChapterNumber.toInt()))
                return@launch
            }

            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.confirm_tracker_update, maxChapterNumber.toInt()),
                actionLabel = context.stringResource(MR.strings.action_ok),
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )

            if (result == SnackbarResult.ActionPerformed) {
                trackChapter.await(context, mangaId, maxChapterNumber)
            }
        }
    }

    private suspend fun refreshTrackers(
        // KMK -->
        enhancedTrackersOnly: Boolean = true,
        // KMK <--
    ) {
        // KMK -->
        refreshTracks.await(mangaId, enhancedTrackersOnly = enhancedTrackersOnly)
            // KMK <--
            .filter { it.first != null }
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data mangaId=$mangaId for service ${track!!.id}"
                }
                withContext(Dispatchers.Main) {
                    context.toast(
                        context.stringResource(
                            MR.strings.track_error,
                            track!!.name,
                            e.message ?: "",
                        ),
                    )
                }
            }
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private suspend fun downloadChapters(chapters: List<Chapter>) {
        // SY -->
        val state = successState ?: return
        if (state.source is MergedSource) {
            chapters.groupBy { it.mangaId }.forEach { map ->
                val manga = state.mergedData?.manga?.get(map.key) ?: return@forEach
                downloadManager.downloadChapters(manga, map.value)
            }
        } else {
            // SY <--
            val manga = state.manga
            downloadManager.downloadChapters(manga, chapters)
        }
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        viewModelScope.launch {
            // KNS --> (re-)bookmarking is ordinary until a color is chosen; shared with Updates
            ChapterBookmarks.toggle(chapters.map { it.bookmarkTarget() }, bookmarked)
                .let { updateChapter.awaitAll(it) }
            // KNS <--
        }
        toggleAllSelection(false)
    }

    // KNS -->
    /** Bookmarks [chapters] with [color] ([BookmarkColor.NONE] = an ordinary bookmark). */
    fun setBookmarkColor(chapters: List<Chapter>, color: BookmarkColor) {
        viewModelScope.launch {
            ChapterBookmarks.setColor(chapters.map { it.bookmarkTarget() }, color)
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Hides [chapters] from the chapter list, the reader, History, downloads and counts. Each entry
     * identifies exactly the selected source chapter (URL first, then its number, scanlator and name),
     * so an extra named like a chapter number never hides that numbered chapter.
     */
    fun blacklistChapters(chapters: List<Chapter>) {
        val manga = successState?.manga ?: return
        val keys = chapters.map(::hybridChapterBlacklistKey)
        if (keys.isEmpty()) return
        viewModelScope.launch {
            updateManga.await(
                MangaUpdate(manga.id) { blacklistedChapters = (manga.blacklistedChapters + keys).distinct() },
            )
        }
        toggleAllSelection(false)
    }

    /** Removes every blacklist entry that resolves to one of [chapters]. */
    fun unblacklistChapters(chapters: List<Chapter>) {
        val manga = successState?.manga ?: return
        val urls = chapters.mapTo(HashSet()) { chapterUrlBlacklistKey(it.url) }
        viewModelScope.launch {
            val all = getChaptersForBlacklist(manga)
            val remaining = manga.blacklistedChapters.filterNot { key ->
                resolveChapterBlacklistKey(key, all).any(urls::contains) ||
                    chapters.any { isChapterBlacklisted(it, listOf(key)) }
            }
            if (remaining != manga.blacklistedChapters) {
                updateManga.await(MangaUpdate(manga.id) { blacklistedChapters = remaining })
            }
        }
        toggleAllSelection(false)
    }

    fun setHideBlacklistedChapters(hide: Boolean) {
        libraryPreferences.hideBlacklistedChapters(mangaId).set(hide)
    }

    fun setBookmarkColorFilter(included: Set<BookmarkColor>, excluded: Set<BookmarkColor>) {
        libraryPreferences.bookmarkColorFilter(mangaId).set(included.mapTo(mutableSetOf()) { it.name })
        libraryPreferences.bookmarkColorFilterExclude(mangaId).set(excluded.mapTo(mutableSetOf()) { it.name })
        if ((included.isNotEmpty() || excluded.isNotEmpty()) && successState?.manga?.bookmarkedFilter == TriState.DISABLED) {
            setBookmarkedFilter(TriState.ENABLED_IS)
        }
    }

    /**
     * The scanlator preferences dialog's data (opened from the chapter filters' Scanlator entry), whose
     * second mode is the chapter blacklist: each entry with the chapters it currently hides (resolved
     * against all chapters, hidden ones included). Null before the manga has loaded.
     */
    suspend fun loadScanlatorPreferences(): ScanlatorPreferencesData? {
        val state = successState ?: return null
        // The stored values, read once earlier edits have landed: the screen state can still hold the
        // values from before them, and a dialog built on those would show (and then store) old ones.
        scanlatorPreferenceWriter.awaitIdle()
        val manga = getManga.await(mangaId) ?: state.manga
        val all = getChaptersForBlacklist(manga)
        return ScanlatorPreferencesData(
            // The scanlators of all chapters, so ones hidden by a filter can still be ordered
            scanlators = (state.availableScanlators + all.mapNotNull { it.scanlator?.ifBlank { null } }).toList(),
            priority = manga.scanlatorPriority,
            excludedScanlators = getExcludedScanlators.await(mangaId),
            smartMergeOverride = manga.smartMergeOverride,
            globalSmartMerge = libraryPreferences.smartScanlatorMerge.get(),
            rangeRules = manga.scanlatorRangeRules.mapNotNull(::parseScanlatorRangeRule),
            blacklist = manga.blacklistedChapters.map { key ->
                BlacklistEntry(key = key, chapters = resolveChapterBlacklistMatches(key, all).map { it.name })
            },
            scanlatorStats = scanlatorStatsOf(all),
        )
    }

    /**
     * Stores the scanlator preferences dialog's edits as they happen, one at a time and in order, so a
     * quick series of edits cannot store an older value last. Each write only touches its own field;
     * blacklist removals start from the stored blacklist, so consecutive removals all stick.
     */
    fun applyScanlatorPreferenceChanges(changes: List<ScanlatorPreferencesDraft.Change>) {
        // An app-wide queue, so closing the dialog or leaving the entry right away cannot cancel the write
        changes.forEach { scanlatorPreferenceWriter.submit(mangaId, it) }
    }

    /** All chapters of the manga (hidden and excluded ones too), to resolve blacklist entries. */
    private suspend fun getChaptersForBlacklist(manga: Manga): List<Chapter> {
        return if (manga.source == MERGED_SOURCE_ID) {
            getMergedChaptersByMangaId.await(manga.id, applyFilter = false)
        } else {
            getChaptersByMangaId.await(manga.id, applyFilter = false)
        }
    }
    // KNS <--

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<Chapter>) {
        val state = successState ?: return
        // Outlives the screen, which the delete dialog usually closes along with
        appScope.launch {
            try {
                successState?.let { state ->
                    // KMK -->
                    if (state.source.id == MERGED_SOURCE_ID) {
                        chapters.groupBy { it.mangaId }.forEach { map ->
                            val manga = state.mergedData?.manga?.get(map.key) ?: return@forEach
                            val source = state.mergedData.sources.find { it.id != MERGED_SOURCE_ID && manga.source == it.id } ?: return@forEach
                            downloadManager.deleteChapters(
                                map.value,
                                manga,
                                source,
                                ignoreCategoryExclusion = true,
                            )
                            if (source.isLocal()) {
                                // Refresh chapters state for Local source
                                fetchAllFromSource(
                                    manga = state.manga,
                                    manualFetch = false,
                                    fetchDetails = false,
                                    fetchChapters = true,
                                )
                            }
                        }
                    } else {
                        // KMK <--
                        downloadManager.deleteChapters(
                            chapters,
                            state.manga,
                            state.source,
                            // KMK -->
                            ignoreCategoryExclusion = true,
                            // KMK <--
                        )
                        // KMK -->
                        if (state.source.isLocal()) {
                            // Refresh chapters state for Local source
                            fetchAllFromSource(
                                manga = state.manga,
                                manualFetch = false,
                                fetchDetails = false,
                                fetchChapters = true,
                            )
                        }
                        // KMK <--
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    // KMK -->
    fun clearManga(
        deleteDownload: Boolean,
        removeChapters: Boolean,
    ) {
        if (deleteDownload) {
            deleteDownloadedData()
        }
        if (removeChapters) {
            removeChaptersDatabase()
        }
    }

    private fun deleteDownloadedData() {
        viewModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    if (state.source.id == MERGED_SOURCE_ID) {
                        state.mergedData?.manga
                            ?.forEach { (_, manga) ->
                                val source = state.mergedData.sources.find { it.id != MERGED_SOURCE_ID && manga.source == it.id } ?: return@forEach

                                downloadManager.deleteManga(
                                    manga = manga,
                                    source = source,
                                    removeQueued = true,
                                )
                                if (source.isLocal()) {
                                    // Refresh chapters state for Local source
                                    fetchAllFromSource(
                                        manga = state.manga,
                                        manualFetch = false,
                                        fetchDetails = false,
                                        fetchChapters = true,
                                    )
                                }
                            }
                    } else {
                        downloadManager.deleteManga(
                            manga = state.manga,
                            source = state.source,
                            removeQueued = true,
                        )
                        if (state.source.isLocal()) {
                            // Refresh chapters state for Local source
                            fetchAllFromSource(
                                manga = state.manga,
                                manualFetch = false,
                                fetchDetails = false,
                                fetchChapters = true,
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun removeChaptersDatabase() {
        viewModelScope.launchNonCancellable {
            try {
                successState?.chapters?.map { it.id }
                    ?.let { deleteChaptersFromDb.await(it) }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }
    // KMK <--

    private fun downloadNewChapters(chapters: List<Chapter>) {
        appScope.launch {
            val manga = successState?.manga ?: return@launch
            val chaptersToDownload = filterChaptersForDownload.await(manga, chapters)

            if (chaptersToDownload.isNotEmpty() /* SY --> */ && !manga.isEhBasedManga() /* SY <-- */) {
                downloadChapters(chaptersToDownload)
            }
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        appScope.launch {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        appScope.launch {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        appScope.launch {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        appScope.launch {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        appScope.launch {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        viewModelScope.launch {
            appScope.launch {
                libraryPreferences.setChapterSettingsDefault(manga)
                if (applyToExisting) {
                    setMangaDefaultChapterFlags.awaitAll()
                }
            }.join()
            snackbarHostState.showSnackbar(message = context.stringResource(MR.strings.chapter_settings_updated))
        }
    }

    fun resetToDefaultSettings() {
        val manga = successState?.manga ?: return
        viewModelScope.launch {
            setMangaDefaultChapterFlags.await(manga)
        }
    }

    fun toggleSelection(
        item: ChapterList.Item,
        selected: Boolean,
        fromLongPress: Boolean = false,
    ) {
        val chapters = successState?.processedChapters ?: return
        val selectedIndex = chapters.indexOfFirst { it.id == item.chapter.id }
        if (selectedIndex < 0) return

        // Read from the ids, as the state catches up with a selection only after it's been made
        val selectedIds = HashSet(selectedChapterIds.value)
        if ((item.id in selectedIds) == selected) return

        val firstSelection = chapters.none { it.id in selectedIds }
        selectedIds.addOrRemove(item.id, selected)

        if (selected && fromLongPress) {
            if (firstSelection) {
                selectedPositions[0] = selectedIndex
                selectedPositions[1] = selectedIndex
            } else {
                // Try to select the items in-between when possible
                val range: IntRange
                if (selectedIndex < selectedPositions[0]) {
                    range = selectedIndex + 1..<selectedPositions[0]
                    selectedPositions[0] = selectedIndex
                } else if (selectedIndex > selectedPositions[1]) {
                    range = (selectedPositions[1] + 1)..<selectedIndex
                    selectedPositions[1] = selectedIndex
                } else {
                    // Just select itself
                    range = IntRange.EMPTY
                }

                range.forEach { selectedIds.add(chapters[it].id) }
            }
        } else if (!fromLongPress) {
            if (!selected) {
                if (selectedIndex == selectedPositions[0]) {
                    selectedPositions[0] = chapters.indexOfFirst { it.id in selectedIds }
                } else if (selectedIndex == selectedPositions[1]) {
                    selectedPositions[1] = chapters.indexOfLast { it.id in selectedIds }
                }
            } else {
                if (selectedIndex < selectedPositions[0]) {
                    selectedPositions[0] = selectedIndex
                } else if (selectedIndex > selectedPositions[1]) {
                    selectedPositions[1] = selectedIndex
                }
            }
        }
        selectedChapterIds.value = selectedIds
    }

    fun toggleAllSelection(selected: Boolean) {
        selectedChapterIds.value = if (selected) allChapters.orEmpty().mapTo(HashSet()) { it.id } else emptySet()
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        val selectedIds = selectedChapterIds.value
        selectedChapterIds.value = allChapters.orEmpty().filterNot { it.id in selectedIds }.mapTo(HashSet()) { it.id }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    // Chapters list - end

    // Track sheet - start

    // SY -->
    private suspend fun createMdListTrack(manga: Manga, mergedData: MergedMangaData?): Track? {
        try {
            val mdManga = manga.takeIf { it.source in mangaDexSourceIds }
                ?: mergedData?.manga?.values?.find { it.source in mangaDexSourceIds }
                ?: throw IllegalArgumentException("Entry does not belong to MangaDex")
            val track = trackerManager.mdList.createInitialTracker(manga, mdManga)
                .toDomainTrack(false)
                ?: throw IllegalStateException("Could not create initial track")
            upsertTrack.await(track)
            /* KMK -->
            return TrackItem(
                getTracks.await(mangaId).first { it.trackerId == trackerManager.mdList.id },
                 trackerManager.mdList,
             )
            KMK <-- */
            return getTracks.await(mangaId).first { it.trackerId == trackerManager.mdList.id }
        } catch (e: Exception) {
            xLogE("Failed to create MangaDex track", e)
            return null
        }
    }
    // SY <--

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
        data class SetFetchInterval(val manga: Manga) : Dialog
        data object RemoveFromLibrary : Dialog

        // SY -->
        data class EditMangaInfo(val manga: Manga) : Dialog
        data class EditMergedSettings(val mergedData: MergedMangaData) : Dialog
        // SY <--

        // KMK -->
        data object ClearManga : Dialog
        // KMK <--

        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog

        // KNS -->
        // KNS <--
    }

    // KNS -->
    /** A blacklist entry and the current chapters it resolves to (none: the chapter is unavailable). */
    data class BlacklistEntry(val key: String, val chapters: List<String>)

    /** What the scanlator preferences dialog opens with. */
    data class ScanlatorPreferencesData(
        val scanlators: List<String>,
        val priority: List<String>,
        val excludedScanlators: Set<String>,
        val smartMergeOverride: Boolean?,
        val globalSmartMerge: Boolean,
        val rangeRules: List<ScanlatorRangeRule>,
        val blacklist: List<BlacklistEntry>,
        /** Every chapter per scanlator (hidden ones too), for its count and covered numbers. */
        val scanlatorStats: Map<String, ScanlatorStats> = emptyMap(),
    )
    // KNS <--

    fun dismissDialog() {
        dialog.value = null
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        dialog.value = Dialog.DeleteChapters(chapters)
    }

    fun showSettingsDialog() {
        dialog.value = Dialog.SettingsSheet
    }

    fun showTrackDialog() {
        dialog.value = Dialog.TrackSheet
    }

    fun showCoverDialog() {
        dialog.value = Dialog.FullCover
    }

    fun showMigrateDialog(duplicate: Manga) {
        val manga = successState?.manga ?: return
        dialog.value = Dialog.Migrate(target = manga, current = duplicate)
    }

    // SY -->
    fun showEditMangaInfoDialog() {
        val manga = successState?.manga ?: return
        dialog.value = Dialog.EditMangaInfo(manga)
    }

    fun showEditMergedSettingsDialog() {
        val mergedData = successState?.mergedData ?: return
        dialog.value = Dialog.EditMergedSettings(mergedData)
    }
    // SY <--

    // KMK -->
    fun showClearMangaDialog() {
        dialog.value = Dialog.ClearManga
    }
    // KMK <--

    private data class MangaAndChapters(
        val manga: Manga,
        val source: Source,
        val chapters: List<ChapterList.Item>,
        // SY -->
        val flatMetadata: FlatMetadata?,
        val mergedData: MergedMangaData?,
        // SY <--
        // KNS -->
        val blacklistedCount: Int,
        val hideBlacklistedChapters: Boolean,
        val bookmarkColorFilter: Set<BookmarkColor>,
        val bookmarkColorFilterExclude: Set<BookmarkColor>,
        // KNS <--
    )

    // KMK/SY -->
    private data class UiExtras(
        val pagePreviewsState: PagePreviewState,
        val relatedMangaCollection: List<RelatedManga>?,
        val isRelatedMangasFetched: Boolean?,
        val seedColor: Color?,
        // KNS -->
        val sequelPrequel: List<SequelPrequelEntry>,
        // KNS <--
    )

    private data class DialogAndExtras(
        val dialog: Dialog?,
        val isRefreshingData: Boolean,
        val extras: UiExtras,
    )
    // KMK/SY <--

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val manga: Manga,
            val source: Source,
            val isFromSource: Boolean,
            val chapters: List<ChapterList.Item>,
            val availableScanlators: Set<String>,
            val excludedScanlators: Set<String>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hideMissingChapters: Boolean = false,

            // SY -->
            val meta: RaisedSearchMetadata?,
            val mergedData: MergedMangaData?,
            val showRecommendationsInOverflow: Boolean,
            val showMergeInOverflow: Boolean,
            val showMergeWithAnother: Boolean,
            val pagePreviewsState: PagePreviewState,
            val alwaysShowReadingProgress: Boolean,
            val previewsRowCount: Int,
            // SY <--
            // KMK -->
            /**
             * status of fetching related mangas
             * - null: not started
             * - false: started & fetching
             * - true: finished
             */
            val isRelatedMangasFetched: Boolean? = null,
            /**
             * a list of <keyword, related mangas>
             */
            val relatedMangaCollection: List<RelatedManga>? = null,
            val seedColor: Color? = manga.asMangaCover().vibrantCoverColor?.let { Color(it) },
            // KMK <--
            // KNS -->
            /** Blacklisted chapters of this manga that resolve to a current chapter. */
            val blacklistedChapterCount: Int = 0,
            val hideBlacklistedChapters: Boolean = true,

            /** Only bookmarks of these colors are listed; empty = no color filter. */
            val bookmarkColorFilter: Set<BookmarkColor> = emptySet(),
            val bookmarkColorFilterExclude: Set<BookmarkColor> = emptySet(),
            val sequelPrequel: List<SequelPrequelEntry> = emptyList(),
            // KNS <--
        ) : State {
            // KMK -->
            /**
             * a value of null will be treated as still loading, so if all searching were failed and won't update
             * 'relatedMangaCollection` then we should return empty list
             */
            val relatedMangasSorted = relatedMangaCollection
                ?.sorted(manga)
                ?.removeDuplicates(manga)
                ?.filter { it.isVisible() }
                ?.isLoading(isRelatedMangasFetched)
                ?: if (isRelatedMangasFetched == true) emptyList() else null
            // KMK <--

            val processedChapters by lazy {
                chapters.applyFilters(manga).toList()
                    // KMK -->
                    // safe-guard some edge-cases where chapters are duplicated some how on a merged entry
                    .distinctBy { it.id }
                // KMK <--
            }

            val isAnySelected by lazy {
                chapters.fastAny { it.selected }
            }

            val selectedChapters by lazy {
                processedChapters.fastFilter { it.selected }
            }

            val hasUnreadChapters by lazy {
                processedChapters.fastAny { !it.chapter.read }
            }

            val hasReadChapters by lazy {
                chapters.fastAny { it.chapter.read }
            }

            val missingChapterCount by lazy {
                if (hideMissingChapters) return@lazy 0
                processedChapters.fastMap { it.chapter.chapterNumber }.missingChaptersCount()
            }

            val chapterListItems by lazy {
                if (hideMissingChapters) {
                    return@lazy processedChapters
                }

                processedChapters.insertSeparators { before, after ->
                    val (lowerChapter, higherChapter) = if (manga.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherChapter == null) return@insertSeparators null

                    if (lowerChapter == null) {
                        floor(higherChapter.chapter.chapterNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateChapterGap(higherChapter.chapter, lowerChapter.chapter)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            ChapterList.MissingCount(
                                id = "${lowerChapter?.id}-${higherChapter.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val filterActive: Boolean
                get() = scanlatorFilterActive || manga.chaptersFiltered() ||
                    // KNS -->
                    bookmarkColorFilter.isNotEmpty() || bookmarkColorFilterExclude.isNotEmpty()
            // KNS <--

            /**
             * Applies the view filters to the list of chapters obtained from the database.
             * @return an observable of the list of chapters filtered and sorted.
             */
            private fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
                val isLocalManga = manga.isLocal()
                val unreadFilter = manga.unreadFilter
                val downloadedFilter = manga.downloadedFilter
                val bookmarkedFilter = manga.bookmarkedFilter
                val chapterSort = getChapterSort(manga)
                return asSequence()
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    // KNS -->
                    .filter { (chapter) ->
                        bookmarkedFilter != TriState.ENABLED_IS ||
                            (bookmarkColorFilter.isEmpty() && bookmarkColorFilterExclude.isEmpty()) ||
                            (
                                chapter.bookmark && BookmarkColor.matchesFilter(
                                    listOf(chapter.bookmarkColor),
                                    bookmarkColorFilter,
                                    bookmarkColorFilterExclude,
                                )
                                )
                    }
                    // KNS <--
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
                    .sortedWith { (chapter1), (chapter2) -> chapterSort(chapter1, chapter2) }
            }
        }
    }
}

// SY -->
data class MergedMangaData(
    val references: List<MergedMangaReference>,
    val manga: Map<Long, Manga>,
    val sources: List<Source>,
)
// SY <--

@Immutable
sealed class ChapterList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : ChapterList()

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: Download.State,
        val selected: Boolean = false,
        // SY -->
        val sourceName: String?,
        val showScanlator: Boolean,
        // SY <--
        // KNS -->
        /** A blacklisted chapter that is shown because the manga's blacklist is revealed. */
        val blacklisted: Boolean = false,
        // KNS <--
    ) : ChapterList() {
        val id = chapter.id
        val isDownloaded = downloadState == Download.State.DOWNLOADED
    }
}

// SY -->
sealed interface PagePreviewState {
    data object Unused : PagePreviewState
    data object Loading : PagePreviewState
    data class Success(val pagePreviews: List<PagePreview>) : PagePreviewState
    data class Error(val error: Throwable) : PagePreviewState
}
// SY <--

// KMK -->
sealed interface RelatedManga {
    data object Loading : RelatedManga

    data class Success(
        val keyword: String,
        val mangaList: List<Manga>,
    ) : RelatedManga {
        val isEmpty: Boolean
            get() = mangaList.isEmpty()

        companion object {
            suspend fun fromPair(
                pair: Pair<String, List<SManga>>,
                toManga: suspend (mangaList: List<SManga>) -> List<Manga>,
            ) = Success(pair.first, toManga(pair.second))
        }
    }

    fun isVisible(): Boolean {
        return this is Loading || (this is Success && !this.isEmpty)
    }

    companion object {
        internal fun List<RelatedManga>.sorted(manga: Manga): List<RelatedManga> {
            val success = filterIsInstance<Success>()
            val loading = filterIsInstance<Loading>()
            val title = manga.title.lowercase()
            val ogTitle = manga.ogTitle.lowercase()
            return success.filter { it.keyword.isEmpty() } +
                success.filter { it.keyword.lowercase() == title } +
                success.filter { it.keyword.lowercase() == ogTitle && ogTitle != title } +
                success.filter { it.keyword.isNotEmpty() && it.keyword.lowercase() !in listOf(title, ogTitle) }
                    .sortedByDescending { it.keyword.length }
                    .sortedBy { it.mangaList.size } +
                loading
        }

        internal fun List<RelatedManga>.removeDuplicates(manga: Manga): List<RelatedManga> {
            val mangaIds = HashSet<Long>().apply { add(manga.id) }

            return map { relatedManga ->
                if (relatedManga is Success) {
                    Success(
                        relatedManga.keyword,
                        relatedManga.mangaList
                            .filter { mangaIds.add(it.id) },
                    )
                } else {
                    relatedManga
                }
            }
        }

        internal fun List<RelatedManga>.isLoading(isRelatedMangaFetched: Boolean?): List<RelatedManga> {
            return if (isRelatedMangaFetched == false) this + listOf(Loading) else this
        }
    }
}
// KMK <--

// KNS -->
/**
 * What opening an entry fetches, from the previous personal fork: details when the entry is not
 * initialized, chapters when it has none; with "Automatically refresh opened manga" an initialized entry
 * fetches both. Chapter flags reset for entries outside the library and, only with the separate reset
 * setting, for an auto-refreshed entry.
 */
internal data class OpenRefresh(
    val fetchDetails: Boolean,
    val fetchChapters: Boolean,
    val resetChapterFlags: Boolean,
) {
    companion object {
        fun of(manga: Manga, hasChapters: Boolean, autoRefresh: Boolean, autoResetFlags: Boolean): OpenRefresh {
            val autoRefreshing = autoRefresh && manga.initialized
            return OpenRefresh(
                fetchDetails = !manga.initialized || autoRefreshing,
                fetchChapters = !hasChapters || autoRefreshing,
                resetChapterFlags = !manga.favorite || (autoRefreshing && autoResetFlags),
            )
        }
    }
}
// KNS <--
