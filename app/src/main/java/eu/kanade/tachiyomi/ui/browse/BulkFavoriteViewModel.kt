package eu.kanade.tachiyomi.ui.browse

import androidx.compose.runtime.Immutable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class BulkFavoriteViewModel(
    private val sourceManager: SourceManager,
    private val libraryPreferences: LibraryPreferences,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val updateManga: UpdateManga,
    private val coverCache: CoverCache,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val addTracks: AddTracks,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
) : ViewModel() {

    val state: StateFlow<BulkFavoriteViewModel.State>
        field = MutableStateFlow<BulkFavoriteViewModel.State>(State())

    fun backHandler() {
        toggleSelectionMode(false)
    }

    fun toggleSelectionMode(newMode: Boolean? = null) {
        state.update { state ->
            val mode = newMode ?: !state.selectionMode
            state.copy(
                selectionMode = mode,
                selection = if (mode) state.selection else listOf(),
            )
        }
    }

    fun select(manga: Manga) {
        toggleSelection(manga, toSelectedState = true)
    }

    /**
     * @param toSelectedState set to `true` to only Select, set to `false` to only Unselect
     */
    fun toggleSelection(manga: Manga, toSelectedState: Boolean? = null) {
        state.update { state ->
            val newSelection = state.selection.toMutableList().also { list ->
                val isSelected = list.fastAny { it.id == manga.id }
                val shouldSelect = toSelectedState ?: !isSelected
                // Both condition to avoid adding duplicate entries
                if (shouldSelect && !isSelected) {
                    list.add(manga)
                } else if (!shouldSelect && isSelected) {
                    list.removeAll { it.id == manga.id }
                }
            }
            state.copy(
                selection = newSelection,
                selectionMode = newSelection.isNotEmpty(),
            )
        }
    }

    fun reverseSelection(mangas: List<Manga>) {
        state.update { state ->
            val newSelection = mangas.filterNot { manga ->
                state.selection.contains(manga)
            }
                .fastDistinctBy { it.id }
                .toList()
            state.copy(
                selection = newSelection,
                selectionMode = newSelection.isNotEmpty(),
            )
        }
    }

    /**
     * Called when user click on [BulkSelectionToolbar]'s `Favorite` button.
     * It will then look for any duplicated mangas.
     * - If there is any, it will show the [DuplicateMangaDialog].
     * - If not then it will call the [addFavoriteDuplicate].
     */
    fun addFavorite(startIdx: Int = 0) {
        viewModelScope.launch {
            startRunning()
            val entryWithDuplicates = getDuplicateLibraryManga(startIdx)
            if (entryWithDuplicates != null) {
                val (index, manga, duplicates) = entryWithDuplicates
                if (state.value.selection.size == 1) {
                    // If only one manga is selected, show the multiple-duplicates dialog.
                    setDialog(Dialog.AddDuplicateManga(manga, duplicates))
                } else {
                    setDialog(Dialog.BulkAllowDuplicate(manga, duplicates, index))
                }
            } else {
                addFavoriteDuplicate()
            }
        }
    }

    /**
     * Add mangas to library if there is default category or no category exists.
     * If not, it shows the categories list.
     *
     * @param skipAllDuplicates if true, skip all duplicates and add all selected mangas to library.
     * if false, allow all duplicates and add all selected mangas to library
     */
    internal fun addFavoriteDuplicate(skipAllDuplicates: Boolean = false) {
        viewModelScope.launch {
            val mangaList = if (skipAllDuplicates) getNotDuplicateLibraryMangas() else state.value.selection
            if (mangaList.isEmpty()) {
                stopRunning()
                toggleSelectionMode(false)
                return@launch
            }
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    stopRunning()
                    setMangasCategories(mangaList, listOf(defaultCategory.id), emptyList())
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    stopRunning()
                    // Automatic 'Default' or no categories
                    setMangasCategories(mangaList, emptyList(), emptyList())
                }

                else -> {
                    // Get indexes of the common categories to preselect.
                    val common = getCommonCategories(mangaList)
                    // Get indexes of the mix categories to preselect.
                    val mix = getMixCategories(mangaList)
                    val preselected = categories
                        .map {
                            when (it) {
                                in common -> CheckboxState.State.Checked(it)
                                in mix -> CheckboxState.TriState.Exclude(it)
                                else -> CheckboxState.State.None(it)
                            }
                        }
                        .toList()
                    stopRunning()
                    setDialog(Dialog.ChangeMangasCategory(mangaList, preselected))
                }
            }
        }
    }

    private suspend fun getNotDuplicateLibraryMangas(): List<Manga> {
        return state.value.selection.filterNot { manga ->
            getDuplicateLibraryManga(manga).isNotEmpty()
        }
    }

    private suspend fun getDuplicateLibraryManga(startIdx: Int = 0): Triple<Int, Manga, List<MangaWithChapterCount>>? {
        val mangas = state.value.selection
        mangas.fastForEachIndexed { index, manga ->
            if (index < startIdx) return@fastForEachIndexed
            val duplicates = getDuplicateLibraryManga(manga)
            if (duplicates.isEmpty()) return@fastForEachIndexed
            return Triple(index, manga, duplicates)
        }
        return null
    }

    internal fun removeDuplicateSelectedManga(index: Int) {
        state.update { state ->
            val newSelection = state.selection.toMutableList().also { list ->
                list.removeAt(index)
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    internal fun setMangasCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        viewModelScope.launchNonCancellable {
            startRunning()
            mangaList.fastForEach { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                moveMangaToCategoriesAndAddToLibrary(manga, categoryIds)
            }
            stopRunning()
        }
        toggleSelectionMode(false)
    }

    private fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        viewModelScope.launchIO {
            try {
                val source = sourceManager.getOrStub(manga.source)
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, source)
                updateManga.awaitUpdateFavorite(manga.id, true)
                val fetchMetadataOnAdd = libraryPreferences.fetchMetadataOnAdd.get()
                val fetchChaptersOnAdd = libraryPreferences.fetchChaptersOnAdd.get()
                if (fetchMetadataOnAdd || fetchChaptersOnAdd) {
                    try {
                        updateMangaFromRemote(
                            source = source,
                            manga = manga,
                            fetchDetails = fetchMetadataOnAdd,
                            fetchChapters = fetchChaptersOnAdd,
                            // FIXME (KMK): Should have throttle here
                        )
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e)
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        viewModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    private suspend fun getCategories(): List<Category> {
        return getCategories.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCategory }
            .orEmpty()
    }

    private fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.filter { it.id != 0L }.map { it.id })
    }

    private fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        viewModelScope.launchIO {
            setMangaCategories.await(
                mangaId = manga.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    /**
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    internal fun changeMangaFavorite(manga: Manga) {
        viewModelScope.launch {
            val source = sourceManager.getOrStub(manga.source)
            var new = manga.copy(
                favorite = !manga.favorite,
                dateAdded = when (manga.favorite) {
                    true -> null
                    false -> Clock.System.now()
                },
            )
            // TODO: also allow deleting chapters when remove favorite (just like in [MangaViewModel])
            if (!new.favorite) {
                new.removeCovers(coverCache)
            } else {
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, source)
            }

            updateManga.await(new.toMangaUpdate())
            val fetchMetadataOnAdd = libraryPreferences.fetchMetadataOnAdd.get()
            val fetchChaptersOnAdd = libraryPreferences.fetchChaptersOnAdd.get()
            if (new.favorite && (fetchMetadataOnAdd || fetchChaptersOnAdd)) {
                try {
                    // Use `manga` instead of `new` so its title got updated with source's `getMangaDetails`
                    updateMangaFromRemote(
                        source = source,
                        manga = manga,
                        fetchDetails = fetchMetadataOnAdd,
                        fetchChapters = fetchChaptersOnAdd,
                        // FIXME (KMK): Should have throttle here
                    )
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }
    }

    internal fun addFavorite(manga: Manga) {
        viewModelScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveMangaToCategories(manga, defaultCategory)
                    changeMangaFavorite(manga)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveMangaToCategories(manga)
                    changeMangaFavorite(manga)
                }

                // Choose a category
                else -> {
                    val preselectedIds = getCategories.await(manga.id).map { it.id }
                    setDialog(
                        Dialog.ChangeMangasCategory(
                            listOf(manga),
                            categories.mapAsCheckboxState { it.id in preselectedIds }.toList(),
                        ),
                    )
                }
            }
        }
    }

    fun addRemoveManga(manga: Manga, haptic: HapticFeedback? = null) {
        viewModelScope.launchIO {
            val duplicates = getDuplicateLibraryManga(manga)
            when {
                manga.favorite -> setDialog(Dialog.RemoveManga(manga))
                duplicates.isNotEmpty() -> setDialog(
                    Dialog.AddDuplicateManga(manga, duplicates),
                )
                else -> addFavorite(manga)
            }
            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    internal fun showMigrateDialog(manga: Manga, duplicate: Manga) {
        setDialog(Dialog.Migrate(target = manga, current = duplicate))
    }

    private fun setDialog(dialog: Dialog?) {
        state.update {
            it.copy(dialog = dialog)
        }
    }

    internal fun dismissDialog() {
        state.update {
            it.copy(dialog = null)
        }
    }

    private fun startRunning() {
        state.update {
            it.copy(isRunning = true)
        }
    }

    internal fun stopRunning() {
        state.update {
            it.copy(isRunning = false)
        }
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
        data class AddDuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class BulkAllowDuplicate(val manga: Manga, val duplicates: List<MangaWithChapterCount>, val currentIdx: Int) : Dialog
        data class RemoveManga(val manga: Manga) : Dialog
        data class ChangeMangasCategory(
            val mangas: List<Manga>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val selection: List<Manga> = listOf(),
        val selectionMode: Boolean = false,
        val isRunning: Boolean = false,
    )
}
