package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryMergeDialog
import eu.kanade.presentation.category.components.CategoryMoveDialog
import eu.kanade.presentation.category.components.CategoryParentPickerDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.category.components.TrackStatusPickerDialog
import eu.kanade.presentation.category.components.UpdateIntervalPickerDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class CategoryScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<CategoryViewModel>()

        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state is CategoryScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as CategoryScreenState.Success

        CategoryScreen(
            state = successState,
            onClickCreate = { viewModel.showDialog(CategoryDialog.Create) },
            onClickRename = { viewModel.showDialog(CategoryDialog.Rename(it)) },
            onClickDelete = { viewModel.showDialog(CategoryDialog.Delete(it)) },
            onChangeOrder = viewModel::changeOrder,
            // KMK -->
            onClickHide = viewModel::hideCategory,
            onClickTrackStatus = { viewModel.showDialog(CategoryDialog.TrackStatus(it)) },
            onClickUpdateInterval = { viewModel.showDialog(CategoryDialog.UpdateInterval(it)) },
            onClickCreateSubcategory = { viewModel.showDialog(CategoryDialog.CreateSubcategory(it)) },
            onClickMove = { viewModel.showDialog(CategoryDialog.Move(it)) },
            onReparentSubcategory = viewModel::reparentSubcategory,
            onSearchQuery = viewModel::setSearchQuery,
            onToggleCollapsed = viewModel::toggleCollapsed,
            onExpandAll = viewModel::expandAll,
            onCollapseAll = viewModel::collapseAll,
            onSortMode = viewModel::setSortMode,
            onToggleSelection = viewModel::toggleSelection,
            onToggleSelectionMode = { viewModel.setSelectionMode(!successState.selectionMode) },
            onSelectVisible = viewModel::selectIds,
            onBulkHide = { viewModel.bulkSetHidden(true) },
            onBulkShow = { viewModel.bulkSetHidden(false) },
            onShowBulkMove = {
                viewModel.showDialog(CategoryDialog.MoveMany(successState.selectedIds.toList()))
            },
            onShowBulkMerge = {
                viewModel.showDialog(CategoryDialog.MergeMany(successState.selectedIds.toList()))
            },
            onShowBulkDelete = {
                viewModel.showDialog(CategoryDialog.DeleteMany(successState.selectedIds.toList()))
            },
            // KMK <--
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            CategoryDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onCreate = viewModel::createCategory,
                    // KMK --> Root categories only compete with other root categories.
                    categories = successState.categories.filter { it.parentId == 0L }.fastMap { it.name }.toImmutableList(),
                )
            }
            is CategoryDialog.Rename -> {
                CategoryRenameDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onRename = { viewModel.renameCategory(dialog.category, it) },
                    // KMK --> A rename only needs to check the current category's siblings.
                    categories = successState.categories
                        .filter { it.parentId == dialog.category.parentId && it.id != dialog.category.id }
                        .fastMap { it.name }
                        .toImmutableList(),
                    category = dialog.category.name,
                )
            }
            is CategoryDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onDelete = { viewModel.deleteCategory(dialog.category.id) },
                    category = dialog.category.name,
                )
            }
            // KMK -->
            is CategoryDialog.TrackStatus -> {
                TrackStatusPickerDialog(
                    currentStatus = dialog.category.trackStatus,
                    onSelect = { status ->
                        viewModel.setTrackStatus(dialog.category.id, status)
                        viewModel.dismissDialog()
                    },
                    onDismiss = viewModel::dismissDialog,
                )
            }
            is CategoryDialog.UpdateInterval -> {
                UpdateIntervalPickerDialog(
                    currentIntervalDays = dialog.category.updateInterval,
                    onSelect = { days ->
                        viewModel.setUpdateInterval(dialog.category.id, days)
                        viewModel.dismissDialog()
                    },
                    onDismiss = viewModel::dismissDialog,
                )
            }
            is CategoryDialog.CreateSubcategory -> {
                CategoryCreateDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onCreate = { viewModel.createSubcategory(dialog.parent, it) },
                    // KMK --> Same names are valid below different parents.
                    categories = successState.categories
                        .filter { it.parentId == dialog.parent.id }
                        .fastMap { it.name }
                        .toImmutableList(),
                    title = stringResource(tachiyomi.i18n.kmk.KMR.strings.category_create_subcategory),
                )
            }
            is CategoryDialog.Move -> {
                CategoryParentPickerDialog(
                    category = dialog.category,
                    categories = successState.categories,
                    onSelect = { parentId ->
                        viewModel.moveCategory(dialog.category.id, parentId)
                        viewModel.dismissDialog()
                    },
                    onDismiss = viewModel::dismissDialog,
                )
            }
            is CategoryDialog.MoveMany -> {
                CategoryMoveDialog(
                    parents = successState.categories.filter { it.parentId == 0L }.toImmutableList(),
                    excludedIds = dialog.ids.toSet(),
                    onDismissRequest = viewModel::dismissDialog,
                    onConfirm = { parentId -> viewModel.bulkMove(dialog.ids, parentId) },
                )
            }
            is CategoryDialog.MergeMany -> {
                val categoriesById = successState.categories.associateBy { it.id }
                CategoryMergeDialog(
                    candidates = dialog.ids.mapNotNull(categoriesById::get).toImmutableList(),
                    onDismissRequest = viewModel::dismissDialog,
                    onConfirm = { targetId ->
                        viewModel.mergeInto(targetId, dialog.ids.filterNot { it == targetId })
                    },
                )
            }
            is CategoryDialog.DeleteMany -> {
                CategoryDeleteDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onDelete = { viewModel.bulkDelete(dialog.ids) },
                    title = stringResource(tachiyomi.i18n.kmk.KMR.strings.category_manager_delete_many_title),
                    text = stringResource(tachiyomi.i18n.kmk.KMR.strings.category_manager_delete_many, dialog.ids.size),
                )
            }
            // KMK <--
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                if (event is CategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
