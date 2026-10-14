package exh.recs

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteViewModel
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.recs.batch.RankedSearchResults
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold
import java.io.Serializable

class BrowseRecommendsScreen(
    private val args: Args,
    private val isExternalSource: Boolean,
) : Screen() {

    sealed interface Args : Serializable {
        data class SingleSourceManga(
            val mangaId: Long,
            val sourceId: Long,
            val recommendationSourceName: String,
        ) : Args
        data class MergedSourceMangas(val results: RankedSearchResults) : Args
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = assistedMetroViewModel<BrowseRecommendsViewModel, BrowseRecommendsViewModel.Factory> {
            create(args = args)
        }

        // KMK -->
        val bulkFavoriteViewModel = metroViewModel<BulkFavoriteViewModel>()
        val bulkFavoriteState by bulkFavoriteViewModel.state.collectAsState()

        val haptic = LocalHapticFeedback.current

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteViewModel.backHandler()
        }
        // KMK <--

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickItem = { manga: Manga ->
            navigator.push(
                when (isExternalSource) {
                    true -> SourcesScreen(SourcesScreen.SmartSearchConfig(manga.ogTitle))
                    false -> MangaScreen(manga.id, true)
                },
            )
        }

        // KMK -->
        val onLongClickItem = { manga: Manga ->
            when (isExternalSource) {
                true -> WebViewActivity.newIntent(context, manga.url, title = manga.title).let(context::startActivity)
                false -> navigator.push(MangaScreen(manga.id, true))
            }
        }

        val mangaList = viewModel.mangaPagerFlowFlow.collectAsLazyPagingItems()
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
                            mangaList.itemSnapshotList.items
                                .map { it.value.first }
                                .forEach { bulkFavoriteViewModel.select(it) }
                        },
                        onReverseSelection = {
                            mangaList.itemSnapshotList.items
                                .map { it.value.first }
                                .let { bulkFavoriteViewModel.reverseSelection(it) }
                        },
                    )
                } else {
                    // KMK <--
                    val title = remember {
                        val recSource = viewModel.recommendationPagingSource
                        "${recSource.name} (${recSource.category.getString(context)})"
                    }

                    BrowseSourceSimpleToolbar(
                        navigateUp = navigator::pop,
                        title = title,
                        displayMode = viewModel.displayMode,
                        onDisplayModeChange = { viewModel.displayMode = it },
                        scrollBehavior = scrollBehavior,
                        // KMK -->
                        toggleSelectionMode = {
                            bulkFavoriteViewModel.toggleSelectionMode(true)
                        }.takeIf { !isExternalSource },
                        isRunning = bulkFavoriteState.isRunning,
                        // KMK <--
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseSourceContent(
                source = viewModel.source,
                mangaList = mangaList,
                columns = viewModel.getColumnsPreference(LocalConfiguration.current.orientation),
                // SY -->
                ehentaiBrowseDisplayMode = false,
                // SY <--
                displayMode = viewModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = null,
                onHelpClick = null,
                onLocalSourceHelpClick = null,
                onMangaClick = { manga ->
                    // KMK -->
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteViewModel.toggleSelection(manga)
                    } else {
                        // KMK <--
                        onClickItem(manga)
                    }
                },
                onMangaLongClick = { manga ->
                    // KMK -->
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteViewModel.addRemoveManga(manga, haptic)
                    } else {
                        onLongClickItem(manga)
                    }
                    // KMK <--
                },
                // KMK -->
                selection = bulkFavoriteState.selection,
                // KMK <--
            )
        }

        // KMK -->
        BulkFavoriteDialogs(
            bulkFavoriteViewModel = bulkFavoriteViewModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }
}
