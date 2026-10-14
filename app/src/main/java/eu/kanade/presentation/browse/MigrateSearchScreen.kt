package eu.kanade.presentation.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.kanade.presentation.browse.components.GlobalSearchToolbar
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteViewModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun MigrateSearchScreen(
    state: SearchViewModel.State,
    fromSourceId: Long?,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    // KMK -->
    bulkFavoriteViewModel: BulkFavoriteViewModel,
    hasPinnedSources: Boolean,
    // KMK <--
) {
    // KMK -->
    val bulkFavoriteState by bulkFavoriteViewModel.state.collectAsState()
    // KMK <--

    Scaffold(
        topBar = { scrollBehavior ->
            // KMK -->
            if (bulkFavoriteState.selectionMode) {
                BulkSelectionToolbar(
                    selectedCount = bulkFavoriteState.selection.size,
                    isRunning = bulkFavoriteState.isRunning,
                    onClickClearSelection = bulkFavoriteViewModel::toggleSelectionMode,
                    onChangeCategoryClick = bulkFavoriteViewModel::addFavorite,
                    onSelectAll = {
                        state.filteredItems.values
                            .filterIsInstance<SearchItemResult.Success>()
                            .flatMap { it.result }
                            .forEach { bulkFavoriteViewModel.select(it) }
                    },
                    onReverseSelection = {
                        state.filteredItems.values
                            .filterIsInstance<SearchItemResult.Success>()
                            .flatMap { it.result }
                            .let { bulkFavoriteViewModel.reverseSelection(it) }
                    },
                )
            } else {
                // KMK <--
                GlobalSearchToolbar(
                    searchQuery = state.searchQuery,
                    progress = state.progress,
                    total = state.total,
                    navigateUp = navigateUp,
                    onChangeSearchQuery = onChangeSearchQuery,
                    onSearch = onSearch,
                    hideSourceFilter = true,
                    sourceFilter = state.sourceFilter,
                    onChangeSearchFilter = onChangeSearchFilter,
                    onlyShowHasResults = state.onlyShowHasResults,
                    onToggleResults = onToggleResults,
                    scrollBehavior = scrollBehavior,
                    // KMK -->
                    toggleSelectionMode = bulkFavoriteViewModel::toggleSelectionMode,
                    isRunning = bulkFavoriteState.isRunning,
                    hasPinnedSources = hasPinnedSources,
                    // KMK <--
                )
            }
        },
    ) { paddingValues ->
        GlobalSearchContent(
            fromSourceId = fromSourceId,
            items = state.filteredItems,
            contentPadding = paddingValues,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            // KMK -->
            selection = bulkFavoriteState.selection,
            // KMK <--
        )
    }
}
