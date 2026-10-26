package eu.kanade.tachiyomi.ui.browse.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.FeedAddDialog
import eu.kanade.presentation.browse.FeedAddSearchDialog
import eu.kanade.presentation.browse.FeedOrderScreen
import eu.kanade.presentation.browse.FeedScreen
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.browse.components.FeedActionsDialog
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.browse.components.bulkSelectionButton
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteViewModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.SwapVert
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.feedTab(
    // KMK -->
    viewModel: FeedViewModel,
    bulkFavoriteViewModel: BulkFavoriteViewModel,
    // KMK <--
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by viewModel.state.collectAsState()

    // KMK -->
    val bulkFavoriteState by bulkFavoriteViewModel.state.collectAsState()
    val showingFeedOrderScreen = rememberSaveable { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(bulkFavoriteState.selectionMode) {
        HomeScreen.showBottomNav(!bulkFavoriteState.selectionMode)
    }
    // KMK <--

    return TabContent(
        titleRes = SYMR.strings.feed,
        actions =
        // KMK -->
        if (showingFeedOrderScreen.value) {
            listOf(
                AppBar.Action(
                    title = stringResource(KMR.strings.action_sort_feed),
                    icon = MaterialSymbols.Rounded.Close,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { showingFeedOrderScreen.value = false },
                ),
            )
        } else {
            // KMK <--
            listOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_add),
                    icon = MaterialSymbols.Rounded.Add,
                    onClick = {
                        viewModel.openAddDialog()
                    },
                ),
                // KMK -->
                AppBar.Action(
                    title = stringResource(KMR.strings.action_sort_feed),
                    icon = MaterialSymbols.Rounded.SwapVert,
                    onClick = { showingFeedOrderScreen.value = true },
                ),
                bulkSelectionButton(
                    isRunning = bulkFavoriteState.isRunning,
                    toggleSelectionMode = bulkFavoriteViewModel::toggleSelectionMode,
                ),
                // KMK <--
            )
        },
        content = { contentPadding, snackbarHostState ->
            // KMK -->
            BackHandler(enabled = bulkFavoriteState.selectionMode || showingFeedOrderScreen.value) {
                when {
                    bulkFavoriteState.selectionMode -> bulkFavoriteViewModel.backHandler()
                    showingFeedOrderScreen.value -> showingFeedOrderScreen.value = false
                }
            }
            Crossfade(
                targetState = showingFeedOrderScreen.value,
                label = "feed_order_crossfade",
            ) { showingFeedOrderScreen ->
                if (showingFeedOrderScreen) {
                    FeedOrderScreen(
                        state = state,
                        onClickDelete = viewModel::openDeleteDialog,
                        onChangeOrder = viewModel::changeOrder,
                    )
                } else {
                    // KMK <--
                    FeedScreen(
                        state = state,
                        contentPadding = contentPadding,
                        onClickSavedSearch = { savedSearch, source ->
                            viewModel.sourcePreferences.lastUsedSource.set(savedSearch.source)
                            navigator.push(
                                BrowseSourceScreen(
                                    source.id,
                                    listingQuery = null,
                                    savedSearch = savedSearch.id,
                                ),
                            )
                        },
                        onClickSource = { source ->
                            viewModel.sourcePreferences.lastUsedSource.set(source.id)
                            navigator.push(
                                BrowseSourceScreen(
                                    source.id,
                                    // KMK -->
                                    listingQuery = if (!source.supportsLatest) {
                                        GetRemoteManga.QUERY_POPULAR
                                    } else {
                                        // KMK <--
                                        GetRemoteManga.QUERY_LATEST
                                    },
                                ),
                            )
                        },
                        // KMK -->
                        onLongClickFeed = viewModel::openActionsDialog,
                        // KMK <--
                        onClickManga = { manga ->
                            // KMK -->
                            if (bulkFavoriteState.selectionMode) {
                                bulkFavoriteViewModel.toggleSelection(manga)
                            } else {
                                // KMK <--
                                navigator.push(MangaScreen(manga.id, true))
                            }
                        },
                        // KMK -->
                        onLongClickManga = { manga ->
                            if (!bulkFavoriteState.selectionMode) {
                                bulkFavoriteViewModel.addRemoveManga(manga, haptic)
                            } else {
                                navigator.push(MangaScreen(manga.id, true))
                            }
                        },
                        selection = bulkFavoriteState.selection,
                        // KMK <--
                        onRefresh = viewModel::init,
                        getMangaState = { manga -> viewModel.getManga(initialManga = manga) },
                    )
                }
            }

            state.dialog?.let { dialog ->
                val onDismissRequest = viewModel::dismissDialog
                when (dialog) {
                    is FeedViewModel.Dialog.AddFeed -> {
                        FeedAddDialog(
                            sources = dialog.options,
                            onDismiss = onDismissRequest,
                            onClickAdd = {
                                if (it != null) {
                                    viewModel.openAddSearchDialog(it)
                                }
                                onDismissRequest()
                            },
                        )
                    }
                    is FeedViewModel.Dialog.AddFeedSearch -> {
                        FeedAddSearchDialog(
                            source = dialog.source,
                            savedSearches = dialog.options,
                            onDismiss = onDismissRequest,
                            onClickAdd = { source, savedSearch ->
                                viewModel.createFeed(source, savedSearch)
                                onDismissRequest()
                            },
                        )
                    }
                    is FeedViewModel.Dialog.DeleteFeed -> {
                        SourceFeedDeleteDialog(
                            onDismissRequest = onDismissRequest,
                            deleteFeed = {
                                viewModel.deleteFeed(dialog.feed)
                                onDismissRequest()
                            },
                        )
                    }
                    // KMK -->
                    is FeedViewModel.Dialog.FeedActions -> {
                        FeedActionsDialog(
                            feed = dialog.feedItem.feed,
                            title = dialog.feedItem.title,
                            onDismissRequest = onDismissRequest,
                            onClickDelete = { viewModel.openDeleteDialog(it) },
                        )
                    }
                    // KMK <--
                }
            }

            // KMK -->
            BulkFavoriteDialogs(
                bulkFavoriteViewModel = bulkFavoriteViewModel,
                dialog = bulkFavoriteState.dialog,
            )
            // KMK <--

            val internalErrString = stringResource(MR.strings.internal_error)
            val tooManyFeedsString = stringResource(KMR.strings.too_many_in_feed)
            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        FeedViewModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                        FeedViewModel.Event.TooManyFeeds -> {
                            launch { snackbarHostState.showSnackbar(tooManyFeedsString) }
                        }
                    }
                }
            }
        },
    )
}
