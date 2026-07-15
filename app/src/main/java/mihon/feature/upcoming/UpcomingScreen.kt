package mihon.feature.upcoming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD

class UpcomingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = viewModel<UpcomingViewModel>()
        val state by viewModel.state.collectAsState()

        UpcomingScreenContent(
            state = state,
            setSelectedYearMonth = viewModel::setSelectedYearMonth,
            onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
            // KMK -->
            showUpdatingMangas = viewModel::showUpdatingMangas,
            hideUpdatingMangas = viewModel::hideUpdatingMangas,
            isPredictReleaseDate = MANGA_OUTSIDE_RELEASE_PERIOD in viewModel.restriction,
            // KMK <--
        )
    }
}
