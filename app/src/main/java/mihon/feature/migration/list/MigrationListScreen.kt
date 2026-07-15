package mihon.feature.migration.list

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.feature.migration.config.MigrationConfigScreenSheet
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationMangaDialog
import mihon.feature.migration.list.components.MigrationProgressDialog
import mihon.feature.migration.list.models.MigratingManga
import tachiyomi.i18n.MR

/**
 * Screen showing a list of pair of current-target manga entries being migrated.
 */
class MigrationListScreen(
    private val mangaIds: Collection<Long>,
    private val extraSearchQuery: String?,
    // KMK -->
    private val isSmartSearchSingleEntry: Boolean = false,
    // KMK <--
) : Screen() {

    private var matchOverride: Pair<Long, Long>? = null

    fun addMatchOverride(current: Long, target: Long) {
        matchOverride = current to target
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        // KMK -->
        val singleEntryNoSmartSearch = mangaIds.size == 1 && !isSmartSearchSingleEntry
        // KMK <--
        val viewModel = viewModel { MigrationListViewModel(mangaIds, extraSearchQuery, /* KMK --> */ singleEntryNoSmartSearch /* KMK <-- */) }
        val state by viewModel.state.collectAsState()
        val context = LocalContext.current

        // KMK -->
        var hasPushedManual by rememberSaveable(mangaIds) { mutableStateOf(false) }
        LaunchedEffect(mangaIds) {
            if (singleEntryNoSmartSearch && !hasPushedManual) {
                @Suppress("AssignedValueIsNeverRead")
                hasPushedManual = true
                navigator.push(MigrateSearchScreen(mangaIds.single()))
            }
        }
        // KMK <--

        LaunchedEffect(matchOverride) {
            val (current, target) = matchOverride ?: return@LaunchedEffect
            viewModel.useMangaForMigration(
                current = current,
                target = target,
                onMissingChapters = {
                    context.toast(MR.strings.migrationListScreen_matchWithoutChapterToast, Toast.LENGTH_LONG)
                },
            )
            matchOverride = null
        }

        LaunchedEffect(viewModel) {
            viewModel.navigateBackEvent.collect {
                // KMK -->
                /* If this screen is called from single manga migration, replace the MangaScreen in the backstack
                   with the newly migrated manga to reflect the changes properly.
                   Otherwise, just pop normally. */
                if (mangaIds.size == 1 && navigator.items.any { it is MangaScreen }) {
                    val mangaId = (state.items.firstOrNull()?.searchResult?.value as? MigratingManga.SearchResult.Success)?.manga?.id
                    if (mangaId != null) {
                        val newStack = navigator.items.filter {
                            it !is MangaScreen &&
                                it !is MigrationListScreen &&
                                it !is MigrationConfigScreen
                        } + MangaScreen(mangaId)
                        navigator replaceAll newStack.first()
                        navigator.push(newStack.drop(1))

                        // need to set the navigator in a pop state to dispose of everything properly
                        navigator.push(this@MigrationListScreen)
                        navigator.pop()
                    } else {
                        navigator.pop()
                    }
                } else {
                    // KMK <--
                    navigator.pop()
                }
            }
        }
        MigrationListScreenContent(
            items = state.items,
            migrationComplete = state.migrationComplete,
            finishedCount = state.finishedCount,
            onItemClick = {
                navigator.push(MangaScreen(it.id, true))
            },
            onSearchManually = { migrationItem ->
                navigator push MigrateSearchScreen(migrationItem.manga.id)
            },
            onSkip = { viewModel.removeManga(it) },
            onMigrate = { viewModel.migrateNow(mangaId = it, replace = true) },
            onCopy = { viewModel.migrateNow(mangaId = it, replace = false) },
            openMigrationDialog = viewModel::showMigrateDialog,
            // KMK -->
            onCancel = { viewModel.cancelManga(it) },
            openOptionsDialog = viewModel::openOptionsDialog,
            // KMK <--
        )

        when (val dialog = state.dialog) {
            is MigrationListViewModel.Dialog.Migrate -> {
                MigrationMangaDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    copy = dialog.copy,
                    totalCount = dialog.totalCount,
                    skippedCount = dialog.skippedCount,
                    onMigrate = {
                        if (dialog.copy) {
                            viewModel.copyMangas()
                        } else {
                            viewModel.migrateMangas()
                        }
                    },
                )
            }
            is MigrationListViewModel.Dialog.Progress -> {
                MigrationProgressDialog(
                    progress = dialog.progress,
                    exitMigration = viewModel::cancelMigrate,
                )
            }
            MigrationListViewModel.Dialog.Exit -> {
                MigrationExitDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    exitMigration = navigator::pop,
                )
            }
            // KMK -->
            MigrationListViewModel.Dialog.Options -> {
                MigrationConfigScreenSheet(
                    preferences = viewModel.preferences,
                    onDismissRequest = viewModel::dismissDialog,
                    onStartMigration = { _ ->
                        viewModel.dismissDialog()
                        viewModel.updateOptions()
                    },
                    fullSettings = false,
                )
            }
            // KMK <--
            null -> Unit
        }

        BackHandler(true) {
            viewModel.showExitDialog()
        }
    }
}
