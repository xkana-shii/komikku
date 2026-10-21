package eu.kanade.tachiyomi.ui.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.storage.StorageScreenContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

// KNS -->
class StorageScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<StorageViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.storage_overview),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            when (val current = state) {
                is StorageViewModel.State.Loading -> LoadingScreen(
                    percentage = current.progress,
                    message = KMR.strings.storage_overview_calculating,
                )
                is StorageViewModel.State.Success -> StorageScreenContent(
                    state = current,
                    paddingValues = paddingValues,
                    onCategorySelected = viewModel::selectCategory,
                    onDelete = viewModel::delete,
                    onClickCover = { navigator.push(MangaScreen(it.entry.manga.id)) },
                )
            }
        }
    }
}
// KNS <--
