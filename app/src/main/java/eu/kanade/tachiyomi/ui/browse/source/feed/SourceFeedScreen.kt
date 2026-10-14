package eu.kanade.tachiyomi.ui.browse.source.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.MissingSourceScreen
import eu.kanade.presentation.browse.SourceFeedOrderScreen
import eu.kanade.presentation.browse.SourceFeedScreen
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.browse.components.FeedActionsDialog
import eu.kanade.presentation.browse.components.SourceFeedAddDialog
import eu.kanade.presentation.browse.components.SourceFeedDeleteDialog
import eu.kanade.presentation.more.settings.screen.SettingsEhScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteViewModel
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.md.follows.MangaDexFollowsScreen
import exh.source.anyIs
import exh.source.isEhBasedSource
import exh.util.nullIfBlank
import mihon.app.di.appGraph
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class SourceFeedScreen(val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val viewModel = assistedMetroViewModel<SourceFeedViewModel, SourceFeedViewModel.Factory> {
            create(sourceId = sourceId)
        }
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        // KMK -->
        val source = state.source
        if (source == null) {
            LoadingScreen()
            return
        }

        if (source is StubSource) {
            MissingSourceScreen(
                source = source,
                navigateUp = navigator::pop,
            )
            return
        }

        LaunchedEffect(navigator.lastItem) {
            // Reset filters & reload saved-searches when screen is navigated back to
            viewModel.resetFilters()
        }

        val bulkFavoriteViewModel = metroViewModel<BulkFavoriteViewModel>()
        val bulkFavoriteState by bulkFavoriteViewModel.state.collectAsState()
        val showingFeedOrderScreen = rememberSaveable { mutableStateOf(false) }

        val isHentaiEnabled: Boolean = LocalContext.current.appGraph.exhPreferences.isHentaiEnabled.get()
        val isConfigurableSource = source.anyIs<ConfigurableSource>() ||
            (source.isEhBasedSource() && isHentaiEnabled)

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode || showingFeedOrderScreen.value) {
            when {
                bulkFavoriteState.selectionMode -> bulkFavoriteViewModel.backHandler()
                showingFeedOrderScreen.value -> showingFeedOrderScreen.value = false
            }
        }
        Crossfade(
            targetState = showingFeedOrderScreen.value,
            label = "feed_order_crossfade",
        ) { targetState ->
            if (targetState) {
                SourceFeedOrderScreen(
                    state = state,
                    onClickDelete = viewModel::openDeleteFeed,
                    onChangeOrder = viewModel::changeOrder,
                    navigateUp = { showingFeedOrderScreen.value = false },
                )
            } else {
                // KMK <--
                SourceFeedScreen(
                    name = source.name,
                    isLoading = state.isLoading,
                    items = state.items,
                    hasFilters = state.filters.isNotEmpty(),
                    onFabClick = viewModel::openFilterSheet,
                    onClickBrowse = { onBrowseClick(navigator, source) },
                    onClickLatest = { onLatestClick(navigator, source) },
                    onClickSavedSearch = { onSavedSearchClick(navigator, source, it) },
                    // KMK -->
                    // onClickDelete = viewModel::openDeleteFeed,
                    onLongClickFeed = viewModel::openActionsDialog,
                    // KMK <--
                    onClickManga = { manga ->
                        // KMK -->
                        if (bulkFavoriteState.selectionMode) {
                            bulkFavoriteViewModel.toggleSelection(manga)
                        } else {
                            // KMK <--
                            onMangaClick(navigator, manga)
                        }
                    },
                    onClickSearch = { onSearchClick(navigator, source, it) },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = viewModel::search,
                    getMangaState = { viewModel.getManga(initialManga = it) },
                    // KMK -->
                    navigateUp = { navigator.pop() },
                    onWebViewClick = {
                        val httpSource = source as HttpSource
                        navigator.push(
                            WebViewScreen(
                                url = httpSource.baseUrl,
                                initialTitle = httpSource.name,
                                sourceId = httpSource.id,
                            ),
                        )
                    }.takeIf { source is HttpSource },
                    onToggleIncognito = viewModel::toggleIncognitoMode,
                    onSourceSettingClick = {
                        when {
                            source.isEhBasedSource() && isHentaiEnabled ->
                                navigator.push(SettingsEhScreen)
                            source.anyIs<ConfigurableSource>() ->
                                navigator.push(SourcePreferencesScreen(source.id))
                            else -> {}
                        }
                    }.takeIf { isConfigurableSource },
                    onSortFeedClick = { showingFeedOrderScreen.value = true }
                        .takeIf {
                            viewModel.state.value.items
                                .filterIsInstance<SourceFeedUI.SourceSavedSearch>()
                                .isNotEmpty()
                        },
                    onLongClickManga = { manga ->
                        if (!bulkFavoriteState.selectionMode) {
                            bulkFavoriteViewModel.addRemoveManga(manga, haptic)
                        } else {
                            navigator.push(MangaScreen(manga.id, true))
                        }
                    },
                    bulkFavoriteViewModel = bulkFavoriteViewModel,
                    // KMK <--
                )
            }
        }

        val onDismissRequest = viewModel::dismissDialog
        when (val dialog = state.dialog) {
            is SourceFeedViewModel.Dialog.AddFeed -> {
                SourceFeedAddDialog(
                    onDismissRequest = onDismissRequest,
                    name = dialog.name,
                    addFeed = {
                        viewModel.createFeed(dialog.feedId)
                        onDismissRequest()
                    },
                )
            }
            is SourceFeedViewModel.Dialog.DeleteFeed -> {
                SourceFeedDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    deleteFeed = {
                        viewModel.deleteFeed(dialog.feed)
                        onDismissRequest()
                    },
                )
            }
            // KMK -->
            is SourceFeedViewModel.Dialog.FeedActions -> {
                FeedActionsDialog(
                    feed = dialog.feedItem.feed,
                    title = dialog.feedItem.title,
                    onDismissRequest = viewModel::dismissDialog,
                    onClickDelete = { viewModel.openDeleteFeed(it) },
                )
            }
            // KMK <--
            is SourceFeedViewModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    // KMK -->
                    onReset = viewModel::resetFilters,
                    // KMK <--
                    onFilter = {
                        viewModel.onFilter { query, filters ->
                            onBrowseClick(
                                navigator = navigator,
                                sourceId = sourceId,
                                search = query,
                                filters = filters,
                            )
                        }
                    },
                    onUpdate = viewModel::setFilters,
                    startExpanded = viewModel.startExpanded,
                    onSave = {},
                    savedSearches = state.savedSearches,
                    onSavedSearch = { search ->
                        viewModel.onSavedSearch(
                            search,
                            onBrowseClick = { query, searchId ->
                                onBrowseClick(
                                    navigator = navigator,
                                    sourceId = sourceId,
                                    search = query,
                                    savedSearch = searchId,
                                )
                            },
                            onToast = {
                                context.toast(it)
                            },
                        )
                    },
                    onSavedSearchPress = { search ->
                        viewModel.onSavedSearchAddToFeed(search) {
                            context.toast(it)
                        }
                    },
                    // KMK -->
                    onSavedSearchPressDesc = stringResource(KMR.strings.saved_searches_add_feed),
                    shouldShowSavingButton = false,
                    // KMK <--
                    openMangaDexRandom = if (viewModel.sourceIsMangaDex) {
                        {
                            viewModel.onMangaDexRandom {
                                // KMK -->
                                // navigator.replace(
                                navigator.push(
                                    // KMK <--
                                    BrowseSourceScreen(
                                        sourceId,
                                        "id:$it",
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    openMangaDexFollows = if (viewModel.sourceIsMangaDex) {
                        {
                            // KMK -->
                            // navigator.replace(MangaDexFollowsScreen(sourceId))
                            navigator.push(MangaDexFollowsScreen(sourceId))
                            // KMK <--
                        }
                    } else {
                        null
                    },
                )
            }
            null -> Unit
        }

        // KMK -->
        BulkFavoriteDialogs(
            bulkFavoriteViewModel = bulkFavoriteViewModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }

    private fun onMangaClick(navigator: Navigator, manga: Manga) {
        navigator.push(MangaScreen(manga.id, true))
    }

    private fun onBrowseClick(navigator: Navigator, sourceId: Long, search: String? = null, savedSearch: Long? = null, filters: String? = null) {
        // KMK -->
        // navigator.replace(BrowseSourceScreen(sourceId, search, savedSearch = savedSearch, filtersJson = filters))
        navigator.push(BrowseSourceScreen(sourceId, search, savedSearch = savedSearch, filtersJson = filters))
        // KMK <--
    }

    private fun onLatestClick(navigator: Navigator, source: Source) {
        // KMK -->
        // navigator.replace(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_LATEST))
        navigator.push(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_LATEST))
        // KMK <--
    }

    private fun onBrowseClick(navigator: Navigator, source: Source) {
        // KMK -->
        // navigator.replace(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR))
        navigator.push(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR))
        // KMK <--
    }

    private fun onSavedSearchClick(navigator: Navigator, source: Source, savedSearch: SavedSearch) {
        // KMK -->
        // navigator.replace(BrowseSourceScreen(source.id, listingQuery = null, savedSearch = savedSearch.id))
        navigator.push(BrowseSourceScreen(source.id, listingQuery = null, savedSearch = savedSearch.id))
        // KMK <--
    }

    private fun onSearchClick(navigator: Navigator, source: Source, query: String) {
        onBrowseClick(navigator, source.id, query.nullIfBlank())
    }
}
