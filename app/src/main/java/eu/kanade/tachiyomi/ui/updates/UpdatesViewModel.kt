package eu.kanade.tachiyomi.ui.updates

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.util.system.workManager
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.SubscriberGate
import tachiyomi.core.common.util.lang.pausedWhileUnsubscribed
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.service.UpdatesPreferences
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class UpdatesViewModel(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val context: Context,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val updateChapter: UpdateChapter,
    private val setReadStatus: SetReadStatus,
    private val getUpdates: GetUpdates,
    private val getManga: GetManga,
    private val getChapter: GetChapter,
    private val libraryPreferences: LibraryPreferences,
    private val updatesPreferences: UpdatesPreferences,
    // SY -->
    readerPreferences: ReaderPreferences,
    // SY <--
    // KNS -->
    private val updatesVisibilityFilter: UpdatesVisibilityFilter,
    // KNS <--
) : ViewModel() {

    private val subscriberGate = SubscriberGate()

    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    val lastUpdated by libraryPreferences.lastUpdatedTimestamp.asState(viewModelScope)

    // SY -->
    val preserveReadingPosition by readerPreferences.preserveReadingPosition.asState(viewModelScope)
    // SY <--

    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedChapterIds = MutableStateFlow(emptySet<Long>())

    private val dialog = MutableStateFlow<Dialog?>(null)

    private val downloadStates = MutableStateFlow(emptyMap<Long, Download.State>())

    // Progress ticks often, so only the download indicators that read it recompose instead of every tick rebuilding
    // the list
    private val downloadProgress = mutableStateMapOf<Long, Int>()

    // A finished chapter leaves the queue, and its last status change can be lost with it, so its state is left to
    // the queried item once it's no longer queued
    private val queuedDownloadStates = combine(downloadStates, downloadManager.queueState) { states, queue ->
        val queuedChapterIds = queue.mapTo(HashSet()) { it.chapter.id }
        states.filterKeys { it in queuedChapterIds }
    }

    // KMK -->
    private val expandedState = MutableStateFlow(emptySet<String>())
    // KMK <--

    init {
        viewModelScope.launch {
            downloadManager.statusFlow()
                .catch { logcat(LogPriority.ERROR, it) }
                .collect(this@UpdatesViewModel::updateDownloadState)
        }
        viewModelScope.launch {
            // Each progress tick rebuilds every row of the list, so cap how often that happens
            downloadManager.progressFlow().sample(200.milliseconds)
                .catch { logcat(LogPriority.ERROR, it) }
                .collect { downloadProgress[it.chapter.id] = it.progress }
        }
    }

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

    private val hasActiveFilters = getUpdatesItemPreferenceFlow()
        .map { prefs ->
            listOf(
                prefs.filterUnread,
                prefs.filterDownloaded,
                prefs.filterStarted,
                prefs.filterBookmarked,
            )
                .any { it != TriState.DISABLED } ||
                prefs.filterExcludedScanlators ||
                listOf(
                    prefs.filterIncludedCategories,
                    prefs.filterExcludedCategories,
                )
                    .any { it.isNotEmpty() }
        }
        .distinctUntilChanged()

    private val updateItems = combine(
        // needed for SQL filters (unread, started, bookmarked, etc)
        getUpdatesItemPreferenceFlow()
            .distinctUntilChanged()
            .flatMapLatest {
                getUpdates.subscribe(
                    // Set date limit for recent chapters
                    Clock.System.now().minus(3, DateTimeUnit.MONTH, TimeZone.currentSystemDefault()),
                    unread = it.filterUnread.toBooleanOrNull(),
                    started = it.filterStarted.toBooleanOrNull(),
                    bookmarked = it.filterBookmarked.toBooleanOrNull(),
                    hideExcludedScanlators = it.filterExcludedScanlators,
                    includedCategories = it.filterIncludedCategories,
                    excludedCategories = it.filterExcludedCategories,
                ).distinctUntilChanged()
            },
        downloadCache.changes.pausedWhileUnsubscribed(),
        downloadManager.queueState.pausedWhileUnsubscribed(),
        // needed for Kotlin filters (downloaded)
        getUpdatesItemPreferenceFlow().distinctUntilChanged { old, new ->
            old.filterDownloaded == new.filterDownloaded
        },
    ) { updates, _, _, itemPreferences ->
        updates
            // KNS -->
            .let { updatesVisibilityFilter.filter(it) }
            // KNS <--
            .toUpdateItems()
            .applyFilters(itemPreferences)
            // KMK -->
            .groupBy { it.update.dateFetch.toLocalDate() }
            .flatMap { (_, mangas) ->
                mangas.groupBy { it.update.mangaId }
                    .values
                    .flatMap { mangaChapters ->
                        val (unread, read) = mangaChapters.partition { !it.update.read }
                        unread.sortedBy { it.update.dateFetch } +
                            read.sortedByDescending { it.update.dateFetch }
                    }
            }
        // KMK <--
    }
        .catch {
            logcat(LogPriority.ERROR, it)
            _events.send(Event.InternalError)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope + subscriberGate, SharingStarted.Eagerly, null)

    val state: StateFlow<State> = combine(
        combine(
            updateItems,
            selectedChapterIds,
            queuedDownloadStates.pausedWhileUnsubscribed(),
            dialog,
            // KMK -->
            expandedState,
            // KMK <--
        ) { items, selectedIds, downloads, dialog, expandedState ->
            State(
                isLoading = items == null,
                items = items.orEmpty().map { item ->
                    val downloadState = downloads[item.update.chapterId]
                    item.copy(
                        selected = item.update.chapterId in selectedIds,
                        downloadStateProvider = if (downloadState != null) {
                            { downloadState }
                        } else {
                            item.downloadStateProvider
                        },
                    )
                }
                    .toList(),
                // KMK -->
                expandedState = expandedState,
                // KMK <--
                dialog = dialog,
            )
        },
        hasActiveFilters,
    ) { state, hasActiveFilters ->
        state.copy(hasActiveFilters = hasActiveFilters)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope + subscriberGate, subscriberGate.whileSubscribed(5.seconds), State())

    private fun List<UpdatesItem>.applyFilters(
        preferences: ItemPreferences,
    ): List<UpdatesItem> {
        val filterDownloaded = preferences.filterDownloaded

        val filterFnDownloaded: (UpdatesItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.downloadStateProvider() == Download.State.DOWNLOADED
            }
        }

        return fastFilter {
            filterFnDownloaded(it)
        }
    }

    private fun List<UpdatesWithRelations>.toUpdateItems(): List<UpdatesItem> {
        val queuedDownloads = downloadManager.getQueuedDownloadsByChapterId()
        return this
            .map { update ->
                val activeDownload = queuedDownloads[update.chapterId]
                val downloaded = downloadManager.isChapterDownloaded(
                    update.chapterName,
                    update.scanlator,
                    update.chapterUrl,
                    // SY -->
                    update.ogMangaTitle,
                    // SY <--
                    update.sourceId,
                )
                val downloadState = when {
                    activeDownload != null -> activeDownload.status
                    downloaded -> Download.State.DOWNLOADED
                    else -> Download.State.NOT_DOWNLOADED
                }
                UpdatesItem(
                    update = update,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { downloadProgress[update.chapterId] ?: 0 },
                )
            }
    }

    suspend fun updateLibrary(): Boolean {
        val started = LibraryUpdateWorker.startNow(context)
        _events.send(Event.LibraryUpdateTriggered(started))
        return started
    }

    fun downloadChapters(items: List<UpdatesItem>, action: ChapterDownloadAction) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            when (action) {
                ChapterDownloadAction.START -> {
                    downloadChapters(items)
                    if (items.any { it.downloadStateProvider() == Download.State.ERROR }) {
                        downloadManager.startDownloads()
                    }
                }
                ChapterDownloadAction.START_NOW -> {
                    val chapterId = items.singleOrNull()?.update?.chapterId ?: return@launch
                    startDownloadingNow(chapterId)
                }
                ChapterDownloadAction.CANCEL -> {
                    val chapterId = items.singleOrNull()?.update?.chapterId ?: return@launch
                    cancelDownload(chapterId)
                }
                ChapterDownloadAction.DELETE -> {
                    deleteChapters(items)
                }
            }
            toggleAllSelection(false)
        }
    }

    private suspend fun startDownloadingNow(chapterId: Long) {
        downloadManager.startDownloadNow(chapterId)
    }

    private suspend fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    /**
     * Mark the selected updates list as read/unread.
     * @param updates the list of selected updates.
     * @param read whether to mark chapters as read or unread.
     */
    fun markUpdatesRead(updates: List<UpdatesItem>, read: Boolean) {
        appScope.launch {
            setReadStatus.await(
                read = read,
                chapters = updates
                    .mapNotNull { getChapter.await(it.update.chapterId) }
                    .toTypedArray(),
            )
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param updates the list of chapters to bookmark.
     */
    fun bookmarkUpdates(updates: List<UpdatesItem>, bookmark: Boolean) {
        viewModelScope.launch {
            updates
                .filterNot { it.update.bookmark == bookmark }
                .map { ChapterUpdate(it.update.chapterId) { this.bookmark = bookmark } }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param updatesItem the list of chapters to download.
     */
    private fun downloadChapters(updatesItem: List<UpdatesItem>) {
        appScope.launch {
            val groupedUpdates = updatesItem.groupBy { it.update.mangaId }.values
            for (updates in groupedUpdates) {
                val mangaId = updates.first().update.mangaId
                val manga = getManga.await(mangaId) ?: continue
                // Don't download if source isn't available
                sourceManager.get(manga.source) ?: continue
                val chapters = updates.mapNotNull { getChapter.await(it.update.chapterId) }
                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    /**
     * Delete selected chapters
     *
     * @param updatesItem list of chapters
     */
    fun deleteChapters(updatesItem: List<UpdatesItem>) {
        appScope.launch {
            updatesItem
                .groupBy { it.update.mangaId }
                .entries
                .forEach { (mangaId, updates) ->
                    val manga = getManga.await(mangaId) ?: return@forEach
                    val source = sourceManager.get(manga.source) ?: return@forEach
                    val chapters = updates.mapNotNull { getChapter.await(it.update.chapterId) }
                    downloadManager.deleteChapters(
                        chapters,
                        manga,
                        source,
                        // KMK -->
                        ignoreCategoryExclusion = true,
                        // KMK <--
                    )
                }
        }
        toggleAllSelection(false)
    }

    fun showConfirmDeleteChapters(updatesItem: List<UpdatesItem>) {
        setDialog(Dialog.DeleteConfirmation(updatesItem))
    }

    // KMK -->
    /** Bundles all of the boolean flags for update‐selection into one type */
    data class UpdateSelectionOptions(
        val selected: Boolean,
        val fromLongPress: Boolean = false,
        val isGroup: Boolean = false,
        val isExpanded: Boolean = false,
    )
    // KMK <--

    fun toggleSelection(
        item: UpdatesItem,
        // KMK -->
        selectionOptions: UpdateSelectionOptions,
        // KMK <--
    ) {
        // KMK -->
        val (selected, fromLongPress, isGroup, isExpanded) = selectionOptions
        // KMK <--
        val items = state.value.items
        val selectedIndex = items.indexOfFirst { it.update.chapterId == item.update.chapterId }
        if (selectedIndex < 0) return

        // Read selection from its own flow, not the derived items, which lag behind it.
        val currentSelection = selectedChapterIds.value
        if ((item.update.chapterId in currentSelection) == selected) return

        // Only visible items count, since the id set can retain ids filtered out of the list
        val firstSelection = items.none { it.update.chapterId in currentSelection }
        val newSelection = currentSelection.toHashSet()
        newSelection.addOrRemove(item.update.chapterId, selected)

        // KMK -->
        if (isGroup && !isExpanded) {
            val selectedItemDate = item.update.dateFetch.toLocalDate()
            val zone = TimeZone.currentSystemDefault()
            val dayStart = selectedItemDate.atStartOfDayIn(zone)
            val dayEnd = selectedItemDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

            items
                .filter {
                    it.update.mangaId == item.update.mangaId &&
                        it.update.dateFetch in dayStart..<dayEnd
                }
                .forEach { newSelection.addOrRemove(it.update.chapterId, selected) }
        }
        // KMK <--

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

                range.forEach { newSelection.add(items[it].update.chapterId) }
            }
        } else if (!fromLongPress) {
            if (!selected) {
                if (selectedIndex == selectedPositions[0]) {
                    selectedPositions[0] = items.indexOfFirst { it.update.chapterId in newSelection }
                } else if (selectedIndex == selectedPositions[1]) {
                    selectedPositions[1] = items.indexOfLast { it.update.chapterId in newSelection }
                }
            } else {
                if (selectedIndex < selectedPositions[0]) {
                    selectedPositions[0] = selectedIndex
                } else if (selectedIndex > selectedPositions[1]) {
                    selectedPositions[1] = selectedIndex
                }
            }
        }

        selectedChapterIds.update { newSelection }
    }

    fun toggleAllSelection(selected: Boolean) {
        val ids = if (selected) state.value.items.map { it.update.chapterId }.toSet() else emptySet()
        selectedChapterIds.update { ids }

        // KMK -->
        selectedPositions[0] = -1
        selectedPositions[1] = -1
        // KMK <--
    }

    fun invertSelection() {
        val current = selectedChapterIds.value
        val ids = state.value.items
            .map { it.update.chapterId }
            .filterNot { it in current }
            .toSet()
        selectedChapterIds.update { ids }

        // KMK -->
        selectedPositions[0] = -1
        selectedPositions[1] = -1
        // KMK <--
    }

    fun setDialog(dialog: Dialog?) {
        this.dialog.update { dialog }
    }

    fun resetNewUpdatesCount() {
        libraryPreferences.newUpdatesCount.set(0)
    }

    // KMK -->
    fun toggleExpandedState(key: String) {
        expandedState.update { current ->
            current.toMutableSet().apply {
                if (current.contains(key)) remove(key) else add(key)
            }
        }
    }

    val chapterSwipeStartAction by libraryPreferences.swipeToEndAction.asState(viewModelScope)
    val chapterSwipeEndAction by libraryPreferences.swipeToStartAction.asState(viewModelScope)

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    fun updateSwipe(updateItem: UpdatesItem, swipeAction: LibraryPreferences.ChapterSwipeAction) {
        viewModelScope.launch {
            executeUpdateSwipeAction(updateItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun executeUpdateSwipeAction(
        updateItem: UpdatesItem,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val update = updateItem.update
        when (swipeAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markUpdatesRead(listOf(updateItem), !update.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkUpdates(listOf(updateItem), !update.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction = when (updateItem.downloadStateProvider()) {
                    Download.State.ERROR,
                    Download.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    Download.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                downloadChapters(
                    items = listOf(updateItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> throw IllegalStateException()
        }
    }
    // KMK <--

    private fun getUpdatesItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            updatesPreferences.filterDownloaded.changes(),
            updatesPreferences.filterUnread.changes(),
            updatesPreferences.filterStarted.changes(),
            updatesPreferences.filterBookmarked.changes(),
            updatesPreferences.filterExcludedScanlators.changes(),
            updatesPreferences.filterIncludedCategories.changes(),
            updatesPreferences.filterExcludedCategories.changes(),
        ) {
            @Suppress("UNCHECKED_CAST")
            ItemPreferences(
                filterDownloaded = it[0] as TriState,
                filterUnread = it[1] as TriState,
                filterStarted = it[2] as TriState,
                filterBookmarked = it[3] as TriState,
                filterExcludedScanlators = it[4] as Boolean,
                filterIncludedCategories = it[5] as List<Long>,
                filterExcludedCategories = it[6] as List<Long>,
            )
        }
    }

    fun showFilterDialog() {
        dialog.update { Dialog.FilterSheet }
    }

    @Immutable
    private data class ItemPreferences(
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterExcludedScanlators: Boolean,
        val filterIncludedCategories: List<Long>,
        val filterExcludedCategories: List<Long>,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val hasActiveFilters: Boolean = false,
        val items: List<UpdatesItem> = listOf(),
        // KMK -->
        val expandedState: Set<String> = setOf(),
        // KMK <--
        val dialog: Dialog? = null,
    ) {
        val selected = items.filter { it.selected }
        val selectionMode = selected.isNotEmpty()

        fun getUiModel(): List<UpdatesUiModel> {
            // KMK -->
            return items
                .groupBy { it.update.dateFetch.toLocalDate() }
                .flatMap { (date, mangas) ->
                    val header = UpdatesUiModel.Header(date, mangas.size)
                    val mangaItems = mangas
                        .groupBy { it.update.mangaId }
                        .values
                        .flatMap { mangaChapters ->
                            val isExpandable = mangaChapters.size > 1
                            var lastMangaId = -1L
                            mangaChapters.map { chapter ->
                                if (chapter.update.mangaId != lastMangaId) {
                                    lastMangaId = chapter.update.mangaId
                                    UpdatesUiModel.Leader(chapter, isExpandable)
                                } else {
                                    UpdatesUiModel.Item(chapter, isExpandable)
                                }
                            }
                        }
                    listOf(header) + mangaItems
                }
                .distinctBy {
                    when (it) {
                        is UpdatesUiModel.Header -> it.hashCode()
                        is UpdatesUiModel.Item -> "${it.item.update.mangaId}-${it.item.update.chapterId}"
                    }
                }
            // KMK <--
        }
    }

    sealed interface Dialog {
        data class DeleteConfirmation(val toDelete: List<UpdatesItem>) : Dialog
        data object FilterSheet : Dialog
    }

    sealed interface Event {
        data object InternalError : Event
        data class LibraryUpdateTriggered(val started: Boolean) : Event
    }
}

private fun TriState.toBooleanOrNull(): Boolean? {
    return when (this) {
        TriState.DISABLED -> null
        TriState.ENABLED_IS -> true
        TriState.ENABLED_NOT -> false
    }
}

@Immutable
data class UpdatesItem(
    val update: UpdatesWithRelations,
    val downloadStateProvider: () -> Download.State,
    val downloadProgressProvider: () -> Int,
    val selected: Boolean = false,
) {
    // SY -->
    fun isEhBasedUpdate(): Boolean {
        return update.sourceId == EH_SOURCE_ID || update.sourceId == EXH_SOURCE_ID
    }
    // SY <--
}

// KMK -->
/** String to identify which manga's update on which day it is collapsing */
fun UpdatesWithRelations.groupByDateAndManga() = "${dateFetch.toLocalDate().toEpochDays()}-$mangaId"
// KMK <--
