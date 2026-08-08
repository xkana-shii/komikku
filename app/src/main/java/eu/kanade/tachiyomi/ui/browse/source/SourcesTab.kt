package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined._18UpRating
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.SourceCategoriesDialog
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen.SmartSearchConfig
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.sourcesTab(
    smartSearchConfig: SmartSearchConfig? = null,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = viewModel { SourcesViewModel(smartSearchConfig = smartSearchConfig) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    return TabContent(
        // SY -->
        titleRes = when (smartSearchConfig == null) {
            true -> MR.strings.label_sources
            false -> SYMR.strings.find_in_another_source
        },
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalSearchScreen(smartSearchConfig?.origTitle ?: "")) },
            ),
            // KMK -->
            AppBar.Action(
                title = stringResource(KMR.strings.action_toggle_nsfw_only),
                icon = Icons.Outlined._18UpRating,
                iconTint = if (state.nsfwOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                onClick = { viewModel.toggleNsfwOnly() },
            ),
            // KMK <--
        ).let {
            when (smartSearchConfig) {
                null -> {
                    it.add(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_filter),
                            icon = Icons.Outlined.FilterList,
                            onClick = { navigator.push(SourcesFilterScreen()) },
                        ),
                    )
                }
                // Merge: find in another source
                else -> it
            }
        },
        // SY <--
        content = { contentPadding, snackbarHostState ->
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    // SY -->
                    val screen = when {
                        // Search selected source for entries to merge or for the recommending entry
                        smartSearchConfig != null -> SmartSearchScreen(source.id, smartSearchConfig)
                        listing == Listing.Popular && viewModel.useNewSourceNavigation -> SourceFeedScreen(source.id)
                        else -> BrowseSourceScreen(source.id, listing.query)
                    }
                    navigator.push(screen)
                    // SY <--
                },
                onClickPin = viewModel::togglePin,
                onLongClickItem = viewModel::showSourceDialog,
                // KMK -->
                onChangeSearchQuery = viewModel::search,
                // KMK <--
            )

            when (val dialog = state.dialog) {
                is SourcesViewModel.Dialog.SourceLongClick -> {
                    val source = dialog.source
                    SourceOptionsDialog(
                        source = source,
                        onClickPin = {
                            viewModel.togglePin(source)
                            viewModel.closeDialog()
                        },
                        onClickDisable = {
                            viewModel.toggleSource(source)
                            viewModel.closeDialog()
                        },
                        // SY -->
                        onClickSetCategories = {
                            viewModel.showSourceCategoriesDialog(source)
                        }.takeIf { state.categories.isNotEmpty() },
                        onClickToggleDataSaver = {
                            viewModel.toggleExcludeFromDataSaver(source)
                            viewModel.closeDialog()
                        }.takeIf { state.dataSaverEnabled },
                        // SY <--
                        onDismiss = viewModel::closeDialog,
                        // KMK -->
                        onClickSettings = {
                            if (source.installedExtension !== null) {
                                navigator.push(ExtensionDetailsScreen(source.installedExtension!!.pkgName))
                            }
                            viewModel.closeDialog()
                        },
                        // KMK <--
                    )
                }
                is SourcesViewModel.Dialog.SourceCategories -> {
                    val source = dialog.source
                    SourceCategoriesDialog(
                        source = source,
                        categories = state.categories,
                        onClickCategories = { categories ->
                            viewModel.setSourceCategories(source, categories)
                            viewModel.closeDialog()
                        },
                        onDismissRequest = viewModel::closeDialog,
                    )
                }
                null -> Unit
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        SourcesViewModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
