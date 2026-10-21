package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import eu.kanade.presentation.category.components.CategoryRowActions
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.ui.category.CategoryScreenState
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.CallMerge
import mihon.icons.materialsymbols.rounded.Checklist
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Folder
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.SelectAll
import mihon.icons.materialsymbols.roundedfilled.Visibility
import mihon.icons.materialsymbols.roundedfilled.VisibilityOff
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

// KNS -->
/** Screen-level actions of the category manager (row actions are in [CategoryRowActions]). */
class CategoryScreenActions(
    val onClickCreate: () -> Unit,
    /** Moves a category to the given position among its siblings. */
    val onChangeOrder: (Category, Int) -> Unit,
    val onSearchQuery: (String) -> Unit,
    val onSortMode: (Int) -> Unit,
    val onExpandAll: () -> Unit,
    val onCollapseAll: () -> Unit,
    val onSelectionMode: (Boolean) -> Unit,
    val onSelectIds: (Set<Long>) -> Unit,
    val onBulkHidden: (Boolean) -> Unit,
    val onBulkMove: () -> Unit,
    val onBulkMerge: () -> Unit,
    val onBulkDelete: () -> Unit,
)
// KNS <--

@Composable
fun CategoryScreen(
    state: CategoryScreenState.Success,
    // KNS -->
    actions: CategoryScreenActions,
    rowActions: CategoryRowActions,
    // KNS <--
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            // KNS -->
            if (state.selectionMode) {
                AppBar(
                    titleContent = { Text(text = state.selectedIds.size.toString()) },
                    navigateUp = { actions.onSelectionMode(false) },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(KMR.strings.category_manager_select_all),
                                    icon = MaterialSymbols.Rounded.SelectAll,
                                    onClick = { actions.onSelectIds(state.rows.mapTo(mutableSetOf()) { it.category.id }) },
                                ),
                                AppBar.Action(
                                    title = stringResource(KMR.strings.category_manager_move),
                                    icon = MaterialSymbols.Rounded.Folder,
                                    onClick = actions.onBulkMove,
                                    enabled = state.selectedIds.isNotEmpty(),
                                ),
                                AppBar.Action(
                                    title = stringResource(KMR.strings.category_manager_merge),
                                    icon = MaterialSymbols.AutoMirroredRounded.CallMerge,
                                    onClick = actions.onBulkMerge,
                                    enabled = state.selectedIds.size >= 2,
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_delete),
                                    icon = MaterialSymbols.Rounded.Delete,
                                    onClick = actions.onBulkDelete,
                                    enabled = state.selectedIds.isNotEmpty(),
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(KMR.strings.category_manager_hide),
                                    onClick = { actions.onBulkHidden(true) },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(KMR.strings.category_manager_show),
                                    onClick = { actions.onBulkHidden(false) },
                                ),
                            ),
                        )
                    },
                )
            } else {
                AppBar(
                    title = stringResource(MR.strings.action_edit_categories),
                    navigateUp = navigateUp,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(KMR.strings.category_manager_select),
                                    icon = MaterialSymbols.Rounded.Checklist,
                                    onClick = { actions.onSelectionMode(true) },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(KMR.strings.category_manager_expand_all),
                                    onClick = actions.onExpandAll,
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(KMR.strings.category_manager_collapse_all),
                                    onClick = actions.onCollapseAll,
                                ),
                            ),
                        )
                    },
                )
            }
            // KNS <--
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = actions.onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_category,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        CategoryContent(
            state = state,
            lazyListState = lazyListState,
            paddingValues = paddingValues,
            actions = actions,
            rowActions = rowActions,
        )
    }
}

// KNS -->
@Composable
private fun CategoryContent(
    state: CategoryScreenState.Success,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    actions: CategoryScreenActions,
    rowActions: CategoryRowActions,
) {
    // Dragging only changes the manual order, so it is off while searching, sorting or selecting.
    val reorderEnabled = state.searchQuery.isBlank() &&
        state.sortMode == CategoryManagerSort.MANUAL &&
        !state.selectionMode
    val rowState = remember { state.rows.toMutableStateList() }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        if (!reorderEnabled) return@rememberReorderableLazyListState
        val fromIndex = rowState.indexOfFirst { it.category.key == from.key }
        val toIndex = rowState.indexOfFirst { it.category.key == to.key }
        if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState
        val moved = rowState.removeAt(fromIndex)
        rowState.add(toIndex, moved)
        draggedId = moved.category.id
    }

    // One write per drop: the position is committed when the drag ends, not on every step.
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            draggedId?.let { id ->
                val category = rowState.firstOrNull { it.category.id == id }?.category
                val index = siblingIndexAfterDrop(rowState, id)
                if (category != null && index != null) actions.onChangeOrder(category, index)
            }
            draggedId = null
        }
    }
    LaunchedEffect(state.rows) {
        if (!reorderableState.isAnyItemDragging) {
            rowState.clear()
            rowState.addAll(state.rows)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = paddingValues +
            topSmallPaddingValues +
            PaddingValues(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        item(key = "category-manager-search") {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = actions.onSearchQuery,
                modifier = Modifier.fillMaxWidth().animateItem(),
                placeholder = { Text(stringResource(KMR.strings.category_manager_search)) },
                leadingIcon = { Icon(MaterialSymbols.Rounded.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        item(key = "category-manager-sort") {
            SortModeSelector(
                sortMode = state.sortMode,
                onSortMode = actions.onSortMode,
                modifier = Modifier.animateItem(),
            )
        }
        items(items = rowState, key = { it.category.key }) { row ->
            ReorderableItem(reorderableState, row.category.key) {
                CategoryListItem(
                    modifier = Modifier.animateItem(),
                    row = row,
                    actions = rowActions,
                    showDragHandle = reorderEnabled,
                    selectionMode = state.selectionMode,
                    selected = row.category.id in state.selectedIds,
                )
            }
        }
    }
}

@Composable
private fun SortModeSelector(sortMode: Int, onSortMode: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val labels = linkedMapOf(
        CategoryManagerSort.MANUAL to stringResource(KMR.strings.category_manager_sort_manual),
        CategoryManagerSort.AZ to stringResource(KMR.strings.category_manager_sort_az),
        CategoryManagerSort.ZA to stringResource(KMR.strings.category_manager_sort_za),
        CategoryManagerSort.MOST_ITEMS to stringResource(KMR.strings.category_manager_sort_most),
        CategoryManagerSort.LEAST_ITEMS to stringResource(KMR.strings.category_manager_sort_least),
        CategoryManagerSort.DATE_ADDED_NEWEST to stringResource(KMR.strings.category_manager_sort_date_added_newest),
        CategoryManagerSort.DATE_ADDED_OLDEST to stringResource(KMR.strings.category_manager_sort_date_added_oldest),
        CategoryManagerSort.DATE_MODIFIED_NEWEST to
            stringResource(KMR.strings.category_manager_sort_date_modified_newest),
        CategoryManagerSort.DATE_MODIFIED_OLDEST to
            stringResource(KMR.strings.category_manager_sort_date_modified_oldest),
    )
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { expanded = true }) {
                Text("${stringResource(KMR.strings.category_manager_sort)}: ${labels[sortMode].orEmpty()}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { RadioButton(selected = mode == sortMode, onClick = null) },
                        onClick = {
                            expanded = false
                            onSortMode(mode)
                        },
                    )
                }
            }
        }
    }
}
// KNS <--

private val Category.key inline get() = "category-$id"
