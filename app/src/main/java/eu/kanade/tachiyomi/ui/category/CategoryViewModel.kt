package eu.kanade.tachiyomi.ui.category

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class CategoryViewModel(
    private val getCategories: GetCategories = Injekt.get(),
    private val createCategoryWithName: CreateCategoryWithName = Injekt.get(),
    private val deleteCategory: DeleteCategory = Injekt.get(),
    private val reorderCategory: ReorderCategory = Injekt.get(),
    private val renameCategory: RenameCategory = Injekt.get(),
    // KMK -->
    private val hideCategory: HideCategory = Injekt.get(),
    private val setCategoryTrackStatus: SetCategoryTrackStatus = Injekt.get(),
    private val setCategoryUpdateInterval: SetCategoryUpdateInterval = Injekt.get(),
    private val moveCategory: MoveCategory = Injekt.get(),
    private val mergeCategories: MergeCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val categoryRepository: CategoryRepository = Injekt.get(),
    // KMK <--
) : ViewModel() {

    private val _events: Channel<CategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val dialog = MutableStateFlow<CategoryDialog?>(null)
    private val searchQuery = MutableStateFlow("")
    private val collapsedIds = MutableStateFlow(
        libraryPreferences.categoryManagerCollapsedIds().get().mapNotNull { it.toLongOrNull() }.toSet(),
    )
    private val sortMode = MutableStateFlow(libraryPreferences.categoryManagerSortMode().get())
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val selectionMode = MutableStateFlow(false)

    private val categoryData = combine(
        getCategories.subscribe(),
        getLibraryManga.subscribe(),
    ) { categories, library ->
        CategoryManagerData(
            categories = categories.filterNot(Category::isSystemCategory).toImmutableList(),
            mangaCounts = library.flatMap { manga -> manga.categories.map { it to manga.id } }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.distinct().size },
        )
    }

    val state: StateFlow<CategoryScreenState> = combine(
        categoryData,
        combine(dialog, searchQuery, collapsedIds, ::Triple),
        combine(sortMode, selectedIds, selectionMode, ::Triple),
    ) { data, (dialog, search, collapsed), (sort, selected, selecting) ->
        CategoryScreenState.Success(
            categories = data.categories,
            dialog = dialog,
            searchQuery = search,
            collapsedIds = collapsed,
            sortMode = sort,
            selectedIds = selected,
            selectionMode = selecting,
            mangaCounts = data.mangaCounts,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), CategoryScreenState.Loading)

    fun createCategory(name: String) {
        viewModelScope.launch {
            when (createCategoryWithName.await(name)) {
                is CreateCategoryWithName.Result.InternalError -> _events.send(CategoryEvent.InternalError)
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
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

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
                else -> {}
            }
        }
    }

    // KMK -->
    fun setTrackStatus(categoryId: Long, trackStatus: Int?) {
        viewModelScope.launch {
            setCategoryTrackStatus.await(categoryId, trackStatus)
        }
    }

    fun setUpdateInterval(categoryId: Long, updateIntervalDays: Int?) {
        viewModelScope.launch {
            setCategoryUpdateInterval.await(categoryId, updateIntervalDays)
            // Rebuild the single periodic worker as soon as a category changes its cadence.
            LibraryUpdateJob.setupTask(Injekt.get<Application>())
        }
    }

    fun createSubcategory(parent: Category, name: String) {
        viewModelScope.launch {
            when (createCategoryWithName.await(name, parent.id)) {
                is CreateCategoryWithName.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun moveCategory(categoryId: Long, parentId: Long) {
        viewModelScope.launch {
            if (!moveCategory.await(categoryId, parentId)) _events.send(CategoryEvent.InternalError)
        }
    }

    fun reparentSubcategory(category: Category, parentId: Long, newIndex: Int) {
        viewModelScope.launch {
            if (!moveCategory.await(category.id, parentId, newIndex)) {
                _events.send(CategoryEvent.InternalError)
            }
        }
    }

    // KMK --> Category manager state is cheap to update and persisted independently from the
    // category/library flows, so search and expansion never rebuild library grouping.
    fun setSearchQuery(value: String) = searchQuery.update { value }

    fun setSortMode(value: Int) {
        sortMode.update { value }
        libraryPreferences.categoryManagerSortMode().set(value)
    }

    fun toggleCollapsed(categoryId: Long) {
        collapsedIds.update { current ->
            (current.toMutableSet().apply { if (!add(categoryId)) remove(categoryId) }).also {
                libraryPreferences.categoryManagerCollapsedIds().set(it.map(Long::toString).toSet())
            }
        }
    }

    fun expandAll() {
        collapsedIds.value = emptySet()
        libraryPreferences.categoryManagerCollapsedIds().set(emptySet())
    }

    fun collapseAll() {
        val ids = state.value.let { it as? CategoryScreenState.Success }
            ?.categories.orEmpty().filter { it.parentId == 0L }.mapTo(mutableSetOf(), Category::id)
        collapsedIds.value = ids
        libraryPreferences.categoryManagerCollapsedIds().set(ids.map(Long::toString).toSet())
    }

    fun toggleSelection(categoryId: Long) = selectedIds.update { current ->
        current.toMutableSet().apply { if (!add(categoryId)) remove(categoryId) }
    }

    fun clearSelection() = selectedIds.update { emptySet() }

    fun selectIds(ids: Set<Long>) = selectedIds.update { ids }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode.value = enabled
        if (!enabled) selectedIds.value = emptySet()
    }

    fun bulkSetHidden(hidden: Boolean) {
        val ids = selectedIds.value
        viewModelScope.launch {
            ids.forEach { categoryRepository.updateHidden(it, hidden) }
            finishSelection()
        }
    }

    fun bulkDelete(ids: List<Long>) {
        viewModelScope.launch {
            ids.forEach { deleteCategory.await(it) }
            finishSelection()
        }
    }

    fun bulkMove(ids: List<Long>, parentId: Long) {
        viewModelScope.launch {
            val state = state.value as? CategoryScreenState.Success ?: return@launch
            val selectedSet = ids.toSet()
            if (parentId in selectedSet) {
                _events.send(CategoryEvent.InternalError)
                return@launch
            }
            var nextIndex = state.categories.count { it.parentId == parentId && it.id !in selectedSet }
            var failed = false
            ids.forEach { id ->
                if (!moveCategory.await(id, parentId, nextIndex++)) failed = true
            }
            if (failed) _events.send(CategoryEvent.InternalError)
            finishSelection()
        }
    }

    fun mergeInto(targetId: Long, sourceIds: List<Long>) {
        viewModelScope.launch {
            if (mergeCategories.await(targetId, sourceIds) is MergeCategories.Result.InternalError) {
                _events.send(CategoryEvent.InternalError)
            }
            finishSelection()
        }
    }

    private fun finishSelection() {
        selectedIds.value = emptySet()
        selectionMode.value = false
        dismissDialog()
    }
    // KMK <--
    // KMK <--

    fun showDialog(dialog: CategoryDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

private data class CategoryManagerData(
    val categories: ImmutableList<Category>,
    val mangaCounts: Map<Long, Int>,
)

sealed interface CategoryDialog {
    data object Create : CategoryDialog
    // KMK -->
    data class TrackStatus(val category: Category) : CategoryDialog
    data class UpdateInterval(val category: Category) : CategoryDialog
    data class CreateSubcategory(val parent: Category) : CategoryDialog
    data class Move(val category: Category) : CategoryDialog
    data class MoveMany(val ids: List<Long>) : CategoryDialog
    data class MergeMany(val ids: List<Long>) : CategoryDialog
    data class DeleteMany(val ids: List<Long>) : CategoryDialog
    // KMK <--
    data class Rename(val category: Category) : CategoryDialog
    data class Delete(val category: Category) : CategoryDialog
}

// KMK --> Persisted values; append new modes instead of renumbering existing ones.
object CategoryManagerSort {
    const val MANUAL = 0
    const val AZ = 1
    const val ZA = 2
    const val MOST_ITEMS = 3
    const val LEAST_ITEMS = 4
    const val DATE_ADDED_NEWEST = 5
    const val DATE_ADDED_OLDEST = 6
    const val DATE_MODIFIED_NEWEST = 7
    const val DATE_MODIFIED_OLDEST = 8
}
// KMK <--

sealed interface CategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : CategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface CategoryScreenState {

    @Immutable
    data object Loading : CategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val dialog: CategoryDialog? = null,
        // KMK -->
        val searchQuery: String = "",
        val collapsedIds: Set<Long> = emptySet(),
        val sortMode: Int = 0,
        val selectedIds: Set<Long> = emptySet(),
        val selectionMode: Boolean = false,
        val mangaCounts: Map<Long, Int> = emptyMap(),
        // KMK <--
    ) : CategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
