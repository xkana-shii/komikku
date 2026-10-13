package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import eu.kanade.presentation.components.AppBar
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

@Composable
fun CategoryScreen(
    state: CategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    // KMK -->
    onClickHide: (Category) -> Unit,
    onClickTrackStatus: (Category) -> Unit,
    onClickUpdateInterval: (Category) -> Unit,
    onClickCreateSubcategory: (Category) -> Unit,
    onClickMove: (Category) -> Unit,
    // KMK -->
    onSearchQuery: (String) -> Unit = {},
    onToggleCollapsed: (Long) -> Unit = {},
    onExpandAll: () -> Unit = {},
    onCollapseAll: () -> Unit = {},
    onSortMode: (Int) -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onToggleSelectionMode: (Boolean) -> Unit = {},
    onBulkHide: (Boolean) -> Unit = {},
    onBulkDelete: () -> Unit = {},
    // KMK <--
    // KMK <--
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.action_edit_categories),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
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
            screenState = state,
            categories = state.categories,
            lazyListState = lazyListState,
            paddingValues = paddingValues,
            onClickRename = onClickRename,
            onClickDelete = onClickDelete,
            onChangeOrder = onChangeOrder,
            // KMK -->
            onClickHide = onClickHide,
            onClickTrackStatus = onClickTrackStatus,
            onClickUpdateInterval = onClickUpdateInterval,
            onClickCreateSubcategory = onClickCreateSubcategory,
            onClickMove = onClickMove,
            // KMK -->
            onSearchQuery = onSearchQuery,
            onToggleCollapsed = onToggleCollapsed,
            onExpandAll = onExpandAll,
            onCollapseAll = onCollapseAll,
            onSortMode = onSortMode,
            onToggleSelection = onToggleSelection,
            onToggleSelectionMode = onToggleSelectionMode,
            onBulkHide = onBulkHide,
            onBulkDelete = onBulkDelete,
            // KMK <--
            // KMK <--
        )
    }
}

@Composable
private fun CategoryContent(
    screenState: CategoryScreenState.Success,
    categories: ImmutableList<Category>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
    // KMK -->
    onClickHide: (Category) -> Unit,
    onClickTrackStatus: (Category) -> Unit,
    onClickUpdateInterval: (Category) -> Unit,
    onClickCreateSubcategory: (Category) -> Unit,
    onClickMove: (Category) -> Unit,
    // KMK -->
    onSearchQuery: (String) -> Unit,
    onToggleCollapsed: (Long) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onSortMode: (Int) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    onBulkHide: (Boolean) -> Unit,
    onBulkDelete: () -> Unit,
    // KMK <--
    // KMK <--
) {
    val categoriesState = remember { categories.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        // KMK --> Filtered and nested rows do not share the flat database index. Resolve the
        // source item defensively and let the flow refresh the canonical order after the write.
        categoriesState.getOrNull(from.index)?.let { onChangeOrder(it, to.index) }
        // KMK <--
    }

    LaunchedEffect(categories) {
        if (!reorderableState.isAnyItemDragging) {
            categoriesState.clear()
            categoriesState.addAll(categories)
        }
    }

    val byParent = categories.groupBy { it.parentId }
    val filteredRows = buildList {
        val query = screenState.searchQuery
        fun appendChildren(parentId: Long, depth: Int) {
            byParent[parentId].orEmpty().sortedWith(
                when (screenState.sortMode) {
                    1 -> compareBy { it.name.lowercase() }
                    2 -> compareByDescending { it.name.lowercase() }
                    3 -> compareByDescending { screenState.mangaCounts[it.id] ?: 0 }
                    4 -> compareBy { screenState.mangaCounts[it.id] ?: 0 }
                    5 -> compareByDescending { it.dateAdded }
                    6 -> compareBy { it.dateAdded }
                    7 -> compareByDescending { it.dateModified }
                    8 -> compareBy { it.dateModified }
                    else -> compareBy { it.order }
                },
            ).forEach { category ->
                if (query.isBlank() || category.name.contains(query, ignoreCase = true)) add(category to depth)
                if (category.id !in screenState.collapsedIds || query.isNotBlank()) appendChildren(category.id, depth + 1)
            }
        }
        appendChildren(0L, 0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium)) {
            OutlinedTextField(
                value = screenState.searchQuery,
                onValueChange = onSearchQuery,
                label = { Text(stringResource(KMR.strings.category_manager_search)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(onClick = { onToggleSelectionMode(!screenState.selectionMode) }) { Text(stringResource(KMR.strings.category_manager_select)) }
        }
        Row(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium)) {
            TextButton(onClick = onExpandAll) { Text(stringResource(KMR.strings.category_manager_expand_all)) }
            TextButton(onClick = onCollapseAll) { Text(stringResource(KMR.strings.category_manager_collapse_all)) }
            TextButton(onClick = { onSortMode((screenState.sortMode + 1) % 9) }) { Text(stringResource(KMR.strings.category_manager_sort)) }
            if (screenState.selectedIds.isNotEmpty()) {
                TextButton(onClick = { onBulkHide(true) }) { Text(stringResource(KMR.strings.category_manager_hide)) }
                TextButton(onClick = { onBulkHide(false) }) { Text(stringResource(KMR.strings.category_manager_show)) }
                TextButton(onClick = onBulkDelete) { Text(stringResource(MR.strings.action_delete)) }
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
            items(
                items = filteredRows,
                key = { (category, _) -> category.key },
            ) { (category, depth) ->
                ReorderableItem(reorderableState, category.key) {
                    CategoryListItem(
                        modifier = Modifier.animateItem(),
                        category = category,
                        onRename = { onClickRename(category) },
                        onDelete = { onClickDelete(category) },
                        // KMK -->
                        onHide = { onClickHide(category) },
                        onTrackStatus = { onClickTrackStatus(category) },
                        onUpdateInterval = { onClickUpdateInterval(category) },
                        onCreateSubcategory = { onClickCreateSubcategory(category) },
                        onMove = { onClickMove(category) },
                        // KMK -->
                        depth = depth,
                        onToggleExpanded = { onToggleCollapsed(category.id) },
                        hasChildren = byParent[category.id].orEmpty().isNotEmpty(),
                        expanded = category.id !in screenState.collapsedIds,
                        selectable = screenState.selectionMode,
                        selected = category.id in screenState.selectedIds,
                        onToggleSelected = { onToggleSelection(category.id) },
                        mangaCount = screenState.mangaCounts[category.id] ?: 0,
                        // KMK <--
                        // KMK <--
                    )
                }
            }
        }
    }
}

private val Category.key inline get() = "category-$id"
