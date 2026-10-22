package eu.kanade.tachiyomi.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.datetime.LocalDate
import logcat.LogPriority
import mihon.core.common.utils.mutate
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.SubscriberGate
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class HistoryViewModel(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val addTracks: AddTracks,
    private val getCategories: GetCategories,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getHistory: GetHistory,
    // KNS -->
    private val historyVisibleCounts: HistoryVisibleCounts,
    // KNS <--
    private val getManga: GetManga,
    private val getNextChapters: GetNextChapters,
    private val libraryPreferences: LibraryPreferences,
    private val removeHistory: RemoveHistory,
    private val setMangaCategories: SetMangaCategories,
    private val updateManga: UpdateManga,
    private val sourceManager: SourceManager,
    // KMK -->
    private val historyPreferences: HistoryPreferences,
    // KMK <--
) : ViewModel() {

    private val subscriberGate = SubscriberGate()

    val snackbarHostState: SnackbarHostState = SnackbarHostState()

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val searchQuery = MutableStateFlow<String?>(null)

    private val dialog = MutableStateFlow<Dialog?>(null)

    // KMK -->
    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)

    private val selection = MutableStateFlow(emptySet<Long>())

    private val selectionMode = MutableStateFlow(false)
    // KMK <--

    private val history = combine(
        searchQuery,
        // KMK -->
        getHistoryItemPreferenceFlow()
            .distinctUntilChanged(),
        // KMK <--
        // KNS --> the global Smart Merge switch changes which chapters count (manga rules re-emit by themselves)
        libraryPreferences.smartScanlatorMerge.changes(),
        // KNS <--
    ) { query, itemPreferences, _ -> query to itemPreferences }
        .flatMapLatest { (query, pref) ->
            getHistory.subscribe(
                query ?: "",
                // KMK -->
                // KNS --> filtered below on counts that follow the chapter visibility rule
                unfinishedManga = null,
                // KNS <--
                unfinishedChapter = pref.filterUnfinishedChapter.toBooleanOrNull(),
                nonLibraryEntries = pref.filterNonLibraryManga.toBooleanOrNull(),
                // KMK <--
            )
                // KNS -->
                .map { history ->
                    HistoryVisibleCounts.filterUnfinished(
                        historyVisibleCounts.apply(history),
                        pref.filterUnfinishedManga.toBooleanOrNull(),
                    )
                }
                // KNS <--
                .distinctUntilChanged()
                .catch { error ->
                    logcat(LogPriority.ERROR, error)
                    _events.send(Event.InternalError)
                }
                .map { it.toList() }
                .flowOn(Dispatchers.IO)
        }

    // KMK -->
    private val hasActiveFilters = getHistoryItemPreferenceFlow()
        .map { prefs ->
            listOf(
                prefs.filterUnfinishedManga,
                prefs.filterUnfinishedChapter,
                prefs.filterNonLibraryManga,
            )
                .any { it != TriState.DISABLED }
        }
        .distinctUntilChanged()
    // KMK <--

    val state: StateFlow<State> = combine(
        combine(
            searchQuery,
            history,
            dialog,
            // KMK -->
            selection,
            selectionMode,
            // KMK <--
        ) { searchQuery, history, dialog, selection, selectionMode ->
            State(
                searchQuery = searchQuery,
                // KMK -->
                list = history,
                isLoading = false,
                // KMK <--
                dialog = dialog,
                // KMK -->
                selection = selection,
                selectionMode = selectionMode,
                // KMK <--
            )
        },
        // KMK -->
        hasActiveFilters,
        // KMK <--
    ) { state, hasActiveFilters ->
        state.copy(hasActiveFilters = hasActiveFilters)
    }
        .stateIn(viewModelScope + subscriberGate, subscriberGate.whileSubscribed(5.seconds), State())

    suspend fun getNextChapter(): Chapter? {
        return getNextChapters.await(onlyUnread = false).firstOrNull()
    }

    fun getNextChapterForManga(mangaId: Long, chapterId: Long) {
        viewModelScope.launch {
            sendNextChapterEvent(getNextChapters.await(mangaId, chapterId, onlyUnread = false))
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<Chapter>) {
        val chapter = chapters.firstOrNull()
        _events.send(Event.OpenChapter(chapter))
    }

    // KMK -->
    fun removeFromHistory(toDelete: List<HistoryWithRelations>) {
        viewModelScope.launch(Dispatchers.IO) {
            removeHistory.await(toDelete.map { it.id })
        }
        toggleSelectionMode(false)
    }

    fun removeAllFromHistory(toDelete: List<HistoryWithRelations>) {
        viewModelScope.launch(Dispatchers.IO) {
            removeHistory.awaitManga(toDelete.map { it.mangaId })
        }
        toggleSelectionMode(false)
    }
    // KMK <--

    fun removeAllHistory() {
        viewModelScope.launch {
            val result = removeHistory.awaitAll()
            if (!result) return@launch
            _events.send(Event.HistoryCleared)
        }
        toggleSelectionMode(false)
    }

    fun updateSearchQuery(query: String?) {
        searchQuery.update { query }
    }

    fun setDialog(dialog: Dialog?) {
        this.dialog.update { dialog }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveMangaToCategory(mangaId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveMangaToCategory(mangaId, categoryIds)
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        viewModelScope.launch {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        viewModelScope.launch {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun addFavorite(mangaId: Long) {
        viewModelScope.launch {
            val manga = getManga.await(mangaId) ?: return@launch

            val duplicates = getDuplicateLibraryManga(manga)
            if (duplicates.isNotEmpty()) {
                dialog.update { Dialog.DuplicateManga(manga, duplicates) }
                return@launch
            }

            addFavorite(manga)
        }
    }

    fun addFavorite(manga: Manga) {
        viewModelScope.launch {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launch
                    moveMangaToCategory(manga.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launch
                    moveMangaToCategory(manga.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(manga)
            }

            // Sync with tracking services if applicable
            appScope.launch { addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source)) }
        }
    }

    fun showMigrateDialog(target: Manga, current: Manga) {
        dialog.update { Dialog.Migrate(target = target, current = current) }
    }

    private fun showChangeCategoryDialog(manga: Manga) {
        viewModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            dialog.update {
                Dialog.ChangeCategory(
                    manga = manga,
                    initialSelection = categories.mapAsCheckboxState { it.id in selection }.toList(),
                )
            }
        }
    }

    // KMK -->
    data class HistorySelectionOptions(
        val selected: Boolean,
        val fromLongPress: Boolean = false,
    )

    fun toggleSelection(
        item: HistoryWithRelations,
        selectionOptions: HistorySelectionOptions,
    ) {
        val (selected, fromLongPress) = selectionOptions
        val list = state.value.list

        val newSelection = selection.updateAndGet { currentSelection ->
            if (item.chapterId in currentSelection == selected) return@updateAndGet currentSelection
            val selectedIndex = list.indexOfFirst { it.chapterId == item.chapterId }
            if (selectedIndex < 0) return@updateAndGet currentSelection

            currentSelection.mutate { selection ->
                list.run {
                    val firstSelection = selection.isEmpty()
                    if (selected) selection.add(item.chapterId) else selection.remove(item.chapterId)

                    if (firstSelection) {
                        // Since it can go into selectionMode from toolbar (without long press), we need to set both positions here
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else if (selected && fromLongPress) {
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

                        range.forEach {
                            val inBetweenItem = get(it)
                            if (inBetweenItem.chapterId !in selection) {
                                selection.add(inBetweenItem.chapterId)
                            }
                        }
                    } else if (!fromLongPress) {
                        if (!selected) {
                            if (selectedIndex == selectedPositions[0]) {
                                selectedPositions[0] = indexOfFirst { it.chapterId in selection }
                            } else if (selectedIndex == selectedPositions[1]) {
                                selectedPositions[1] = indexOfLast { it.chapterId in selection }
                            }
                        } else {
                            if (selectedIndex < selectedPositions[0]) {
                                selectedPositions[0] = selectedIndex
                            } else if (selectedIndex > selectedPositions[1]) {
                                selectedPositions[1] = selectedIndex
                            }
                        }
                    }
                }
            }
        }
        selectionMode.value = selected || newSelection.isNotEmpty()
    }

    fun toggleAllSelection(selected: Boolean) {
        val list = state.value.list
        selection.value = if (selected) {
            list.mapTo(mutableSetOf()) { it.chapterId }
        } else {
            emptySet()
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
        selectionMode.value = selected
    }

    fun invertSelection() {
        val list = state.value.list
        selection.update { currentSelection ->
            currentSelection.mutate { selection ->
                list.forEach { item ->
                    if (!selection.remove(item.chapterId)) selection.add(item.chapterId)
                }
            }
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun toggleSelectionMode(newMode: Boolean? = null) {
        if (newMode == false || selectionMode.value) {
            toggleAllSelection(false)
        } else {
            selectionMode.value = newMode ?: !selectionMode.value
        }
    }
    // KMK <--

    // KMK -->
    private fun getHistoryItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            historyPreferences.filterUnfinishedManga.changes(),
            historyPreferences.filterUnfinishedChapter.changes(),
            historyPreferences.filterNonLibraryManga.changes(),
        ) { unfinishedManga, unfinishedChapter, nonLibraryManga ->
            ItemPreferences(
                filterUnfinishedManga = unfinishedManga,
                filterUnfinishedChapter = unfinishedChapter,
                filterNonLibraryManga = nonLibraryManga,
            )
        }
    }

    fun showFilterDialog() {
        dialog.update { Dialog.FilterSheet }
    }

    @Immutable
    private data class ItemPreferences(
        val filterUnfinishedManga: TriState,
        val filterUnfinishedChapter: TriState,
        val filterNonLibraryManga: TriState,
    )
    // KMK <--

    @Immutable
    data class State(
        val searchQuery: String? = null,
        // KMK -->
        val list: List<HistoryWithRelations> = listOf(),
        val isLoading: Boolean = true,
        // KMK <--
        val dialog: Dialog? = null,
        // KMK -->
        val selection: Set<Long> = emptySet(),
        val hasActiveFilters: Boolean = false,
        val selectionMode: Boolean = false,
    ) {
        val selected
            get() = list.fastFilter { it.chapterId in selection }

        fun getUiModel(): List<HistoryUiModel> {
            // KNS --> each day shows how many different manga were read, not how many chapters
            val mangaPerDay = list.groupBy { it.readAt?.toLocalDate() }
                .mapValues { (_, entries) -> entries.distinctBy { it.mangaId }.size }
            // KNS <--
            return getUiModel(mangaPerDay)
        }

        private fun getUiModel(mangaPerDay: Map<LocalDate?, Int>) = list.map { HistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.toLocalDate()
                val afterDate = after?.item?.readAt?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null ->
                        HistoryUiModel.Header(
                            afterDate,
                            // KNS -->
                            mangaCount = mangaPerDay[afterDate] ?: 0,
                            // KNS <--
                        )
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }
    // KMK <--

    sealed interface Dialog {
        data object DeleteAll : Dialog
        // KMK -->
        data class Delete(val histories: List<HistoryWithRelations>) : Dialog {
            constructor(history: HistoryWithRelations) : this(listOf(history))
        }
        // KMK <--
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
        // KMK -->
        data object FilterSheet : Dialog
        // KMK <--
    }

    // KMK -->
    private fun TriState.toBooleanOrNull(): Boolean? {
        return when (this) {
            TriState.DISABLED -> null
            TriState.ENABLED_IS -> true
            TriState.ENABLED_NOT -> false
        }
    }
    // KMK <--

    sealed interface Event {
        data class OpenChapter(val chapter: Chapter?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
