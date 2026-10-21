package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.presentation.category.CategoryManagerRow
import eu.kanade.presentation.category.buildCategoryManagerRows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.HideCategory
import tachiyomi.domain.category.interactor.MergeCategories
import tachiyomi.domain.category.interactor.MoveCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.SetCategoryTrackStatus
import tachiyomi.domain.category.interactor.SetCategoryUpdateInterval
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class CategoryViewModel(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val getCategories: GetCategories,
    private val createCategoryWithName: CreateCategoryWithName,
    private val deleteCategory: DeleteCategory,
    private val reorderCategory: ReorderCategory,
    private val renameCategory: RenameCategory,
    // KMK -->
    private val hideCategory: HideCategory,
    // KMK <--
    // KNS -->
    private val moveCategory: MoveCategory,
    private val mergeCategories: MergeCategories,
    private val setCategoryTrackStatus: SetCategoryTrackStatus,
    private val setCategoryUpdateInterval: SetCategoryUpdateInterval,
    private val getLibraryManga: GetLibraryManga,
    private val libraryPreferences: LibraryPreferences,
    // KNS <--
) : ViewModel() {

    private val _events: Channel<CategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val dialog = MutableStateFlow<CategoryDialog?>(null)

    // KNS -->
    // View state of the tree; cheap to change and independent of the category/library flows.
    private val searchQuery = MutableStateFlow("")
    private val collapsedIds = MutableStateFlow(
        libraryPreferences.categoryManagerCollapsedIds.get().mapNotNull(String::toLongOrNull).toSet(),
    )
    private val sortMode = MutableStateFlow(libraryPreferences.categoryManagerSortMode.get())
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val selectionMode = MutableStateFlow(false)

    /** Manga ids per category; only recomputed when memberships change, not on every library emission. */
    private val mangaIdsByCategory = getLibraryManga.subscribe()
        .map { library ->
            buildMap<Long, MutableSet<Long>> {
                library.forEach { item ->
                    item.categories.forEach { getOrPut(it) { mutableSetOf() } += item.id }
                }
            }.mapValues { it.value.toSet() }
        }
        .distinctUntilChanged()

    private data class ViewState(
        val search: String,
        val collapsed: Set<Long>,
        val sort: Int,
        val selected: Set<Long>,
        val selecting: Boolean,
    )

    private val viewState = combine(searchQuery, collapsedIds, sortMode, selectedIds, selectionMode) { search, collapsed, sort, selected, selecting ->
        ViewState(search, collapsed, sort, selected, selecting)
    }
    // KNS <--

    val state: StateFlow<CategoryScreenState> = combine(
        getCategories.subscribe(),
        dialog,
        // KNS -->
        mangaIdsByCategory,
        viewState,
        // KNS <--
    ) { categories, dialog, mangaIds, view ->
        val userCategories = categories.filterNot(Category::isSystemCategory)
        CategoryScreenState.Success(
            categories = userCategories,
            dialog = dialog,
            // KNS -->
            rows = buildCategoryManagerRows(
                categories = userCategories,
                searchQuery = view.search,
                collapsedIds = view.collapsed,
                sortMode = view.sort,
                mangaIdsByCategory = mangaIds,
            ),
            searchQuery = view.search,
            sortMode = view.sort,
            selectedIds = view.selected.filterTo(mutableSetOf()) { id -> userCategories.any { it.id == id } },
            selectionMode = view.selecting,
            // KNS <--
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), CategoryScreenState.Loading)

    fun createCategory(
        name: String,
        // KNS -->
        parentId: Long = Category.UNCATEGORIZED_ID,
        // KNS <--
    ) {
        viewModelScope.launch {
            when (createCategoryWithName.await(name, parentId)) {
                is CreateCategoryWithName.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                // KNS -->
                is CreateCategoryWithName.Result.DuplicateName -> _events.send(CategoryEvent.DuplicateName)
                // KNS <--
                else -> {}
            }
        }
    }

    // KMK -->
    fun hideCategory(category: Category) {
        viewModelScope.launch {
            when (hideCategory.await(category)) {
                is HideCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }
    // KMK <--

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            // The deletion finishes even if the screen is left while it runs
            when (appScope.async { deleteCategory.await(categoryId = categoryId) }.await()) {
                is DeleteCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                // KNS -->
                DeleteCategory.Result.DuplicateName -> _events.send(CategoryEvent.DuplicateName)
                // KNS <--
                else -> {}
            }
        }
    }

    /** [newIndex] is the position among the category's siblings. */
    fun changeOrder(category: Category, newIndex: Int) {
        viewModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        viewModelScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                // KNS -->
                RenameCategory.Result.DuplicateName -> _events.send(CategoryEvent.DuplicateName)
                // KNS <--
                else -> {}
            }
        }
    }

    // KNS -->
    fun moveCategory(categoryId: Long, newParentId: Long) {
        viewModelScope.launch {
            val position = (state.value as? CategoryScreenState.Success)?.categories
                ?.count { it.parentId == newParentId && it.id != categoryId }
                ?: 0
            sendMoveResult(moveCategory.await(categoryId, newParentId, position))
            dismissDialog()
        }
    }

    fun bulkMove(ids: List<Long>, newParentId: Long) {
        viewModelScope.launch {
            ids.forEach { id ->
                val position = (state.value as? CategoryScreenState.Success)?.categories
                    ?.count { it.parentId == newParentId && it.id != id }
                    ?: 0
                sendMoveResult(moveCategory.await(id, newParentId, position))
            }
            finishSelection()
        }
    }

    private suspend fun sendMoveResult(result: MoveCategory.Result) {
        when (result) {
            MoveCategory.Result.InvalidMove -> _events.send(CategoryEvent.InvalidMove)
            MoveCategory.Result.DuplicateName -> _events.send(CategoryEvent.DuplicateName)
            is MoveCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
            else -> {}
        }
    }

    fun mergeInto(targetId: Long, sourceIds: List<Long>) {
        viewModelScope.launch {
            when (appScope.async { mergeCategories.await(targetId, sourceIds) }.await()) {
                MergeCategories.Result.DuplicateName -> _events.send(CategoryEvent.DuplicateName)
                MergeCategories.Result.InvalidMerge -> _events.send(CategoryEvent.InvalidMove)
                is MergeCategories.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                MergeCategories.Result.Success -> {}
            }
            finishSelection()
        }
    }

    fun setTrackStatus(categoryId: Long, trackStatus: Int?) {
        viewModelScope.launch {
            if (!setCategoryTrackStatus.await(categoryId, trackStatus)) _events.send(CategoryEvent.InternalError)
            dismissDialog()
        }
    }

    fun setUpdateInterval(categoryId: Long, intervalDays: Int?) {
        viewModelScope.launch {
            if (!setCategoryUpdateInterval.await(categoryId, intervalDays)) _events.send(CategoryEvent.InternalError)
            dismissDialog()
        }
    }

    fun bulkSetHidden(hidden: Boolean) {
        val selected = selectedIds.value
        viewModelScope.launch {
            val categories = (state.value as? CategoryScreenState.Success)?.categories.orEmpty()
                .filter { it.id in selected }
            if (hideCategory.await(categories, hidden) is HideCategory.Result.InternalError) {
                _events.send(CategoryEvent.InternalError)
            }
            finishSelection()
        }
    }

    fun bulkDelete(ids: List<Long>) {
        viewModelScope.launch {
            ids.forEach { id ->
                when (appScope.async { deleteCategory.await(id) }.await()) {
                    is DeleteCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                    DeleteCategory.Result.DuplicateName -> _events.send(CategoryEvent.DuplicateName)
                    DeleteCategory.Result.Success -> {}
                }
            }
            finishSelection()
        }
    }

    fun setSearchQuery(value: String) = searchQuery.update { value }

    fun setSortMode(value: Int) {
        sortMode.update { value }
        libraryPreferences.categoryManagerSortMode.set(value)
    }

    fun toggleCollapsed(categoryId: Long) {
        collapsedIds.update { current ->
            (if (categoryId in current) current - categoryId else current + categoryId).also(::persistCollapsed)
        }
    }

    fun expandAll() {
        collapsedIds.value = emptySet()
        persistCollapsed(emptySet())
    }

    fun collapseAll() {
        val parents = (state.value as? CategoryScreenState.Success)?.categories.orEmpty()
            .mapTo(mutableSetOf(), Category::parentId)
            .minus(Category.UNCATEGORIZED_ID)
        collapsedIds.value = parents
        persistCollapsed(parents)
    }

    private fun persistCollapsed(ids: Set<Long>) {
        libraryPreferences.categoryManagerCollapsedIds.set(ids.mapTo(mutableSetOf(), Long::toString))
    }

    fun toggleSelection(categoryId: Long) = selectedIds.update { current ->
        if (categoryId in current) current - categoryId else current + categoryId
    }

    fun selectIds(ids: Set<Long>) = selectedIds.update { ids }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode.value = enabled
        if (!enabled) selectedIds.value = emptySet()
    }

    private fun finishSelection() {
        selectedIds.value = emptySet()
        selectionMode.value = false
        dismissDialog()
    }
    // KNS <--

    fun showDialog(dialog: CategoryDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

sealed interface CategoryDialog {
    data object Create : CategoryDialog
    data class Rename(val category: Category) : CategoryDialog
    data class Delete(val category: Category) : CategoryDialog

    // KNS -->
    data class CreateSubcategory(val parent: Category) : CategoryDialog
    data class Move(val category: Category) : CategoryDialog
    data class TrackStatus(val category: Category) : CategoryDialog
    data class UpdateInterval(val category: Category) : CategoryDialog
    data class MoveMany(val ids: List<Long>) : CategoryDialog
    data class MergeMany(val ids: List<Long>) : CategoryDialog
    data class DeleteMany(val ids: List<Long>) : CategoryDialog
    // KNS <--
}

sealed interface CategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : CategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)

    // KNS -->
    data object DuplicateName : LocalizedMessage(KMR.strings.category_error_duplicate_sibling)
    data object InvalidMove : LocalizedMessage(KMR.strings.category_error_invalid_move)
    // KNS <--
}

sealed interface CategoryScreenState {

    @Immutable
    data object Loading : CategoryScreenState

    @Immutable
    data class Success(
        val categories: List<Category>,
        val dialog: CategoryDialog? = null,
        // KNS -->
        val rows: List<CategoryManagerRow> = emptyList(),
        val searchQuery: String = "",
        val sortMode: Int = 0,
        val selectedIds: Set<Long> = emptySet(),
        val selectionMode: Boolean = false,
        // KNS <--
    ) : CategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
