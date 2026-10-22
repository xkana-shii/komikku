package eu.kanade.tachiyomi.ui.updates

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.core.preference.asState
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.UpdatesFilterDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesViewModel.Event
import kotlinx.coroutines.flow.collectLatest
import mihon.app.di.appGraph
import mihon.feature.upcoming.UpcomingScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

data object UpdatesTab : Tab {
    @Suppress("unused")
    private fun readResolve(): Any = UpdatesTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.animated_updates)
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadQueueScreen)
    }

    // SY -->
    @Composable
    override fun isEnabled(): Boolean {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        return remember {
            context.appGraph.uiPreferences.showNavUpdates.asState(scope)
        }.value
    }
    // SY <--

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<UpdatesViewModel>()
        val settingsViewModel = metroViewModel<UpdatesSettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        // KMK -->
        val usePanoramaCover by settingsViewModel.updatesPreferences.usePanoramaCover.collectAsState()
        // KMK <--

        UpdateScreen(
            state = state,
            snackbarHostState = viewModel.snackbarHostState,
            lastUpdated = viewModel.lastUpdated,
            // SY -->
            preserveReadingPosition = viewModel.preserveReadingPosition,
            // SY <--
            onClickCover = { item -> navigator.push(MangaScreen(item.update.mangaId)) },
            onSelectAll = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
            onUpdateLibrary = viewModel::updateLibrary,
            onDownloadChapter = viewModel::downloadChapters,
            onMultiBookmarkClicked = viewModel::bookmarkUpdates,
            onMultiMarkAsReadClicked = viewModel::markUpdatesRead,
            onMultiDeleteClicked = viewModel::showConfirmDeleteChapters,
            // KNS -->
            onBookmarkColorSelected = viewModel::setBookmarkColor,
            // KNS <--
            // KMK -->
            updateSwipeStartAction = viewModel.chapterSwipeStartAction,
            updateSwipeEndAction = viewModel.chapterSwipeEndAction,
            onUpdateSwipe = viewModel::updateSwipe,
            // KMK <--
            onUpdateSelected = viewModel::toggleSelection,
            onOpenChapter = {
                val intent = ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
                context.startActivity(intent)
            },
            onCalendarClicked = { navigator.push(UpcomingScreen()) },
            onFilterClicked = viewModel::showFilterDialog,
            hasActiveFilters = state.hasActiveFilters,
            // KMK -->
            usePanoramaCover = usePanoramaCover,
            collapseToggle = viewModel::toggleExpandedState,
            // KMK <--
        )

        val onDismissDialog = { viewModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is UpdatesViewModel.Dialog.DeleteConfirmation -> {
                UpdatesDeleteConfirmationDialog(
                    onDismissRequest = onDismissDialog,
                    onConfirm = { viewModel.deleteChapters(dialog.toDelete) },
                )
            }
            is UpdatesViewModel.Dialog.FilterSheet -> {
                UpdatesFilterDialog(
                    onDismissRequest = onDismissDialog,
                    viewModel = settingsViewModel,
                )
            }
            null -> {}
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    Event.InternalError -> viewModel.snackbarHostState.showSnackbar(
                        context.stringResource(MR.strings.internal_error),
                    )
                    is Event.LibraryUpdateTriggered -> {
                        val msg = if (event.started) {
                            MR.strings.updating_library
                        } else {
                            MR.strings.update_already_running
                        }
                        viewModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                    }
                }
            }
        }

        LaunchedEffect(state.selectionMode) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true

                // AM (DISCORD) -->
                with(DiscordRPCService) {
                    discordScope.launchIO { setScreen(context, DiscordScreen.UPDATES) }
                }
                // <-- AM (DISCORD)
            }
        }
        DisposableEffect(Unit) {
            viewModel.resetNewUpdatesCount()

            onDispose {
                viewModel.resetNewUpdatesCount()
            }
        }
    }
}
