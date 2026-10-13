package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.category.CategoryManagerSort
import eu.kanade.tachiyomi.ui.category.CategoryScreenState
import kotlinx.collections.immutable.ImmutableList
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

// KMK --> Adapted from Houri's category manager Compose implementation.
@Composable
fun CategoryScreen(
    state: CategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickTrackStatus: (Category) -> Unit,
    onClickUpdateInterval: (Category) -> Unit,
    onClickCreateSubcategory: (Category) -> Unit,
    onClickMove: (Category) -> Unit,
    onReparentSubcategory: (Category, Long, Int) -> Unit,
    onSearchQuery: (String) -> Unit,
    onToggleCollapsed: (Long) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onSortMode: (Int) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSelectVisible: (Set<Long>) -> Unit,
    onBulkHide: () -> Unit,
    onBulkShow: () -> Unit,
    onShowBulkMove: () -> Unit,
    onShowBulkMerge: () -> Unit,
    onShowBulkDelete: () -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_edit_categories),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onToggleSelectionMode) {
                        Icon(
                            imageVector = Icons.Outlined.Checklist,
                            contentDescription = stringResource(KMR.strings.category_manager_select),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(lazyListState = lazyListState, onCreate = onClickCreate)
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
            onClickRename = onClickRename,
            onClickDelete = onClickDelete,
            onChangeOrder = onChangeOrder,
            onClickHide = onClickHide,
            onClickTrackStatus = onClickTrackStatus,
            onClickUpdateInterval = onClickUpdateInterval,
            onClickCreateSubcategory = onClickCreateSubcategory,
            onClickMove = onClickMove,
            onReparentSubcategory = onReparentSubcategory,
            onSearchQuery = onSearchQuery,
            onToggleCollapsed = onToggleCollapsed,
            onExpandAll = onExpandAll,
            onCollapseAll = onCollapseAll,
            onSortMode = onSortMode,
            onToggleSelection = onToggleSelection,
            onSelectVisible = onSelectVisible,
            onBulkHide = onBulkHide,
            onBulkShow = onBulkShow,
            onShowBulkMove = onShowBulkMove,
            onShowBulkMerge = onShowBulkMerge,
            onShowBulkDelete = onShowBulkDelete,
        )
    }
}

@Composable
private fun CategoryContent(
    state: CategoryScreenState.Success,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickTrackStatus: (Category) -> Unit,
    onClickUpdateInterval: (Category) -> Unit,
    onClickCreateSubcategory: (Category) -> Unit,
    onClickMove: (Category) -> Unit,
    onReparentSubcategory: (Category, Long, Int) -> Unit,
    onSearchQuery: (String) -> Unit,
    onToggleCollapsed: (Long) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onSortMode: (Int) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectVisible: (Set<Long>) -> Unit,
    onBulkHide: () -> Unit,
    onBulkShow: () -> Unit,
    onShowBulkMove: () -> Unit,
    onShowBulkMerge: () -> Unit,
    onShowBulkDelete: () -> Unit,
) {
    val rows = remember(
        state.categories,
        state.searchQuery,
        state.collapsedIds,
        state.sortMode,
        state.mangaCounts,
    ) {
        buildCategoryManagerRows(
            categories = state.categories,
            searchQuery = state.searchQuery,
            collapsedIds = state.collapsedIds,
            sortMode = state.sortMode,
            mangaCounts = state.mangaCounts,
        )
    }
    val subcategoryCounts = remember(state.categories) {
        state.categories.groupingBy(Category::parentId).eachCount()
    }
    val reorderEnabled = state.searchQuery.isBlank() &&
        state.sortMode == CategoryManagerSort.MANUAL &&
        !state.selectionMode
    val rowState = remember { rows.toMutableStateList() }
    var pendingDrop by remember { mutableStateOf<Pair<CategoryManagerRow, Int>?>(null) }
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        if (!reorderEnabled) return@rememberReorderableLazyListState
        val fromRow = rowState.indexOfFirst { it.category.key == from.key }
        val toRow = rowState.indexOfFirst { it.category.key == to.key }
        if (fromRow == -1 || toRow == -1) return@rememberReorderableLazyListState
        val moved = rowState.removeAt(fromRow)
        rowState.add(toRow, moved)
        pendingDrop = moved to toRow
    }

    fun commitDrop(moved: CategoryManagerRow, dropIndex: Int) {
        if (!moved.isTopLevel) {
            val newParentId = rowState.take(dropIndex).lastOrNull(CategoryManagerRow::isTopLevel)
                ?.category?.id
                ?: moved.category.parentId
            val siblingIndex = rowState.take(dropIndex).count { row ->
                !row.isTopLevel && parentAt(rowState, rowState.indexOf(row)) == newParentId
            }
            if (newParentId == moved.category.parentId) {
                onChangeOrder(moved.category, siblingIndex)
            } else {
                onReparentSubcategory(moved.category, newParentId, siblingIndex)
            }
        } else {
            onChangeOrder(moved.category, rowState.take(dropIndex).count(CategoryManagerRow::isTopLevel))
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            pendingDrop?.let { (moved, index) -> commitDrop(moved, index) }
            pendingDrop = null
        }
    }
    LaunchedEffect(rows) {
        if (!reorderableState.isAnyItemDragging) {
            rowState.clear()
            rowState.addAll(rows)
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
                onValueChange = onSearchQuery,
                modifier = Modifier.fillMaxWidth().animateItem(),
                placeholder = { Text(stringResource(KMR.strings.category_manager_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        item(key = "category-manager-sort") {
            Column(modifier = Modifier.fillMaxWidth().animateItem()) {
                SortModeDropdown(sortMode = state.sortMode, onSortMode = onSortMode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onExpandAll) {
                        Text(stringResource(KMR.strings.category_manager_expand_all))
                    }
                    TextButton(onClick = onCollapseAll) {
                        Text(stringResource(KMR.strings.category_manager_collapse_all))
                    }
                }
            }
        }
        if (state.selectionMode) {
            item(key = "category-manager-bulk-actions") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    TextButton(onClick = { onSelectVisible(rows.mapTo(mutableSetOf()) { it.category.id }) }) {
                        Text(stringResource(KMR.strings.category_manager_select))
                    }
                    TextButton(onClick = onBulkHide, enabled = state.selectedIds.isNotEmpty()) {
                        Text(stringResource(KMR.strings.category_manager_hide))
                    }
                    TextButton(onClick = onBulkShow, enabled = state.selectedIds.isNotEmpty()) {
                        Text(stringResource(KMR.strings.category_manager_show))
                    }
                    TextButton(onClick = onShowBulkMove, enabled = state.selectedIds.isNotEmpty()) {
                        Text(stringResource(KMR.strings.category_manager_move))
                    }
                    TextButton(onClick = onShowBulkMerge, enabled = state.selectedIds.size >= 2) {
                        Text(stringResource(KMR.strings.category_manager_merge))
                    }
                    TextButton(onClick = onShowBulkDelete, enabled = state.selectedIds.isNotEmpty()) {
                        Text(stringResource(MR.strings.action_delete))
                    }
                }
            }
        }
        items(items = rowState, key = { it.category.key }) { row ->
            ReorderableItem(reorderableState, row.category.key) {
                CategoryListItem(
                    modifier = Modifier
                        .animateItem()
                        .padding(start = if (row.isTopLevel) 0.dp else 16.dp),
                    category = row.category,
                    onRename = { onClickRename(row.category) },
                    onDelete = { onClickDelete(row.category) },
                    onHide = { onClickHide(row.category) },
                    onMove = { onClickMove(row.category) },
                    onCreateSubcategory = ({ onClickCreateSubcategory(row.category) }).takeIf { row.isTopLevel },
                    isTopLevel = row.isTopLevel,
                    subcategoryCount = subcategoryCounts[row.category.id] ?: 0,
                    showDragHandle = reorderEnabled,
                    mangaCount = row.mangaCount,
                    expanded = (!row.collapsed).takeIf {
                        row.isTopLevel && (subcategoryCounts[row.category.id] ?: 0) > 0
                    },
                    onToggleExpand = ({ onToggleCollapsed(row.category.id) }).takeIf { row.isTopLevel },
                    selected = (row.category.id in state.selectedIds).takeIf { state.selectionMode },
                    onToggleSelection = ({ onToggleSelection(row.category.id) }).takeIf { state.selectionMode },
                    onClickTrackStatus = { onClickTrackStatus(row.category) },
                    onClickUpdateInterval = { onClickUpdateInterval(row.category) },
                )
            }
        }
    }
}

@Composable
private fun SortModeDropdown(sortMode: Int, onSortMode: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = linkedMapOf(
        CategoryManagerSort.MANUAL to stringResource(KMR.strings.category_manager_sort_manual),
        CategoryManagerSort.AZ to stringResource(KMR.strings.category_manager_sort_az),
        CategoryManagerSort.ZA to stringResource(KMR.strings.category_manager_sort_za),
        CategoryManagerSort.MOST_ITEMS to stringResource(KMR.strings.category_manager_sort_most),
        CategoryManagerSort.LEAST_ITEMS to stringResource(KMR.strings.category_manager_sort_least),
        CategoryManagerSort.DATE_ADDED_NEWEST to stringResource(KMR.strings.category_manager_sort_date_added_newest),
        CategoryManagerSort.DATE_ADDED_OLDEST to stringResource(KMR.strings.category_manager_sort_date_added_oldest),
        CategoryManagerSort.DATE_MODIFIED_NEWEST to stringResource(KMR.strings.category_manager_sort_date_modified_newest),
        CategoryManagerSort.DATE_MODIFIED_OLDEST to stringResource(KMR.strings.category_manager_sort_date_modified_oldest),
    )
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("${stringResource(KMR.strings.category_manager_sort)}: ${labels[sortMode]}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSortMode(mode)
                    },
                )
            }
        }
    }
}

internal data class CategoryManagerRow(
    val category: Category,
    val isTopLevel: Boolean,
    val mangaCount: Int,
    val collapsed: Boolean = false,
)

internal fun buildCategoryManagerRows(
    categories: List<Category>,
    searchQuery: String,
    collapsedIds: Set<Long>,
    sortMode: Int,
    mangaCounts: Map<Long, Int>,
): List<CategoryManagerRow> {
    val query = searchQuery.trim()
    val byParent = categories.groupBy(Category::parentId)
    val roots = sortCategories(byParent[Category.UNCATEGORIZED_ID].orEmpty(), sortMode, mangaCounts)
    return buildList {
        roots.forEach { parent ->
            val children = sortCategories(byParent[parent.id].orEmpty(), sortMode, mangaCounts)
            if (query.isEmpty()) {
                val collapsed = parent.id in collapsedIds && children.isNotEmpty()
                add(CategoryManagerRow(parent, true, mangaCounts[parent.id] ?: 0, collapsed))
                if (!collapsed) children.forEach { add(CategoryManagerRow(it, false, mangaCounts[it.id] ?: 0)) }
            } else {
                val parentMatches = parent.name.contains(query, ignoreCase = true)
                val matchingChildren = children.filter { it.name.contains(query, ignoreCase = true) }
                if (parentMatches || matchingChildren.isNotEmpty()) {
                    add(CategoryManagerRow(parent, true, mangaCounts[parent.id] ?: 0))
                    (if (parentMatches) children else matchingChildren).forEach {
                        add(CategoryManagerRow(it, false, mangaCounts[it.id] ?: 0))
                    }
                }
            }
        }
    }
}

private fun sortCategories(
    categories: List<Category>,
    sortMode: Int,
    mangaCounts: Map<Long, Int>,
): List<Category> = when (sortMode) {
    CategoryManagerSort.AZ -> categories.sortedBy { it.name.lowercase() }
    CategoryManagerSort.ZA -> categories.sortedByDescending { it.name.lowercase() }
    CategoryManagerSort.MOST_ITEMS -> categories.sortedByDescending { mangaCounts[it.id] ?: 0 }
    CategoryManagerSort.LEAST_ITEMS -> categories.sortedBy { mangaCounts[it.id] ?: 0 }
    CategoryManagerSort.DATE_ADDED_NEWEST -> categories.sortedByDescending(Category::dateAdded)
    CategoryManagerSort.DATE_ADDED_OLDEST -> categories.sortedBy(Category::dateAdded)
    CategoryManagerSort.DATE_MODIFIED_NEWEST -> categories.sortedByDescending(Category::dateModified)
    CategoryManagerSort.DATE_MODIFIED_OLDEST -> categories.sortedBy(Category::dateModified)
    else -> categories.sortedBy(Category::order)
}

private fun parentAt(rows: List<CategoryManagerRow>, index: Int): Long? =
    rows.take(index + 1).lastOrNull(CategoryManagerRow::isTopLevel)?.category?.id

private val Category.key inline get() = "category-$id"
// KMK <--
