package eu.kanade.presentation.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.LocalDate
import kotlin.math.min

@Composable
fun HistoryScreen(
    state: HistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (chapter: Chapter?) -> Unit,
    onClickExpand: (historyItem: HistoryWithRelations) -> Unit,
    onClickFavorite: (mangaId: Long) -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
) {
    // KMK -->
    val usePanoramaCover = remember { mutableStateOf(false) }
    // KMK <--
    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
                searchQuery = state.searchQuery,
                onChangeSearchQuery = onSearchQueryChange,
                actions = {
                    AppBarActions(
                        // KMK -->
                        persistentListOf<AppBar.AppBarAction>().builder()
                            .apply {
                                if (!state.list.isNullOrEmpty()) {
                                    add(
                                        AppBar.Action(
                                            title = stringResource(KMR.strings.action_panorama_cover),
                                            icon = Icons.Outlined.Panorama,
                                            iconTint = MaterialTheme.colorScheme.primary.takeIf { usePanoramaCover.value },
                                            onClick = {
                                                usePanoramaCover.value = !usePanoramaCover.value
                                            },
                                        ),
                                    )
                                }
                                add(
                                    // KMK <--
                                    AppBar.Action(
                                        title = stringResource(MR.strings.pref_clear_history),
                                        icon = Icons.Outlined.DeleteSweep,
                                        onClick = {
                                            onDialogChange(HistoryScreenModel.Dialog.DeleteAll)
                                        },
                                    ),
                                    // KMK -->
                                )
                            }
                            .build(),
                        // KMK <--
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.list.let {
            if (it == null) {
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
                HistoryScreenContent(
                    state = state,
                    history = it as ImmutableList<HistoryUiModel>,
                    contentPadding = contentPadding,
                    onClickCover = { history -> onClickCover(history.mangaId) },
                    onClickResume = { history -> onClickResume(history.chapter) },
                    onClickExpand = { history -> onClickExpand(history) },
                    onClickDelete = { item -> onDialogChange(HistoryScreenModel.Dialog.Delete(item)) },
                    onClickFavorite = { history -> onClickFavorite(history.mangaId) },
                    // KMK -->
                    usePanoramaCover = usePanoramaCover.value,
                    // KMK <--
                )
            }
        }
    }
}

@Composable
private fun HistoryScreenContent(
    state: HistoryScreenModel.State,
    history: ImmutableList<HistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickExpand: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
    // KMK -->
    usePanoramaCover: Boolean,
    // KMK <--
) {
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
                    ListGroupHeader(
                        modifier = Modifier.animateItemFastScroll(),
                        text = relativeDateText(item.date),
                    )
                }

                is HistoryUiModel.Item -> {
                    val mainItem = item.item
                    val prevHistory = item.previousHistory
                    val expanded = state.expandedStates[mainItem.mangaId] == true

                    Column {
                        HistoryItem(
                            modifier = Modifier.animateItemFastScroll(),
                            history = mainItem,
                            expanded = expanded,
                            onClickCover = { onClickCover(mainItem) },
                            onClickExpand = { onClickExpand(mainItem) },
                            onClickResume = { onClickResume(mainItem) },
                            onClickDelete = { onClickDelete(mainItem) },
                            onClickFavorite = { onClickFavorite(mainItem) },
                            // KMK -->
                            usePanoramaCover = usePanoramaCover,
                            // KMK <--
                        )

                        AnimatedVisibility(
                            visible = expanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            if (prevHistory == null) return@AnimatedVisibility
                            val itemsCount = prevHistory.size
                            val showMoreState = remember { mutableStateOf(false) }

                            LazyColumn(
                                modifier = Modifier.height((60 * min(14, itemsCount) + if (itemsCount > 14) 70 else 0).dp),
                            ) {
                                val splitIndex = if (itemsCount > 14 && !showMoreState.value) 7 else itemsCount
                                val firstPart = prevHistory.take(splitIndex)
                                val secondPart = prevHistory.takeLast(if (splitIndex == itemsCount) 0 else splitIndex)

                                // Add a null item to separate the two lists
                                items(firstPart + listOf(null) + secondPart) { previous ->
                                    if (previous == null) {
                                        if (itemsCount > 14) {
                                            Box(
                                                contentAlignment = Alignment.CenterStart,
                                                modifier = Modifier
                                                    .clickable { showMoreState.value = !showMoreState.value }
                                                    .height(70.dp)
                                                    .fillMaxSize(),
                                            ) {
                                                Text(
                                                    text = if (showMoreState.value) {
                                                        stringResource(KMR.strings.show_less)
                                                    } else {
                                                        stringResource(
                                                            KMR.strings.show_n_more_chapters,
                                                            itemsCount - splitIndex * 2,
                                                        )
                                                    },
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(
                                                        horizontal = 60.dp,
                                                        vertical = 20.dp,
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        HistoryItem(
                                            modifier = Modifier.animateItemFastScroll(),
                                            history = previous,
                                            isPreviousHistory = true,
                                            expanded = false,
                                            onClickCover = { onClickCover(previous) },
                                            onClickExpand = { onClickExpand(previous) },
                                            onClickResume = { onClickResume(previous) },
                                            onClickDelete = { onClickDelete(previous) },
                                            onClickFavorite = { onClickFavorite(previous) },
                                            // KMK -->
                                            usePanoramaCover = usePanoramaCover,
                                            // KMK <--
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed interface HistoryUiModel {
    data class Header(val date: LocalDate) : HistoryUiModel
    data class Item(
        val item: HistoryWithRelations,
        val previousHistory: ImmutableList<HistoryWithRelations>? = null,
    ) : HistoryUiModel
}

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(HistoryScreenModelStateProvider::class)
    historyState: HistoryScreenModel.State,
) {
    TachiyomiPreviewTheme {
        HistoryScreen(
            state = historyState,
            snackbarHostState = SnackbarHostState(),
            onSearchQueryChange = {},
            onClickCover = {},
            onClickResume = { _ -> run {} },
            onClickExpand = {},
            onDialogChange = {},
            onClickFavorite = {},
        )
    }
}
