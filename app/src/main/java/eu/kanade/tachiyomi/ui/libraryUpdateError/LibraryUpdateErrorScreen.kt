package eu.kanade.tachiyomi.ui.libraryUpdateError

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.migration.config.MigrationConfigScreen

class LibraryUpdateErrorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<LibraryUpdateErrorViewModel>()
        val state by viewModel.state.collectAsState()

        LibraryUpdateErrorScreen(
            state = state,
            onClick = { item ->
                navigator.push(MigrationConfigScreen(item.error.mangaId))
            },
            onClickCover = { item -> navigator.push(MangaScreen(item.error.mangaId)) },
            onMultiMigrateClicked = {
                navigator.push(MigrationConfigScreen(state.selected.map { it.error.mangaId }))
            },
            onSelectAll = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
            onErrorsDelete = viewModel::deleteSelected,
            onErrorDelete = viewModel::delete,
            onErrorSelected = viewModel::toggleSelection,
            navigateUp = navigator::pop,
        )
    }
}
