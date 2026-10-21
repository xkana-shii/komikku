package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.CategoryScreenActions
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryDeleteManyDialog
import eu.kanade.presentation.category.components.CategoryMergeTargetDialog
import eu.kanade.presentation.category.components.CategoryParentPickerDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.category.components.CategoryRowActions
import eu.kanade.presentation.category.components.CategoryTrackStatusDialog
import eu.kanade.presentation.category.components.CategoryUpdateIntervalDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class CategoryScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<CategoryViewModel>()

        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state is CategoryScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as CategoryScreenState.Success

        // KNS -->
        // Sibling names are what a new or renamed category must not repeat.
        fun siblingNames(parentId: Long, excluding: Long? = null) = successState.categories
            .filter { it.parentId == parentId && it.id != excluding }
            .map { it.name }

        CategoryScreen(
            state = successState,
            actions = CategoryScreenActions(
                onClickCreate = { viewModel.showDialog(CategoryDialog.Create) },
                onChangeOrder = viewModel::changeOrder,
                onSearchQuery = viewModel::setSearchQuery,
                onSortMode = viewModel::setSortMode,
                onExpandAll = viewModel::expandAll,
                onCollapseAll = viewModel::collapseAll,
                onSelectionMode = viewModel::setSelectionMode,
                onSelectIds = viewModel::selectIds,
                onBulkHidden = viewModel::bulkSetHidden,
                onBulkMove = { viewModel.showDialog(CategoryDialog.MoveMany(successState.selectedIds.toList())) },
                onBulkMerge = { viewModel.showDialog(CategoryDialog.MergeMany(successState.selectedIds.toList())) },
                onBulkDelete = { viewModel.showDialog(CategoryDialog.DeleteMany(successState.selectedIds.toList())) },
            ),
            rowActions = CategoryRowActions(
                onRename = { viewModel.showDialog(CategoryDialog.Rename(it)) },
                onDelete = { viewModel.showDialog(CategoryDialog.Delete(it)) },
                // KMK -->
                onHide = viewModel::hideCategory,
                // KMK <--
                onCreateSubcategory = { viewModel.showDialog(CategoryDialog.CreateSubcategory(it)) },
                onMove = { viewModel.showDialog(CategoryDialog.Move(it)) },
                onTrackStatus = { viewModel.showDialog(CategoryDialog.TrackStatus(it)) },
                onUpdateInterval = { viewModel.showDialog(CategoryDialog.UpdateInterval(it)) },
                onToggleCollapsed = { viewModel.toggleCollapsed(it.id) },
                onToggleSelection = { viewModel.toggleSelection(it.id) },
                onStartSelection = {
                    viewModel.setSelectionMode(true)
                    viewModel.toggleSelection(it.id)
                },
            ),
            navigateUp = navigator::pop,
        )
        // KNS <--

        when (val dialog = successState.dialog) {
            null -> {}
            CategoryDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onCreate = { viewModel.createCategory(it) },
                    // KNS -->
                    categories = siblingNames(Category.UNCATEGORIZED_ID),
                    // KNS <--
                )
            }
            is CategoryDialog.Rename -> {
                CategoryRenameDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onRename = { viewModel.renameCategory(dialog.category, it) },
                    // KNS -->
                    categories = siblingNames(dialog.category.parentId, excluding = dialog.category.id),
                    // KNS <--
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
            // KNS -->
            is CategoryDialog.CreateSubcategory -> {
                CategoryCreateDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    onCreate = { viewModel.createCategory(it, dialog.parent.id) },
                    categories = siblingNames(dialog.parent.id),
                    title = stringResource(KMR.strings.category_create_subcategory),
                    extraMessage = stringResource(KMR.strings.category_create_subcategory_in, dialog.parent.name),
                    alreadyExistsError = KMR.strings.category_error_duplicate_sibling,
                )
            }
            is CategoryDialog.Move -> {
                CategoryParentPickerDialog(
                    title = stringResource(KMR.strings.category_move),
                    categories = successState.categories,
                    movingIds = setOf(dialog.category.id),
                    currentParentId = dialog.category.parentId,
                    onSelect = { viewModel.moveCategory(dialog.category.id, it) },
                    onDismissRequest = viewModel::dismissDialog,
                )
            }
            is CategoryDialog.MoveMany -> {
                CategoryParentPickerDialog(
                    title = stringResource(KMR.strings.category_manager_move_title),
                    categories = successState.categories,
                    movingIds = dialog.ids.toSet(),
                    currentParentId = null,
                    onSelect = { viewModel.bulkMove(dialog.ids, it) },
                    onDismissRequest = viewModel::dismissDialog,
                )
            }
            is CategoryDialog.MergeMany -> {
                CategoryMergeTargetDialog(
                    categories = successState.categories,
                    selectedIds = dialog.ids,
                    onSelect = { target -> viewModel.mergeInto(target, dialog.ids - target) },
                    onDismissRequest = viewModel::dismissDialog,
                )
            }
            is CategoryDialog.DeleteMany -> {
                CategoryDeleteManyDialog(
                    count = dialog.ids.size,
                    onDelete = { viewModel.bulkDelete(dialog.ids) },
                    onDismissRequest = viewModel::dismissDialog,
                )
            }
            is CategoryDialog.TrackStatus -> {
                CategoryTrackStatusDialog(
                    current = dialog.category.trackStatus,
                    onSelect = { viewModel.setTrackStatus(dialog.category.id, it) },
                    onDismissRequest = viewModel::dismissDialog,
                )
            }
            is CategoryDialog.UpdateInterval -> {
                CategoryUpdateIntervalDialog(
                    current = dialog.category.updateInterval,
                    onSelect = { viewModel.setUpdateInterval(dialog.category.id, it) },
                    onDismissRequest = viewModel::dismissDialog,
                )
            }
            // KNS <--
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
