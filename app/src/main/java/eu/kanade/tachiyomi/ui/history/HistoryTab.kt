package eu.kanade.tachiyomi.ui.history

import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.presentation.history.components.HistoryFilterDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object HistoryTab : Tab {
    @Suppress("unused")
    private fun readResolve(): Any = HistoryTab

    private val snackbarHostState = SnackbarHostState()

    private val resumeLastChapterReadEvent = Channel<Unit>()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.label_recent_manga),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastChapterReadEvent.send(Unit)
    }

    // SY -->
    @Composable
    override fun isEnabled(): Boolean {
        val scope = rememberCoroutineScope()
        return remember {
            Injekt.get<UiPreferences>().showNavHistory().asState(scope)
        }.value
    }
    // SY <--

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = viewModel { HistoryViewModel() }
        val state by viewModel.state.collectAsStateWithLifecycle()
        // KMK -->
        val settingsScreenModel = rememberScreenModel { HistorySettingsScreenModel() }
        val usePanoramaCover by settingsScreenModel.historyPreferences.usePanoramaCover().collectAsState()
        // KMK <--

        HistoryScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onClickCover = { navigator.push(MangaScreen(it)) },
            onClickResume = viewModel::getNextChapterForManga,
            onDialogChange = viewModel::setDialog,
            onClickFavorite = viewModel::addFavorite,
            // KMK -->
            toggleSelectionMode = viewModel::toggleSelectionMode,
            onSelectAll = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
            onHistorySelected = viewModel::toggleSelection,
            onFilterClicked = viewModel::showFilterDialog,
            hasActiveFilters = state.hasActiveFilters,
            usePanoramaCover = usePanoramaCover,
            // KMK <--
        )

        val onDismissRequest = { viewModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is HistoryViewModel.Dialog.Delete -> {
                HistoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { all ->
                        // KMK -->
                        if (all) {
                            viewModel.removeAllFromHistory(dialog.histories)
                        } else {
                            viewModel.removeFromHistory(dialog.histories)
                        }
                        // KMK <--
                    },
                )
            }
            is HistoryViewModel.Dialog.DeleteAll -> {
                HistoryDeleteAllDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = viewModel::removeAllHistory,
                )
            }
            is HistoryViewModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { viewModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { viewModel.showMigrateDialog(dialog.manga, it) },
                    // KMK -->
                    targetManga = dialog.manga,
                    // KMK <--
                )
            }
            is HistoryViewModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        viewModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is HistoryViewModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            // KMK -->
            is HistoryViewModel.Dialog.FilterSheet -> {
                HistoryFilterDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = settingsScreenModel,
                )
            }
            // KMK <--
            null -> {}
        }

        // KMK -->
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                // KMK <--
                (context as? MainActivity)?.ready = true

                // AM (DISCORD) -->
                with(DiscordRPCService) {
                    discordScope.launchIO { setScreen(context, DiscordScreen.HISTORY) }
                }
                // <-- AM (DISCORD)
            }
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { e ->
                when (e) {
                    HistoryViewModel.Event.InternalError ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    HistoryViewModel.Event.HistoryCleared ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    is HistoryViewModel.Event.OpenChapter -> openChapter(context, e.chapter)
                }
            }
        }

        LaunchedEffect(Unit) {
            resumeLastChapterReadEvent.receiveAsFlow().collectLatest {
                openChapter(context, viewModel.getNextChapter())
            }
        }
    }

    private suspend fun openChapter(context: Context, chapter: Chapter?) {
        if (chapter != null) {
            val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
            context.startActivity(intent)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }
}
