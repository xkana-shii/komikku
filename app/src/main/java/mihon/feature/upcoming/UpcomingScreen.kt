package mihon.feature.upcoming

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

class UpcomingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = metroViewModel<UpcomingViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        when (state.dialog) {
            is UpcomingViewModel.Dialog.FilterSheet -> {
                UpcomingFilterDialog(
                    viewModel = viewModel,
                )
            }

            null -> {}
        }

        UpcomingScreenContent(
            state = state,
            setSelectedYearMonth = viewModel::setSelectedYearMonth,
            onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
            // KMK -->
            showUpdatingMangas = viewModel::showUpdatingMangas,
            hideUpdatingMangas = viewModel::hideUpdatingMangas,
            isPredictReleaseDate = MANGA_OUTSIDE_RELEASE_PERIOD in viewModel.restriction,
            // KMK <--
            hasActiveFilters = state.hasActiveFilters,
            onClickFilter = viewModel::showFilterDialog,
        )
    }
}

@Composable
private fun ColumnScope.CategoryFilterSheet(
    viewModel: UpcomingViewModel,
) {
    Text(
        stringResource(MR.strings.pref_filter_upcoming_categories_details),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
    )

    HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.extraSmall))

    val allCategories by viewModel.getCategories.subscribe().collectAsState(initial = emptyList())

    if (allCategories.isEmpty()) {
        LoadingScreen(modifier = Modifier.padding(16.dp))
        return
    }

    val excluded by viewModel.upcomingPreferences.filterExcludedCategories.collectAsState()
    val included by viewModel.upcomingPreferences.filterIncludedCategories.collectAsState()

    // KUN -->
    // Key the remembered tri-state list on the live category list so it is rebuilt (and stays the same
    // size/order) when categories change while the dialog is open. Upstream origin: mihon a73271aefa
    // ("Add category filters to Upcoming calendar", #3607) used a keyless remember, which could throw
    // IndexOutOfBoundsException / misalign the tri-state when allCategories changed.
    val selected = remember(allCategories) {
        allCategories.map { category ->
            when (category.id) {
                in included -> TriState.ENABLED_IS
                in excluded -> TriState.ENABLED_NOT
                else -> TriState.DISABLED
            }
        }.toMutableStateList()
    }
    // KUN <--

    Column {
        allCategories.fastForEachIndexed { idx, category ->
            val state = selected[idx]
            TriStateItem(
                label = category.visualName,
                state = state,
                onClick = {
                    selected[idx] = state.next()
                    viewModel.cycleCategory(category)
                },
            )
        }
    }
}

@Composable
fun UpcomingFilterDialog(
    viewModel: UpcomingViewModel,
) {
    TabbedDialog(
        onDismissRequest = viewModel::resetDialog,
        tabTitles = listOf(
            stringResource(MR.strings.categories),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            CategoryFilterSheet(viewModel = viewModel)
        }
    }
}
