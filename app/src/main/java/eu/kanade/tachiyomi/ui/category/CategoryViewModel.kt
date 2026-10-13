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
import tachiyomi.domain.category.interactor.MoveCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.SetCategoryTrackStatus
import tachiyomi.domain.category.interactor.SetCategoryUpdateInterval
import tachiyomi.domain.category.model.Category
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
    // KMK <--
) : ViewModel() {

    private val _events: Channel<CategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val dialog = MutableStateFlow<CategoryDialog?>(null)

    val state: StateFlow<CategoryScreenState> = combine(
        getCategories.subscribe(),
        dialog,
    ) { categories, dialog ->
        CategoryScreenState.Success(
            categories = categories.filterNot(Category::isSystemCategory).toImmutableList(),
            dialog = dialog,
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
    // KMK <--

    fun showDialog(dialog: CategoryDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

sealed interface CategoryDialog {
    data object Create : CategoryDialog
    // KMK -->
    data class TrackStatus(val category: Category) : CategoryDialog
    data class UpdateInterval(val category: Category) : CategoryDialog
    data class CreateSubcategory(val parent: Category) : CategoryDialog
    data class Move(val category: Category) : CategoryDialog
    // KMK <--
    data class Rename(val category: Category) : CategoryDialog
    data class Delete(val category: Category) : CategoryDialog
}

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
    ) : CategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
