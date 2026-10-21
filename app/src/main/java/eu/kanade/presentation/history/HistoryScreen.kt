package eu.kanade.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.rememberIs24HourSystem
import eu.kanade.tachiyomi.ui.history.HistoryViewModel
import eu.kanade.tachiyomi.ui.history.HistoryViewModel.HistorySelectionOptions
import kotlinx.datetime.LocalDate
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Checklist
import mihon.icons.materialsymbols.rounded.DeleteSweep
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.SelectAll
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import mihon.feature.upcoming.DateHeading
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun HistoryScreen(
    state: HistoryViewModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (mangaId: Long, chapterId: Long) -> Unit,
    onClickFavorite: (mangaId: Long) -> Unit,
    onDialogChange: (HistoryViewModel.Dialog?) -> Unit,
    // KMK -->
    toggleSelectionMode: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onHistorySelected: (HistoryWithRelations, HistorySelectionOptions) -> Unit,
    onFilterClicked: () -> Unit,
    hasActiveFilters: Boolean,
    usePanoramaCover: Boolean,
    // KMK <--
) {
    // KMK -->
    BackHandler(enabled = state.selectionMode, onBack = toggleSelectionMode)
    // KMK <--

    Scaffold(
        topBar = { scrollBehavior ->
            // KMK -->
            when {
                state.selectionMode -> HistorySelectionToolbar(
                    selectedCount = state.selection.size,
                    onCancelActionMode = toggleSelectionMode,
                    onClickSelectAll = { onSelectAll(true) },
                    onClickInvertSelection = onInvertSelection,
                    onClickClearHistory = { onDialogChange(HistoryViewModel.Dialog.Delete(state.selected)) },
                )
                // KMK <--
                else -> SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = onSearchQueryChange,
                    actions = {
                        AppBarActions(
                            listOf(
                                // KMK -->
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_filter),
                                    icon = MaterialSymbols.Rounded.FilterList,
                                    iconTint = if (hasActiveFilters) MaterialTheme.colorScheme.active else LocalContentColor.current,
                                    onClick = onFilterClicked,
                                ),
                                // KMK <--
                                AppBar.Action(
                                    title = stringResource(MR.strings.pref_clear_history),
                                    // KMK -->
                                    icon = MaterialSymbols.Rounded.Checklist,
                                    onClick = toggleSelectionMode,
                                    // KMK <--
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.list.let {
            // KMK -->
            if (state.isLoading) {
                // KMK <--
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                val msg = if (!state.searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.information_no_recent_manga
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                // KMK -->
                val uiModels = remember(state.list) { state.getUiModel() }
                // KMK <--
                HistoryScreenContent(
                    // KMK -->
                    state = state,
                    history = uiModels,
                    // KMK <--
                    contentPadding = contentPadding,
                    onClickCover = { history -> onClickCover(history.mangaId) },
                    onClickResume = { history -> onClickResume(history.mangaId, history.chapterId) },
                    onClickDelete = { item -> onDialogChange(HistoryViewModel.Dialog.Delete(item)) },
                    onClickFavorite = { history -> onClickFavorite(history.mangaId) },
                    // KMK -->
                    selectionMode = state.selectionMode,
                    onHistorySelected = onHistorySelected,
                    usePanoramaCover = usePanoramaCover,
                    // KMK <--
                )
            }
        }
    }
}

@Composable
private fun HistoryScreenContent(
    // KMK -->
    state: HistoryViewModel.State,

    history: List<HistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
    // KMK -->
    selectionMode: Boolean,
    onHistorySelected: (HistoryWithRelations, HistorySelectionOptions) -> Unit,
    usePanoramaCover: Boolean,
    // KMK <--
) {
    val context = LocalContext.current
    val locale = LocalLocale.current
    val is24HourSystem = rememberIs24HourSystem()
    val timeFormat by remember { context.appGraph.uiPreferences.timeFormat }.collectAsState()
    val timeFormatter = remember(timeFormat, locale, is24HourSystem) {
        UiPreferences.timeFormat(timeFormat, locale.platformLocale, is24HourSystem)
    }

    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = history,
            key = { "history-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is HistoryUiModel.Header -> "header"
                    is HistoryUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is HistoryUiModel.Header -> {
                    DateHeading(
                        modifier = Modifier.animateItemFastScroll()
                            .padding(top = MaterialTheme.padding.extraSmall),
                        date = item.date,
                        mangaCount = item.mangaCount,
                    )
                }
                is HistoryUiModel.Item -> {
                    val value = item.item
                    // KMK -->
                    val isSelected = remember(state.selection) { value.chapterId in state.selection }
                    // KMK <--
                    HistoryItem(
                        modifier = Modifier.animateItemFastScroll(),
                        history = value,
                        timeFormatter = timeFormatter,
                        onClickCover = { onClickCover(value) },
                        // KMK -->
                        onClick = {
                            when {
                                selectionMode -> onHistorySelected(
                                    item.item,
                                    HistorySelectionOptions(
                                        selected = !isSelected,
                                        fromLongPress = false,
                                    ),
                                )
                                else -> onClickResume(value)
                            }
                        },
                        onLongClick = {
                            onHistorySelected(
                                item.item,
                                HistorySelectionOptions(
                                    selected = !isSelected,
                                    fromLongPress = true,
                                ),
                            )
                        },
                        // KMK <--
                        onClickDelete = { onClickDelete(value) },
                        onClickFavorite = { onClickFavorite(value) },
                        // KMK -->
                        selected = isSelected,
                        readProgress = value.lastPageRead
                            .takeIf { !value.read && it > 0L }
                            ?.let {
                                stringResource(
                                    MR.strings.chapter_progress,
                                    it + 1,
                                )
                            },
                        hasUnread = value.unreadCount > 0,
                        usePanoramaCover = usePanoramaCover,
                        // KMK <--
                    )
                }
            }
        }
    }
}

sealed interface HistoryUiModel {
    data class Header(
        val date: LocalDate,
        // KNS -->
        /** Different manga read on [date]. */
        val mangaCount: Int = 0,
        // KNS <--
    ) : HistoryUiModel
    // KMK -->
    data class Item(val item: HistoryWithRelations) : HistoryUiModel
    // KMK <--
}

// KMK -->
@Composable
private fun HistorySelectionToolbar(
    selectedCount: Int,
    onCancelActionMode: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickClearHistory: () -> Unit,
) {
    AppBar(
        titleContent = { Text(text = "$selectedCount") },
        actions = {
            AppBarActions(
                listOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = MaterialSymbols.Rounded.SelectAll,
                        onClick = onClickSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = MaterialSymbols.Rounded.FlipToBack,
                        onClick = onClickInvertSelection,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.pref_clear_history),
                        icon = MaterialSymbols.Rounded.DeleteSweep,
                        onClick = onClickClearHistory,
                        enabled = selectedCount > 0,
                    ),
                ),
            )
        },
        isActionMode = true,
        onCancelActionMode = onCancelActionMode,
    )
}
// KMK <--

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(HistoryviewModelStateProvider::class)
    historyState: HistoryViewModel.State,
) {
    TachiyomiPreviewTheme {
        HistoryScreen(
            state = historyState,
            snackbarHostState = SnackbarHostState(),
            onSearchQueryChange = {},
            onClickCover = {},
            onClickResume = { _, _ -> run {} },
            onDialogChange = {},
            onClickFavorite = {},
            // KMK -->
            toggleSelectionMode = {},
            onSelectAll = {},
            onInvertSelection = {},
            onHistorySelected = { _, _ -> },
            onFilterClicked = {},
            hasActiveFilters = true,
            usePanoramaCover = true,
            // KMK <--
        )
    }
}
