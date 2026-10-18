package eu.kanade.tachiyomi.ui.browse.migration.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.BaseMangaListItem
import eu.kanade.presentation.manga.components.Button
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FindReplace
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.SelectAll
import mihon.icons.materialsymbols.rounded.VerticalAlignBottom
import mihon.icons.materialsymbols.rounded.VerticalAlignTop
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.selectedBackground
import kotlin.time.Duration.Companion.seconds

data class MigrateMangaScreen(
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel =
            assistedMetroViewModel<MigrateMangaViewModel, MigrateMangaViewModel.Factory> { create(sourceId = sourceId) }

        val state by viewModel.state.collectAsStateWithLifecycle()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        BackHandler(enabled = state.selectionMode) {
            viewModel.clearSelection()
        }

        val lazyListState = rememberLazyListState()

        // KMK -->
        val scope = rememberCoroutineScope()
        val enableScrollToTop by remember {
            derivedStateOf {
                lazyListState.firstVisibleItemIndex > 0
            }
        }

        val enableScrollToBottom by remember {
            derivedStateOf {
                lazyListState.canScrollForward
            }
        }
        // KMK <--

        Scaffold(
            topBar = { scrollBehavior ->
                // KMK -->
                MigrateMangaAppBar(
                    // KMK <--
                    title = state.source!!.name,
                    navigateUp = {
                        // KMK -->
                        navigator.pop()
                    },
                    itemCnt = state.titles.size,
                    selectedCount = state.selection.size,
                    onClickUnselectAll = viewModel::clearSelection,
                    onClickSelectAll = viewModel::toggleAllSelection,
                    onClickInvertSelection = viewModel::invertSelection,
                    // KMK <--
                    scrollBehavior = scrollBehavior,
                )
            },
            // KMK -->
            bottomBar = {
                MigrateMangaBottomBar(
                    selectionMode = state.selectionMode,
                    onMultiMigrateClicked = {
                        navigator.push(MigrationConfigScreen(state.selection))
                    },
                    enableScrollToTop = enableScrollToTop,
                    enableScrollToBottom = enableScrollToBottom,
                    scrollToTop = {
                        scope.launch {
                            lazyListState.scrollToItem(0)
                        }
                    },
                    scrollToBottom = {
                        scope.launch {
                            lazyListState.scrollToItem(state.titles.size - 1)
                        }
                    },
                )
            },
            // KMK <--
        ) { contentPadding ->
            if (state.isEmpty) {
                EmptyScreen(
                    stringRes = MR.strings.empty_screen,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            MigrateMangaContent(
                lazyListState = lazyListState,
                contentPadding = contentPadding,
                state = state,
                // KMK -->
                onMangaSelected = viewModel::toggleSelection,
                onClickItem = { navigator.push(MigrationConfigScreen(it.id)) },
                // KMK <--
                onClickCover = { navigator.push(MangaScreen(it.id)) },
            )
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    MigrationMangaEvent.FailedFetchingFavorites -> {
                        context.toast(MR.strings.internal_error)
                    }
                }
            }
        }
    }

    @Composable
    private fun MigrateMangaContent(
        lazyListState: LazyListState,
        contentPadding: PaddingValues,
        state: MigrateMangaViewModel.State,
        // KMK -->
        onMangaSelected: (Manga, Boolean, Boolean) -> Unit,
        // KMK <--
        onClickItem: (Manga) -> Unit,
        onClickCover: (Manga) -> Unit,
    ) {
        FastScrollLazyColumn(
            state = lazyListState,
            contentPadding = contentPadding,
        ) {
            items(items = state.titles) { manga ->
                // KMK -->
                val isSelected = manga.id in state.selection
                // KMK <--
                MigrateMangaItem(
                    manga = manga,
                    isSelected = isSelected,
                    onClickItem = {
                        // KMK -->
                        when {
                            state.selectionMode -> onMangaSelected(manga, !isSelected, false)
                            // KMK <--
                            else -> onClickItem(it)
                        }
                    },
                    onClickCover = onClickCover,
                    // KMK -->
                    onLongClick = { onMangaSelected(manga, !isSelected, true) },
                    modifier = Modifier.animateItemFastScroll(),
                    // KMK <--
                )
            }
        }
    }

    @Composable
    private fun MigrateMangaItem(
        manga: Manga,
        isSelected: Boolean,
        onClickItem: (Manga) -> Unit,
        onClickCover: (Manga) -> Unit,
        // KMK -->
        onLongClick: () -> Unit,
        // KMK <--
        modifier: Modifier = Modifier,
    ) {
        BaseMangaListItem(
            modifier = modifier.selectedBackground(isSelected),
            manga = manga,
            onClickItem = { onClickItem(manga) },
            onClickCover = { onClickCover(manga) },
            // KMK -->
            onLongClick = onLongClick,
            // KMK <--
        )
    }

    // KMK -->
    @Composable
    private fun MigrateMangaAppBar(
        title: String,
        navigateUp: () -> Unit,
        itemCnt: Int,
        selectedCount: Int,
        onClickUnselectAll: () -> Unit,
        onClickSelectAll: () -> Unit,
        onClickInvertSelection: () -> Unit,
        scrollBehavior: TopAppBarScrollBehavior,
    ) {
        AppBar(
            title = title,
            navigateUp = navigateUp,
            actions = {
                if (itemCnt > 0) {
                    AppBarActions(
                        listOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_all),
                                icon = MaterialSymbols.Rounded.SelectAll,
                                onClick = onClickSelectAll,
                            ),
                        ),
                    )
                }
            },
            actionModeCounter = selectedCount,
            onCancelActionMode = onClickUnselectAll,
            actionModeActions = {
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
                    ),
                )
            },
            scrollBehavior = scrollBehavior,
        )
    }

    @Composable
    private fun MigrateMangaBottomBar(
        modifier: Modifier = Modifier,
        selectionMode: Boolean,
        onMultiMigrateClicked: () -> Unit,
        enableScrollToTop: Boolean,
        enableScrollToBottom: Boolean,
        scrollToTop: () -> Unit,
        scrollToBottom: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        val animatedElevation by animateDpAsState(
            targetValue = if (selectionMode) 3.dp else 0.dp,
            label = "elevation",
        )
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = ZeroCornerSize,
                bottomStart = ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                elevation = animatedElevation,
            ),
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm = remember { mutableStateListOf(false, false, false) }
            var resetJob by remember { mutableStateOf<Job?>(null) }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                confirm.indices.forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            Row(
                modifier = Modifier
                    .padding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Button(
                    title = stringResource(KMR.strings.action_scroll_to_top),
                    icon = MaterialSymbols.Rounded.VerticalAlignTop,
                    toConfirm = confirm[0],
                    onLongClick = { onLongClickItem(0) },
                    onClick = scrollToTop,
                    enabled = enableScrollToTop,
                )
                Button(
                    title = stringResource(MR.strings.migrate),
                    icon = MaterialSymbols.Rounded.FindReplace,
                    toConfirm = confirm[1],
                    onLongClick = { onLongClickItem(1) },
                    onClick = onMultiMigrateClicked,
                    enabled = selectionMode,
                )
                Button(
                    title = stringResource(KMR.strings.action_scroll_to_bottom),
                    icon = MaterialSymbols.Rounded.VerticalAlignBottom,
                    toConfirm = confirm[2],
                    onLongClick = { onLongClickItem(2) },
                    onClick = scrollToBottom,
                    enabled = enableScrollToBottom,
                )
            }
        }
    }
// KMK <--
}
