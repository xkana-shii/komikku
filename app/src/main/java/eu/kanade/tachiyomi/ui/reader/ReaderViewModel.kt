package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.compose.runtime.Immutable
import androidx.core.graphics.createBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import ca.mpreg.imagedecoder.ImageDecoder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.connections.service.WebhookEvent
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.interactor.StartReread
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.webhook.WebhookNotifier
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.util.chapter.filterDownloaded
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil.MAX_FILE_NAME_BYTES
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.toast
import exh.metadata.metadata.RaisedSearchMetadata
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isEhBasedManga
import exh.util.defaultReaderType
import exh.util.mangaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.storage.UniFileTempFileManager
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.model.resolveVisibleChapter
import tachiyomi.domain.chapter.service.ChapterBookmarks
import tachiyomi.domain.chapter.service.ChapterVisibility
import tachiyomi.domain.chapter.service.bookmarkTarget
import tachiyomi.domain.chapter.service.filterBlacklistedChapters
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.isLocal
import kotlin.getValue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Presenter used by the activity to perform background operations.
 */
@AssistedInject
class ReaderViewModel(
    @Assisted private val savedState: SavedStateHandle,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val context: Context,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val tempFileManager: UniFileTempFileManager,
    private val imageSaver: ImageSaver,
    val readerPreferences: ReaderPreferences,
    private val basePreferences: BasePreferences,
    private val downloadPreferences: DownloadPreferences,
    private val trackPreferences: TrackPreferences,
    private val trackChapter: TrackChapter,
    // KNS -->
    private val startReread: StartReread,
    private val webhookNotifier: WebhookNotifier,
    // KNS <--
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getNextChapters: GetNextChapters,
    private val upsertHistory: UpsertHistory,
    private val updateChapter: UpdateChapter,
    private val setMangaViewerFlags: SetMangaViewerFlags,
    private val getIncognitoState: GetIncognitoState,
    private val libraryPreferences: LibraryPreferences,
    // SY -->
    private val syncPreferences: SyncPreferences,
    private val uiPreferences: UiPreferences,
    private val sourcePreferences: SourcePreferences,
    private val getFlatMetadataById: GetFlatMetadataById,
    private val getMergedMangaById: GetMergedMangaById,
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
    private val coverManager: LocalCoverManager,
    private val updateManga: UpdateManga,
    private val coverCache: CoverCache,
    private val chapterCache: ChapterCache,
    private val downloadCache: DownloadCache,
    // KNS -->
    private val chapterVisibility: ChapterVisibility,
    // KNS <--
) : ViewModel() {

    @AssistedFactory
    @ViewModelAssistedFactoryKey(ReaderViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): ReaderViewModel {
            return create(extras.createSavedStateHandle())
        }

        fun create(@Assisted savedState: SavedStateHandle): ReaderViewModel
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    /**
     * Ids of the manga and chapter the reader was launched with, taken from the activity intent.
     */
    val mangaId = savedState.get<Long>("manga") ?: -1L
    private val initialChapterId = savedState.get<Long>("chapter") ?: -1L
    // SY -->
    private val initialPage = savedState.get<Int>("page")?.takeUnless { it == -1 }
    // SY <--

    val hasValidArgs = mangaId != -1L && initialChapterId != -1L

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    val manga: Manga?
        get() = state.value.manga

    /**
     * The source of the manga loaded in the reader. Null until it has been resolved.
     */
    val source: Source?
        get() = state.value.source

    val currentChapter: Chapter?
        get() = state.value.currentChapter?.chapter

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    /**
     * The visible page index of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterPageIndex = savedState.get<Int>("page_index") ?: -1
        set(value) {
            savedState["page_index"] = value
            field = value
        }

    // KMK -->
    fun handleDownloadAction(chapter: Chapter, action: ChapterDownloadAction) {
        when (action) {
            ChapterDownloadAction.START -> downloadChapter(chapter)
            ChapterDownloadAction.START_NOW -> viewModelScope.launch { downloadManager.startDownloadNow(chapter.id) }
            ChapterDownloadAction.CANCEL -> cancelDownload(chapter.id)
            ChapterDownloadAction.DELETE -> deleteChapter(chapter)
        }
    }

    /**
     * @param chapter the chapter to download.
     */
    private fun downloadChapter(chapter: Chapter) {
        viewModelScope.launch {
            val manga = manga?.let {
                if (it.source == MERGED_SOURCE_ID) {
                    state.value.mergedManga?.get(chapter.mangaId) ?: return@launch
                } else {
                    it
                }
            } ?: return@launch
            downloadManager.downloadChapters(manga, listOf(chapter))
            downloadManager.startDownloads()
        }
    }

    private fun cancelDownload(chapterId: Long) {
        viewModelScope.launch {
            val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return@launch
            downloadManager.cancelQueuedDownloads(listOf(activeDownload))
            // TODO: updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
        }
    }

    private fun deleteChapter(chapter: Chapter) {
        viewModelScope.launchNonCancellable {
            try {
                val manga = if (manga?.source == MERGED_SOURCE_ID) {
                    state.value.mergedManga?.get(chapter.mangaId) ?: return@launchNonCancellable
                } else {
                    manga ?: return@launchNonCancellable
                }
                val source = sourceManager.get(manga.source) ?: return@launchNonCancellable
                downloadManager.deleteChapters(
                    listOf(chapter),
                    manga,
                    source,
                    ignoreCategoryExclusion = true,
                )
//                // KMK -->
//                if (source.isLocal()) {
//                    // TODO: Refresh chapters state for Local source
//                    fetchChaptersFromSource()
//                }
//                // KMK <--
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }
    // KMK <--

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Instant? = null

    // Serializes the progress writes, which outlive the reader, so they are applied in order
    private val progressWrites = Mutex()

    // Set on Main, flushed from the chapter load too
    @Volatile
    private var pendingProgressChapter: ReaderChapter? = null
    private var pendingProgressSave: Job? = null

    private var chapterToDownload: Download? = null

    // KUN -->
    // Primed once by [loadChapterList] on the IO dispatcher. The completion path reads it from a
    // Dispatchers.Main.immediate coroutine, so it must never trigger a blocking query there.
    private lateinit var unfilteredChapterList: List<Chapter>
    // KUN <--

    /**
     * Chapter list for the active manga. Loaded by [init].
     */
    private lateinit var chapterList: List<ReaderChapter>

    private suspend fun loadChapterList(): List<ReaderChapter> {
        val manga = manga!!
        // SY -->
        val (filteredChapters, mangaMap) = runBlocking {
            if (manga.source == MERGED_SOURCE_ID) {
                // KMK -->
                // Prime the unfiltered (undeduped) list here, off the main thread; it also backs the
                // requested-chapter lookup below.
                unfilteredChapterList = getMergedChaptersByMangaId.await(
                    manga.id,
                    dedupe = false,
                    applyFilter = false,
                )
                // KMK <--
                getMergedChaptersByMangaId.await(manga.id, applyFilter = true) to
                    state.value.mergedManga
            } else {
                // KMK -->
                unfilteredChapterList = getChaptersByMangaId.await(manga.id, applyFilter = false)
                // KMK <--
                getChaptersByMangaId.await(manga.id, applyFilter = true) to null
            }
        }
        fun isChapterDownloaded(chapter: Chapter): Boolean {
            val chapterManga = mangaMap?.get(chapter.mangaId) ?: manga
            return downloadManager.isChapterDownloaded(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                chapterUrl = chapter.url,
                mangaTitle = chapterManga.ogTitle,
                sourceId = chapterManga.source,
            )
        }
        // SY <--

        // KNS -->
        // Navigation follows the app-wide visibility rule (chapter blacklist, Smart Scanlator Merge).
        // A requested Smart Merge duplicate opens as its visible release; a requested blacklisted
        // chapter still opens (it was asked for explicitly) and is re-added below like an excluded one.
        val chapters = chapterVisibility.visibleChapters(manga, filteredChapters, unfilteredChapterList)
        if (chapters.none { it.id == chapterId }) {
            val requested = unfilteredChapterList.firstOrNull { it.id == chapterId }
            val blacklisted = requested != null &&
                filterBlacklistedChapters(listOf(requested), manga.blacklistedChapters, unfilteredChapterList).isEmpty()
            if (requested != null && !blacklisted) {
                unfilteredChapterList
                    .resolveVisibleChapter(manga, chapterVisibility.smartMergeEnabled(manga), chapterId, chapters)
                    ?.let { chapterId = it.id }
            }
        }
        // KNS <--

        // KUN -->
        // A chapter requested directly (deep link, notification, continue reading) whose scanlator
        // is excluded by the manga's filter is absent from the filtered list, so the lookup below
        // would throw and the reader would never open. Re-add the requested chapter from the
        // unfiltered list. Adapted from Mihon 2b4b8472bb, kept merged-source aware.
        val chaptersWithRequested = if (chapters.any { it.id == chapterId }) {
            chapters
        } else {
            chapters + unfilteredChapterList.filter { it.id == chapterId }
        }
        // KUN <--

        val selectedChapter = chaptersWithRequested.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader = when {
            (readerPreferences.skipRead.get() || readerPreferences.skipFiltered.get()) -> {
                val filteredChapters = chaptersWithRequested.filterNot {
                    when {
                        readerPreferences.skipRead.get() && it.read -> true
                        readerPreferences.skipFiltered.get() -> {
                            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !it.read) ||
                                (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && it.read) ||
                                // SY -->
                                (
                                    manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_DOWNLOADED &&
                                        !isChapterDownloaded(it)
                                    ) ||
                                (
                                    manga.downloadedFilterRaw == Manga.CHAPTER_SHOW_NOT_DOWNLOADED &&
                                        isChapterDownloaded(it)
                                    ) ||
                                // SY <--
                                (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !it.bookmark) ||
                                (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && it.bookmark)
                        }
                        else -> false
                    }
                }

                if (filteredChapters.any { it.id == chapterId }) {
                    filteredChapters
                } else {
                    filteredChapters + listOf(selectedChapter)
                }
            }
            else -> chaptersWithRequested
        }

        return chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .run {
                if (readerPreferences.skipDupe.get()) {
                    removeDuplicates(selectedChapter)
                } else {
                    this
                }
            }
            .run {
                if (basePreferences.downloadedOnly.get()) {
                    filterDownloaded(manga, downloadCache, mangaMap)
                } else {
                    this
                }
            }
            .map(::ReaderChapter)
    }

    var incognitoMode: Boolean = false
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileReading.get()

    init {
        // To save state
        state.map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            // SY -->
            .drop(1) // allow the loader to set the first page and chapter id
            // SY <--
            .onEach { currentChapter ->
                if (chapterPageIndex >= 0) {
                    // Restore from SavedState
                    currentChapter.requestedPage = chapterPageIndex
                } else if (!currentChapter.chapter.read) {
                    currentChapter.requestedPage = currentChapter.chapter.lastPageRead
                }
                chapterId = currentChapter.chapter.id
            }
            .launchIn(viewModelScope)

        // SY -->
        state.mapLatest { it.ehAutoscrollFreq }
            .distinctUntilChanged()
            .drop(1)
            .onEach { text ->
                val parsed = text.toDoubleOrNull()

                if (parsed == null || parsed <= 0 || parsed > 9999) {
                    readerPreferences.autoscrollInterval.set(-1f)
                    mutableState.update { it.copy(isAutoScrollEnabled = false) }
                } else {
                    readerPreferences.autoscrollInterval.set(parsed.toFloat())
                    mutableState.update { it.copy(isAutoScrollEnabled = true) }
                }
            }
            .launchIn(viewModelScope)
        // SY <--

        if (hasValidArgs) {
            viewModelScope.launch { init() }
        }
    }

    override fun onCleared() {
        val currentChapters = state.value.viewerChapters
        if (currentChapters != null) {
            currentChapters.unref()
            chapterToDownload?.let {
                // viewModelScope is already cancelled here
                appScope.launch { downloadManager.addDownloadsToStartOfQueue(listOf(it)) }
            }
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onActivityFinish() {
        deletePendingChapters()
    }

    /**
     * Initializes this presenter with the [mangaId] and [initialChapterId] the reader was launched
     * with. This method will fetch the manga from the database and initialize the initial chapter.
     * Failures are reported through [State.initError].
     */
    private suspend fun init() {
        withContext(Dispatchers.IO) {
            try {
                val manga = getManga.await(mangaId) ?: error("Requested manga of id $mangaId not found")
                // SY -->
                val source = sourceManager.getOrStub(manga.source)
                incognitoMode = getIncognitoState.await(manga.source)
                val metadataSource = source.getMainSource<MetadataSource<*, *>>()
                val metadata = if (metadataSource != null) {
                    getFlatMetadataById.await(mangaId)?.raise(metadataSource.metaClass)
                } else {
                    null
                }
                val mergedManga = if (source is MergedSource) {
                    runBlocking {
                        getMergedMangaById.await(manga.id)
                    }.associateBy { it.id }
                } else {
                    null
                }
                val relativeTime = uiPreferences.relativeTime.get()
                val autoScrollFreq = readerPreferences.autoscrollInterval.get()
                // SY <--
                mutableState.update {
                    it.copy(
                        manga = manga,
                        source = source,
                        // SY -->
                        meta = metadata,
                        mergedManga = mergedManga,
                        dateRelativeTime = relativeTime,
                        ehAutoscrollFreq = if (autoScrollFreq == -1f) {
                            ""
                        } else {
                            autoScrollFreq.toString()
                        },
                        isAutoScrollEnabled = autoScrollFreq != -1f,
                        // SY <--
                    )
                }
                if (chapterId == -1L) chapterId = initialChapterId

                loader = ChapterLoader(
                    context = context,
                    downloadManager = downloadManager,
                    downloadProvider = downloadProvider,
                    chapterCache = chapterCache,
                    appScope = appScope,
                    canLoadAhead = ::canLoadAhead,
                    manga = manga,
                    source = source,
                    // SY -->
                    sourceManager = sourceManager,
                    readerPrefs = readerPreferences,
                    sourcePreferences = sourcePreferences,
                    mergedManga = mergedManga,
                    // SY <--
                )

                chapterList = loadChapterList()
                loadChapter(
                    loader!!,
                    chapterList.first { chapterId == it.chapter.id },
                    // SY -->
                    initialPage,
                    // SY <--
                )
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                mutableState.update { it.copy(initError = e) }
            }
        }
    }

    // SY -->
    fun getChapters(): List<ReaderChapterItem> {
        // KMK -->
        val manga = manga ?: return emptyList()
        val mangaList = state.value.mergedManga?.takeIf { it.isNotEmpty() } ?: mapOf(manga.id to manga)
        // KMK <--

        val currentChapter = getCurrentChapter()

        return chapterList.map {
            ReaderChapterItem(
                chapter = it.chapter,
                // KMK -->
                manga = mangaList[it.chapter.mangaId] ?: manga,
                // KMK <--
                isCurrent = it.chapter.id == currentChapter?.chapter?.id,
            )
        }
    }
    // SY <--

    /**
     * Loads the given [chapter] with this [loader] and updates the currently active chapters.
     * Callers must handle errors.
     */
    private suspend fun loadChapter(
        loader: ChapterLoader,
        chapter: ReaderChapter,
        // SY -->
        page: Int? = null,
        // SY <--
    ): ViewerChapters {
        loader.loadChapter(chapter /* SY --> */, page/* SY <-- */)

        val chapterPos = chapterList.indexOf(chapter)
        val newChapters = ViewerChapters(
            chapter,
            chapterList.getOrNull(chapterPos - 1),
            chapterList.getOrNull(chapterPos + 1),
        )
        preloadNextChapterAfter(newChapters)

        withContext(Dispatchers.Main) {
            val toDownload = cancelQueuedDownloads(newChapters.currChapter)
            mutableState.update {
                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                it.viewerChapters?.unref()

                chapterToDownload = toDownload
                it.copy(
                    viewerChapters = newChapters,
                    bookmarked = newChapters.currChapter.chapter.bookmark,
                    // KNS -->
                    bookmarkColor = newChapters.currChapter.chapter.bookmarkColor,
                    // KNS <--
                )
            }
        }
        return newChapters
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        viewModelScope.launch(Dispatchers.IO) {
            logcat { "Loading ${chapter.chapter.url}" }

            updateHistory()
            restartReadTimer()

            try {
                loadChapter(loader, chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    fun loadNewChapterFromDialog(chapter: Chapter) {
        viewModelScope.launchIO {
            val newChapter = chapterList.firstOrNull { it.chapter.id == chapter.id } ?: return@launchIO
            loadAdjacent(newChapter)
        }
    }

    /**
     * Called when the user is going to load the prev/next chapter through the toolbar buttons.
     */
    private suspend fun loadAdjacent(chapter: ReaderChapter) {
        val loader = loader ?: return

        logcat { "Loading adjacent ${chapter.chapter.url}" }

        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            withContext(Dispatchers.IO) {
                loadChapter(loader, chapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            logcat(LogPriority.ERROR, e)
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    private var nextChapterPreloadJob: Job? = null

    /**
     * Preloads the next chapter of [chapters] once the current one has loaded, so it doesn't take bandwidth from the
     * pages being read. Skipped when loading ahead isn't allowed on this network.
     */
    private fun preloadNextChapterAfter(chapters: ViewerChapters) {
        val nextChapter = chapters.nextChapter ?: return
        nextChapterPreloadJob?.cancel()
        nextChapterPreloadJob = viewModelScope.launch(Dispatchers.IO) {
            if (!canLoadAhead()) return@launch
            val current = chapters.currChapter
            // Only online pages load in the background; other loaders load a page once it's shown
            if (current.pageLoader is HttpPageLoader) {
                current.pages.orEmpty().forEach { page ->
                    page.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
                }
            }
            preload(nextChapter)
        }
    }

    /**
     * Whether online chapters may load past the pages near the one being read.
     */
    private fun canLoadAhead(): Boolean =
        !readerPreferences.loadAheadOnlyOverWifi.get() || context.activeNetworkState().isWifi

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    suspend fun preload(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) {
            return
        }

        /*
         * This code is likely deprecated since once `chapter.pageLoader` is initialized with [HttpPageLoader],
         * it would set `chapter.state` to `Loading` or `Loaded` and return early already.
         */
        if (chapter.pageLoader?.isLocal == false) {
            val manga = state.value.mergedManga?.get(chapter.chapter.mangaId) ?: manga ?: return
            val dbChapter = chapter.chapter
            val source = sourceManager.getOrStub(manga.source)
            val isDownloaded = downloadManager.isChapterDownloadedOnDisk(
                dbChapter.name,
                dbChapter.scanlator,
                dbChapter.url,
                // SY -->
                manga.ogTitle,
                // SY <--
                source,
            )
            if (isDownloaded) {
                chapter.state = ReaderChapter.State.Wait
            }
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        val loader = loader ?: return
        try {
            logcat { "Preloading ${chapter.chapter.url}" }
            loader.loadChapter(chapter)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            return
        }
        eventChannel.trySend(Event.ReloadViewerChapters)
    }

    fun onViewerLoaded(viewer: Viewer?) {
        mutableState.update {
            it.copy(viewer = viewer)
        }
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage, currentPageText: String /* SY --> */, hasExtraPage: Boolean /* SY <-- */) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) {
            return
        }

        // SY -->
        mutableState.update { it.copy(currentPageText = currentPageText) }
        // SY <--

        val selectedChapter = page.chapter
        val pages = selectedChapter.pages ?: return

        // Save last page read and mark as read if needed
        updateChapterProgress(selectedChapter, page/* SY --> */, hasExtraPage/* SY <-- */)

        if (selectedChapter != getCurrentChapter()) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            loadNewChapter(selectedChapter)
        }

        val inDownloadRange = page.number.toDouble() / pages.size > 0.25
        if (inDownloadRange) {
            downloadNextChapters()
        }

        eventChannel.trySend(Event.PageChanged)
    }

    private fun downloadNextChapters() {
        if (downloadAheadAmount == 0) return
        val manga = manga ?: return

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return
        val nextChapter = state.value.viewerChapters?.nextChapter?.chapter ?: return

        // KMK -->
        val mangas = state.value.mergedManga ?: mapOf(manga.id to manga)
        val nextChapterManga = mangas[nextChapter.mangaId] ?: return
        // KMK <--

        viewModelScope.launchIO {
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                nextChapter.url,
                // KMK -->
                nextChapterManga.ogTitle,
                nextChapterManga.source,
                // KMK <--
            )
            if (!isNextChapterDownloaded) return@launchIO

            val chaptersToDownload = getNextChapters.await(manga.id, nextChapter.id).run {
                if (readerPreferences.skipDupe.get()) {
                    removeDuplicates(nextChapter)
                } else {
                    this
                }
            }.take(downloadAheadAmount)

            // KMK -->
            chaptersToDownload.groupBy { it.mangaId }.forEach { (mangaId, chapters) ->
                val chapterManga = mangas[mangaId] ?: return@forEach
                downloadManager.downloadChapters(
                    chapterManga,
                    chapters,
                )
            }
            // KMK <--
        }
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private suspend fun cancelQueuedDownloads(currentChapter: ReaderChapter): Download? {
        return downloadManager.getQueuedDownloadOrNull(currentChapter.chapter.id)?.also {
            downloadManager.cancelQueuedDownloads(listOf(it))
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete.
     *
     * This deletes chapters from reading list (filtered, unduplicated if any set).
     *
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private suspend fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots.get()
        if (removeAfterReadSlots == -1) return

        // Determine which chapter should be deleted and enqueue
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        // If chapter is completely read, no need to download it
        chapterToDownload = null

        if (chapterToDelete == null || !chapterToDelete.chapter.read) return

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        val duplicateChapters = if (markDuplicateAsRead) {
            unfilteredChapterList
                .filter {
                    it.id != chapterToDelete.chapter.id &&
                        it.isRecognizedNumber &&
                        it.chapterNumber == chapterToDelete.chapter.chapterNumber
                }
                // The list is loaded once, before these were marked read along with chapterToDelete
                .map { it.copy(read = true) }
        } else {
            emptyList()
        }
        enqueueDeleteChapters(listOf(chapterToDelete.chapter) + duplicateChapters)
    }

    // KMK -->
    /**
     * Deletes duplicate chapters when `removeAfterReadSlots` = "Last read chapter" (0).
     *
     * Ignore the case where `removeAfterReadSlots` > 0 while `skipDupe` = true as we don't know
     * where the chapters to be deleted are in the filtered [chapterList].
     *
     * For the case where `skipDupe` = false, chapters at should be deleted normally by [deleteChapterIfNeeded]
     * based on the `removeAfterReadSlots` offset while the user is reading sequentially.
     */
    private fun deleteDupChapterIfNeeded(chapterToDelete: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots.get()
        if (removeAfterReadSlots != 0) return
        enqueueDeleteReadChapters(chapterToDelete)
    }
    // KMK <--

    /**
     * Saves the chapter progress (last read page and whether it's read)
     * if incognito mode isn't on.
     *
     * Completing a chapter is saved right away. Other page changes are saved once the reader settles on a page, or
     * when [saveChapterProgress] is called, so flipping through pages doesn't write to the database on every page.
     */
    private fun updateChapterProgress(
        readerChapter: ReaderChapter,
        page: Page,
        // SY -->
        hasExtraPage: Boolean,
        // SY <--
    ) {
        val pageIndex = page.index
        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        val isSyncEnabled = syncPreferences.isSyncEnabled()

        mutableState.update {
            it.copy(currentPage = pageIndex + 1)
        }
        readerChapter.requestedPage = pageIndex
        chapterPageIndex = pageIndex

        if (incognitoMode || page.status is Page.State.Error) return

        // KNS -->
        notifyChapterStarted(readerChapter)
        // KNS <--

        val read = readerChapter.chapter.read || readerChapter.pages?.lastIndex == pageIndex
        // A page change in another chapter would replace that chapter's pending save
        if (pendingProgressChapter != readerChapter) saveChapterProgress()
        readerChapter.update { it.copy(lastPageRead = pageIndex, read = read) }
        pendingProgressSave?.cancel()

        // SY -->
        // Check if syncing is enabled for chapter open:
        if (isSyncEnabled && syncTriggerOpt.syncOnChapterOpen && readerChapter.chapter.lastPageRead == 0) {
            viewModelScope.launch { SyncDataJob.startNow(context) }
        }

        val isLastPage = readerChapter.pages?.lastIndex == pageIndex ||
            (hasExtraPage && readerChapter.pages?.lastIndex?.minus(1) == page.index)
        // SY <--
        if (isLastPage) {
            pendingProgressChapter = null
            // SY -->
            // Check if syncing is enabled for chapter read:
            if (isSyncEnabled && syncTriggerOpt.syncOnChapterRead) {
                viewModelScope.launch { SyncDataJob.startNow(context) }
            }
            // SY <--
            writeChapterProgress(readerChapter, onComplete = true)
        } else {
            pendingProgressChapter = readerChapter
            pendingProgressSave = viewModelScope.launch {
                delay(PROGRESS_SAVE_DELAY)
                saveChapterProgress()
            }
        }
    }

    /**
     * Writes the progress of the chapter whose page change hasn't been saved yet.
     */
    private fun saveChapterProgress() {
        val readerChapter = pendingProgressChapter ?: return
        pendingProgressChapter = null
        writeChapterProgress(readerChapter, onComplete = false)
    }

    /**
     * Writes the progress of [readerChapter] on the app scope, so it's saved even when the reader is closing.
     */
    private fun writeChapterProgress(readerChapter: ReaderChapter, onComplete: Boolean) {
        // Immediate and through the mutex, which is fair, so the writes land in the order they were made
        appScope.launch(Dispatchers.Main.immediate) {
            progressWrites.withLock {
                if (onComplete) updateChapterProgressOnComplete(readerChapter)

                val chapter = readerChapter.chapter
                updateChapter.await(
                    ChapterUpdate(chapter.id) {
                        read = chapter.read
                        lastPageRead = chapter.lastPageRead
                    },
                )
            }
        }
    }

    private suspend fun updateChapterProgressOnComplete(readerChapter: ReaderChapter) {
        // SY -->
        if (manga?.isEhBasedManga() == true) {
            viewModelScope.launchNonCancellable {
                val chapterUpdates = unfilteredChapterList
                    .filter { it.sourceOrder > readerChapter.chapter.sourceOrder }
                    .map { chapter ->
                        ChapterUpdate(chapter.id) { read = true }
                    }
                updateChapter.awaitAll(chapterUpdates)
            }
        }
        // SY <--

        updateTrackChapterRead(readerChapter)
        // KNS -->
        notifyChapterRead(readerChapter)
        maybeStartReread()
        // KNS <--
        deleteChapterIfNeeded(readerChapter)

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsRead) return

        val duplicateUnreadChapters = unfilteredChapterList
            .mapNotNull { chapter ->
                if (
                    !chapter.read &&
                    chapter.isRecognizedNumber &&
                    chapter.chapterNumber == readerChapter.chapter.chapterNumber
                ) {
                    ChapterUpdate(chapter.id) { read = true }
                        // KMK -->
                        .also { deleteDupChapterIfNeeded(ReaderChapter(chapter.copy(read = true))) }
                    // KMK <--
                } else {
                    null
                }
            }
        updateChapter.awaitAll(duplicateUnreadChapters)
    }

    fun restartReadTimer() {
        chapterReadStartTime = Clock.System.now()
    }

    /**
     * Saves the chapter last read history on the app scope, so it's written even when the reader is closing.
     */
    fun saveHistory() {
        // Immediate so the read time is taken when the reader pauses
        appScope.launch(Dispatchers.Main.immediate) { updateHistory() }
    }

    /**
     * Saves the chapter last read history if incognito mode isn't on.
     */
    private suspend fun updateHistory() {
        saveChapterProgress()
        getCurrentChapter()?.let { readerChapter ->
            if (incognitoMode) return@let

            val chapterId = readerChapter.chapter.id
            val endTime = Clock.System.now()
            val sessionReadDuration = chapterReadStartTime?.let { (endTime - it).inWholeMilliseconds } ?: 0
            chapterReadStartTime = null

            upsertHistory.await(HistoryUpdate(chapterId, endTime, sessionReadDuration))
        }
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    suspend fun loadNextChapter() {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        loadAdjacent(nextChapter)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    suspend fun loadPreviousChapter() {
        val prevChapter = state.value.viewerChapters?.prevChapter ?: return
        loadAdjacent(prevChapter)
    }

    /**
     * Returns the currently active chapter.
     */
    private fun getCurrentChapter(): ReaderChapter? {
        return state.value.currentChapter
    }

    fun getSource() = state.value.source as? HttpSource

    suspend fun getChapterUrl(): String? {
        val chapter = getCurrentChapter()?.chapter ?: return null
        val source = if (manga?.source == MERGED_SOURCE_ID) {
            state.value.mergedManga?.get(chapter.mangaId)?.source?.let { sourceId ->
                sourceManager.getOrStub(sourceId) as? HttpSource
            }
        } else {
            getSource()
        } ?: return null

        return try {
            source.getChapterUrl(chapter.toSChapter())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun toggleChapterBookmark() {
        val readerChapter = getCurrentChapter() ?: return
        val bookmarked = !readerChapter.chapter.bookmark
        // KNS --> toggling always gives an ordinary bookmark; a long press picks a color
        readerChapter.update { it.copy(bookmark = bookmarked, bookmarkColor = BookmarkColor.DEFAULT_ID) }
        // KNS <--

        // On Main so quick toggles are saved in order
        viewModelScope.launch {
            updateChapter.await(
                ChapterUpdate(readerChapter.chapter.id) {
                    bookmark = bookmarked
                    // KNS -->
                    bookmarkColor = BookmarkColor.DEFAULT_ID
                    // KNS <--
                },
            )
        }

        mutableState.update {
            it.copy(
                bookmarked = bookmarked,
                // KNS -->
                bookmarkColor = BookmarkColor.DEFAULT_ID,
                // KNS <--
            )
        }
    }

    // KNS -->
    /** Bookmarks the current chapter with [color] ([BookmarkColor.NONE] = an ordinary bookmark). */
    fun setChapterBookmarkColor(color: BookmarkColor) {
        val readerChapter = getCurrentChapter() ?: return
        // The same update as the manga and Updates menus, read before the local copy changes.
        val updates = ChapterBookmarks.setColor(listOf(readerChapter.chapter.bookmarkTarget()), color)
        readerChapter.update { it.copy(bookmark = true, bookmarkColor = color.value) }
        viewModelScope.launch { updateChapter.awaitAll(updates) }
        mutableState.update { it.copy(bookmarked = true, bookmarkColor = color.value) }
    }
    // KNS <--

    // SY -->
    fun toggleBookmark(chapterId: Long, bookmarked: Boolean) {
        val readerChapter = chapterList.find { it.chapter.id == chapterId } ?: return
        // KNS -->
        readerChapter.update { it.copy(bookmark = bookmarked, bookmarkColor = BookmarkColor.DEFAULT_ID) }
        // KNS <--
        viewModelScope.launchNonCancellable {
            updateChapter.await(
                ChapterUpdate(chapterId) {
                    bookmark = bookmarked
                    // KNS -->
                    bookmarkColor = BookmarkColor.DEFAULT_ID
                    // KNS <--
                },
            )
        }
        // KNS -->
        if (chapterId == getCurrentChapter()?.chapter?.id) {
            mutableState.update { it.copy(bookmarked = bookmarked, bookmarkColor = BookmarkColor.DEFAULT_ID) }
        }
        // KNS <--
    }
    // SY <--

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultReadingMode.get()
        val manga = manga ?: return default
        val readingMode = ReadingMode.fromPreference(manga.readingMode.toInt())
        // SY -->
        return when {
            resolveDefault && readingMode == ReadingMode.DEFAULT && readerPreferences.useAutoWebtoon.get() -> {
                manga.defaultReaderType(manga.mangaType(sourceName = source?.name))
                    ?: default
            }
            resolveDefault && readingMode == ReadingMode.DEFAULT -> default
            else -> manga.readingMode.toInt()
        }
        // SY <--
    }

    private var readingModeJob: Job? = null

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        // Only the latest pick matters, and letting an older one finish last would restore its choice
        readingModeJob?.cancel()
        readingModeJob = viewModelScope.launch(Dispatchers.IO) {
            setMangaViewerFlags.awaitSetReadingMode(manga.id, readingMode.flagValue.toLong())
            val updatedManga = getManga.await(manga.id)
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.lastPageRead

                mutableState.update { it.copy(manga = updatedManga) }
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientation(resolveDefault: Boolean = true): Int {
        val default = readerPreferences.defaultOrientationType.get()
        val orientation = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
        return when {
            resolveDefault && orientation == ReaderOrientation.DEFAULT -> default
            else -> manga?.readerOrientation?.toInt() ?: default
        }
    }

    private var orientationJob: Job? = null

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(orientation: ReaderOrientation) {
        val manga = manga ?: return
        // Only the latest pick matters, and letting an older one finish last would restore its choice
        orientationJob?.cancel()
        orientationJob = viewModelScope.launch(Dispatchers.IO) {
            setMangaViewerFlags.awaitSetOrientation(manga.id, orientation.flagValue.toLong())
            val updatedManga = getManga.await(manga.id)
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.lastPageRead

                mutableState.update { it.copy(manga = updatedManga) }
                eventChannel.send(Event.SetOrientation(getMangaOrientation()))
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    // SY -->
    fun toggleCropBorders(): Boolean {
        val readingMode = getMangaReadingMode()
        val isPagerType = ReadingMode.isPagerType(readingMode)
        val isWebtoon = ReadingMode.WEBTOON.flagValue == readingMode
        return if (isPagerType) {
            readerPreferences.cropBorders.toggle()
        } else if (isWebtoon) {
            readerPreferences.cropBordersWebtoon.toggle()
        } else {
            readerPreferences.cropBordersContinuousVertical.toggle()
        }
    }
    // SY <--

    /**
     * Generate a filename for the given [manga] and [page]
     */
    private fun generateFilename(
        manga: Manga,
        page: ReaderPage,
    ): String {
        val chapter = page.chapter.chapter
        val filenameSuffix = " - ${page.number}"
        return DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}",
            MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
        ) + filenameSuffix
    }

    fun showMenus(visible: Boolean) {
        mutableState.update { it.copy(menuVisible = visible) }
    }

    // SY -->
    fun showEhUtils(visible: Boolean) {
        mutableState.update { it.copy(ehUtilsVisible = visible) }
    }

    fun setIndexChapterToShift(index: Long?) {
        mutableState.update { it.copy(indexChapterToShift = index) }
    }

    fun setIndexPageToShift(index: Int?) {
        mutableState.update { it.copy(indexPageToShift = index) }
    }

    fun openChapterListDialog() {
        mutableState.update { it.copy(dialog = Dialog.ChapterList) }
    }

    fun setDoublePages(doublePages: Boolean) {
        mutableState.update { it.copy(doublePages = doublePages) }
    }

    fun openAutoScrollHelpDialog() {
        mutableState.update { it.copy(dialog = Dialog.AutoScrollHelp) }
    }

    fun openBoostPageHelp() {
        mutableState.update { it.copy(dialog = Dialog.BoostPageHelp) }
    }

    fun openRetryAllHelp() {
        mutableState.update { it.copy(dialog = Dialog.RetryAllHelp) }
    }

    fun toggleAutoScroll(enabled: Boolean) {
        mutableState.update { it.copy(autoScroll = enabled) }
    }

    fun setAutoScrollFrequency(frequency: String) {
        mutableState.update { it.copy(ehAutoscrollFreq = frequency) }
    }
    // SY <--

    fun showLoadingDialog() {
        mutableState.update { it.copy(dialog = Dialog.Loading) }
    }

    fun openReadingModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.ReadingModeSelect) }
    }

    fun openOrientationModeSelectDialog() {
        mutableState.update { it.copy(dialog = Dialog.OrientationModeSelect) }
    }

    fun openPageDialog(page: ReaderPage/* SY --> */, extraPage: ReaderPage? = null/* SY <-- */) {
        mutableState.update { it.copy(dialog = Dialog.PageActions(page, extraPage)) }
    }

    fun openSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.Settings) }
    }

    // KNS -->
    private val webhookStartedChapters = mutableSetOf<Long>()

    private fun notifyChapterStarted(readerChapter: ReaderChapter) {
        val manga = manga ?: return
        if (!webhookStartedChapters.add(readerChapter.chapter.id)) return
        webhookNotifier.notify(WebhookEvent.CHAPTER_STARTED, manga, mapOf("chapter" to readerChapter.chapter.name))
        if (webhookStartedChapters.size == 1 && chapterList.none { it.chapter.read }) {
            webhookNotifier.notify(WebhookEvent.NEW_MANGA_STARTED, manga)
        }
    }

    private fun notifyChapterRead(readerChapter: ReaderChapter) {
        val manga = manga ?: return
        webhookNotifier.notify(WebhookEvent.CHAPTER_READ, manga, mapOf("chapter" to readerChapter.chapter.name))
        val caughtUp = chapterList.all { it.chapter.read || it.chapter.id == readerChapter.chapter.id }
        if (caughtUp) {
            webhookNotifier.notify(WebhookEvent.MANGA_CAUGHT_UP, manga)
            if (manga.status.toInt() in FINISHED_STATUSES) webhookNotifier.notify(WebhookEvent.MANGA_FINISHED, manga)
        }
    }

    /**
     * Completing a chapter of a manga that is completed on a tracker starts a reread, asks first, or does
     * nothing ([TrackPreferences.autoRereadBehavior]); asked at most once per reader session.
     */
    private fun maybeStartReread() {
        if (incognitoMode || state.value.rereadHandled) return
        val manga = manga ?: return
        mutableState.update { it.copy(rereadHandled = true) }
        viewModelScope.launch {
            val behavior = trackPreferences.autoRereadBehavior.get()
            if (behavior == AutoTrackState.NEVER) return@launch
            val candidates = withIOContext { startReread.candidates(manga.id) }
            if (candidates.isEmpty()) return@launch
            when (behavior) {
                AutoTrackState.ALWAYS -> confirmStartReread()
                AutoTrackState.ASK -> mutableState.update { it.copy(dialog = Dialog.RereadPrompt) }
                AutoTrackState.NEVER -> Unit
            }
        }
    }

    fun confirmStartReread() {
        val manga = manga ?: return
        closeDialog()
        val current = getCurrentChapter()?.chapter
        val chapters = chapterList.map { it.chapter }
        appScope.launch {
            val failures = withIOContext {
                startReread.await(manga.id, chapters, current, trackPreferences.autoRereadResetMode.get())
            }
            failures.forEach { (tracker, error) ->
                logcat(LogPriority.WARN, error) { "Failed to start rereading on ${tracker.name}" }
            }
            withUIContext {
                context.toast(
                    if (failures.isEmpty()) {
                        context.stringResource(KMR.strings.reread_started)
                    } else {
                        context.stringResource(
                            MR.strings.track_error,
                            failures.first().first.name,
                            failures.first().second.message.orEmpty(),
                        )
                    },
                )
            }
        }
    }
    // KNS <--

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun setBrightnessOverlayValue(value: Int) {
        mutableState.update { it.copy(brightnessOverlayValue = value) }
    }

    /**
     * Saves the image of the selected page on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(useExtraPage: Boolean) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val filename = generateFilename(manga, page)

        // Pictures directory.
        val relativePath = if (readerPreferences.folderPerManga.get()) {
            DiskUtil.buildValidFilename(manga.title)
        } else {
            ""
        }

        // The save and its notification outlive the reader, the result event doesn't
        viewModelScope.launch {
            val result = appScope.async {
                try {
                    val uri = imageSaver.save(
                        image = Image.Page(
                            inputStream = page.stream!!,
                            name = filename,
                            location = Location.Pictures.create(relativePath),
                        ),
                    )
                    withContext(Dispatchers.Main) { notifier.onComplete(uri) }
                    SaveImageResult.Success(uri)
                } catch (e: Throwable) {
                    notifier.onError(e.message)
                    SaveImageResult.Error(e)
                }
            }.await()
            eventChannel.send(Event.SavedImage(result))
        }
    }

    // SY -->
    fun saveImages() {
        val (firstPage, secondPage) = (state.value.dialog as? Dialog.PageActions ?: return)
        val viewer = state.value.viewer as? PagerViewer ?: return
        val isLTR = (viewer !is R2LPagerViewer) xor (viewer.config.invertDoublePages)
        val bg = viewer.config.pageCanvasColor

        if (firstPage.status != Page.State.Ready) return
        if (secondPage?.status != Page.State.Ready) return

        val manga = manga ?: return

        // KUN --> using injected context
        val notifier = SaveImageNotifier(context)
        notifier.onClear()
        // KUN <--

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = saveImages(
                    page1 = firstPage,
                    page2 = secondPage,
                    isLTR = isLTR,
                    bg = bg,
                    location = Location.Pictures.create(DiskUtil.buildValidFilename(manga.title)),
                    manga = manga,
                )
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    private fun saveImages(
        page1: ReaderPage,
        page2: ReaderPage,
        isLTR: Boolean,
        @ColorInt bg: Int,
        location: Location,
        manga: Manga,
    ): Uri {
        val stream1 = page1.stream!!
        ImageUtil.findImageType(stream1) ?: throw Exception("Not an image")
        val stream2 = page2.stream!!
        ImageUtil.findImageType(stream2) ?: throw Exception("Not an image")
        // KUN --> migrated off the dropped legacy decoder to the mpreg decoder
        val imageBitmap = ImageDecoder.open(stream1()).use { it.decodeNext() }.let { res ->
            createBitmap(res.width, res.height).also { bitmap ->
                res.image.rewind()
                bitmap.copyPixelsFromBuffer(res.image)
            }.also { res.close() }
        }
        val imageBitmap2 = ImageDecoder.open(stream2()).use { it.decodeNext() }.let { res ->
            createBitmap(res.width, res.height).also { bitmap ->
                res.image.rewind()
                bitmap.copyPixelsFromBuffer(res.image)
            }.also { res.close() }
        }
        // KUN <--

        val chapter = page1.chapter.chapter

        // Build destination file.
        val filenameSuffix = " - ${page1.number}-${page2.number}.jpg"
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}",
            MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
        ) + filenameSuffix

        return imageSaver.save(
            image = Image.Page(
                inputStream = { ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, 0, bg).inputStream() },
                name = filename,
                location = location,
            ),
        )
    }
    // SY <--

    /**
     * Shares the image of the selected page and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(
        copyToClipboard: Boolean,
        // SY -->
        useExtraPage: Boolean,
        // SY <--
    ) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return

        val destDir = context.cacheImageDir

        val filename = generateFilename(manga, page)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = page.stream!!,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, page))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    // SY -->
    fun shareImages(copyToClipboard: Boolean) {
        val (firstPage, secondPage) = (state.value.dialog as? Dialog.PageActions ?: return)
        val viewer = state.value.viewer as? PagerViewer ?: return
        val isLTR = (viewer !is R2LPagerViewer) xor (viewer.config.invertDoublePages)
        val bg = viewer.config.pageCanvasColor

        if (firstPage.status != Page.State.Ready) return
        if (secondPage?.status != Page.State.Ready) return
        val manga = manga ?: return

        // KUN --> using injected context
        val destDir = context.cacheImageDir
        // KUN <--

        try {
            viewModelScope.launchNonCancellable {
                destDir.deleteRecursively()
                val uri = saveImages(
                    page1 = firstPage,
                    page2 = secondPage,
                    isLTR = isLTR,
                    bg = bg,
                    location = Location.Cache,
                    manga = manga,
                )
                eventChannel.send(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, firstPage, secondPage))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }
    // SY <--

    /**
     * Sets the image of the selected page as cover and notifies the UI of the result.
     */
    fun setAsCover(useExtraPage: Boolean) {
        // SY -->
        val page = if (useExtraPage) {
            (state.value.dialog as? Dialog.PageActions)?.extraPage
        } else {
            (state.value.dialog as? Dialog.PageActions)?.page
        }
        // SY <--
        if (page?.status != Page.State.Ready) return
        val manga = manga ?: return
        val stream = page.stream ?: return

        viewModelScope.launch {
            val result = appScope.async {
                try {
                    manga.editCover(coverManager, stream(), updateManga, coverCache)
                    if (manga.isLocal() || manga.favorite) {
                        SetAsCoverResult.Success
                    } else {
                        SetAsCoverResult.AddToLibraryFirst
                    }
                } catch (e: Exception) {
                    SetAsCoverResult.Error
                }
            }.await()
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    enum class SetAsCoverResult {
        Success,
        AddToLibraryFirst,
        Error,
    }

    sealed interface SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult
        class Error(val error: Throwable) : SaveImageResult
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (incognitoMode) return
        if (!trackPreferences.autoUpdateTrack.get()) return

        val manga = manga ?: return

        // Reading the last page and leaving right away is common, so it outlives the reader
        appScope.launch {
            trackChapter.await(context, manga.id, readerChapter.chapter.chapterNumber)
        }
    }

    /**
     * Enqueues these [chapters] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting them across process deaths.
     */
    private suspend fun enqueueDeleteChapters(chapters: List<Chapter>) {
        val manga = manga ?: return
        downloadManager.enqueueChaptersToDelete(chapters, manga)
    }

    private fun enqueueDeleteReadChapters(chapter: ReaderChapter) {
        if (!chapter.chapter.read) return
        val mergedManga = state.value.mergedManga
        // SY -->
        val manga = if (mergedManga.isNullOrEmpty()) {
            manga
        } else {
            mergedManga[chapter.chapter.mangaId]
        } ?: return
        // SY <--

        viewModelScope.launchNonCancellable {
            downloadManager.enqueueChaptersToDelete(listOf(chapter.chapter), manga)
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        appScope.launch {
            // Progress writes started before leaving may still enqueue the chapter just finished
            progressWrites.withLock {}
            downloadManager.deletePendingChapters()
            tempFileManager.deleteTempFiles()
        }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val source: Source? = null,
        val initError: Throwable? = null,
        val viewerChapters: ViewerChapters? = null,
        val bookmarked: Boolean = false,
        // KNS -->
        val bookmarkColor: Int = BookmarkColor.DEFAULT_ID,
        /** Whether this reader session already decided about starting a reread. */
        val rereadHandled: Boolean = false,
        // KNS <--
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,

        /**
         * Viewer used to display the pages (pager, webtoon, ...).
         */
        val viewer: Viewer? = null,
        val dialog: Dialog? = null,
        val menuVisible: Boolean = false,
        @field:IntRange(from = -100, to = 100) val brightnessOverlayValue: Int = 0,

        // SY -->
        /** for display page number in double-page mode */
        val currentPageText: String = "",
        val meta: RaisedSearchMetadata? = null,
        val mergedManga: Map<Long, Manga>? = null,
        val ehUtilsVisible: Boolean = false,
        val lastShiftDoubleState: Boolean? = null,
        val indexPageToShift: Int? = null,
        val indexChapterToShift: Long? = null,
        val doublePages: Boolean = false,
        val dateRelativeTime: Boolean = true,
        val autoScroll: Boolean = false,
        val isAutoScrollEnabled: Boolean = false,
        val ehAutoscrollFreq: String = "",
        // SY <--
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            get() = currentChapter?.pages?.size ?: -1
    }

    sealed interface Dialog {
        data object Loading : Dialog
        data object Settings : Dialog
        data object ReadingModeSelect : Dialog
        data object OrientationModeSelect : Dialog

        // SY -->
        data object ChapterList : Dialog
        // SY <--

        // KNS -->
        data object RereadPrompt : Dialog
        // KNS <--

        data class PageActions(
            val page: ReaderPage,
            // SY -->
            val extraPage: ReaderPage? = null,
            // SY <--
        ) : Dialog

        // SY -->
        data object AutoScrollHelp : Dialog
        data object RetryAllHelp : Dialog
        data object BoostPageHelp : Dialog
        // SY <--
    }

    sealed interface Event {
        data object ReloadViewerChapters : Event
        data object PageChanged : Event
        data class SetOrientation(val orientation: Int) : Event
        data class SetCoverResult(val result: SetAsCoverResult) : Event

        data class SavedImage(val result: SaveImageResult) : Event
        data class ShareImage(
            val uri: Uri,
            val page: ReaderPage,
            // SY -->
            val secondPage: ReaderPage? = null,
            // SY <--
        ) : Event
        data class CopyImage(val uri: Uri) : Event
    }
}

private val PROGRESS_SAVE_DELAY = 2.seconds

// KNS --> statuses after which reading every chapter means the manga is finished
private val FINISHED_STATUSES = setOf(SManga.COMPLETED, SManga.PUBLISHING_FINISHED)
// KNS <--
