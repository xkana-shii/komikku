package eu.kanade.presentation.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.CategoryFilterSheetContent
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.manga.components.BookmarkColorFilterDialog
import eu.kanade.presentation.manga.components.BookmarkColorFilterRow
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsViewModel
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.updates.service.UpdatesPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun UpdatesFilterDialog(
    onDismissRequest: () -> Unit,
    viewModel: UpdatesSettingsViewModel,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = listOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.categories),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterSheet(viewModel = viewModel)
                1 -> CategoryFilterSheet(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterSheet(
    viewModel: UpdatesSettingsViewModel,
) {
    val filterDownloaded by viewModel.updatesPreferences.filterDownloaded.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = filterDownloaded,
        onClick = { viewModel.toggleFilter(UpdatesPreferences::filterDownloaded) },
    )

    val filterUnread by viewModel.updatesPreferences.filterUnread.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = filterUnread,
        onClick = { viewModel.toggleFilter(UpdatesPreferences::filterUnread) },
    )

    val filterStarted by viewModel.updatesPreferences.filterStarted.collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = filterStarted,
        onClick = { viewModel.toggleFilter(UpdatesPreferences::filterStarted) },
    )

    val filterBookmarked by viewModel.updatesPreferences.filterBookmarked.collectAsState()
    // KNS --> The color dialog is shared with Library and chapter filters but its preferences stay local to Updates.
    val filterBookmarkColors by viewModel.updatesPreferences.filterBookmarkColors.collectAsState()
    val filterBookmarkColorsExclude by viewModel.updatesPreferences.filterBookmarkColorsExclude.collectAsState()
    var showBookmarkColorFilter by remember { mutableStateOf(false) }
    BookmarkColorFilterRow(
        state = filterBookmarked,
        onBookmarkedClick = { viewModel.toggleFilter(UpdatesPreferences::filterBookmarked) },
        onEditClick = { showBookmarkColorFilter = true },
    )
    if (showBookmarkColorFilter) {
        BookmarkColorFilterDialog(
            included = BookmarkColor.fromNames(filterBookmarkColors),
            excluded = BookmarkColor.fromNames(filterBookmarkColorsExclude),
            onChanged = { included, excluded ->
                viewModel.updatesPreferences.filterBookmarkColors.set(included.mapTo(mutableSetOf()) { it.name })
                viewModel.updatesPreferences.filterBookmarkColorsExclude.set(excluded.mapTo(mutableSetOf()) { it.name })
                if ((included.isNotEmpty() || excluded.isNotEmpty()) && filterBookmarked == TriState.DISABLED) {
                    viewModel.updatesPreferences.filterBookmarked.set(TriState.ENABLED_IS)
                }
                showBookmarkColorFilter = false
            },
            onDismissRequest = { showBookmarkColorFilter = false },
        )
    }
    // KNS <--

    HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.small))

    val filterExcludedScanlators by viewModel.updatesPreferences.filterExcludedScanlators.collectAsState()

    Row(
        modifier = Modifier
            // KMK -->
            .clickable { viewModel.toggleSwitch(UpdatesPreferences::filterExcludedScanlators) }
            // KMK <--
            .fillMaxWidth()
            .padding(horizontal = SettingsItemsPaddings.Horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(MR.strings.action_filter_excluded_scanlators),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )

        Switch(
            checked = filterExcludedScanlators,
            // KMK -->
            onCheckedChange = { viewModel.toggleSwitch(UpdatesPreferences::filterExcludedScanlators) },
            // KMK <--
        )
    }

    // KMK -->
    HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.small))

    val panoramaCover by viewModel.updatesPreferences.usePanoramaCover.collectAsState()

    Row(
        modifier = Modifier
            .clickable { viewModel.toggleSwitch(UpdatesPreferences::usePanoramaCover) }
            .fillMaxWidth()
            .padding(horizontal = SettingsItemsPaddings.Horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(KMR.strings.action_panorama_cover),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )

        Switch(
            checked = panoramaCover,
            onCheckedChange = { viewModel.toggleSwitch(UpdatesPreferences::usePanoramaCover) },
        )
    }
    // KMK <--
}

@Composable
private fun ColumnScope.CategoryFilterSheet(
    viewModel: UpdatesSettingsViewModel,
) {
    val allCategories by viewModel.getCategories.subscribe().collectAsState(initial = emptyList())
    val excluded by viewModel.updatesPreferences.filterExcludedCategories.collectAsState()
    val included by viewModel.updatesPreferences.filterIncludedCategories.collectAsState()

    // KUN -->
    // Rendering is shared with History's category tab so the two cannot drift. The shared list
    // derives the tri-state from the two lists on every recomposition, so there is no snapshot to
    // go stale when categories change (mihon 1d8a2b05dd used a keyless remember; keying it only
    // shortened the window).
    CategoryFilterSheetContent(
        categories = allCategories,
        includedCategories = included,
        excludedCategories = excluded,
        onCycleCategory = viewModel::cycleCategory,
        description = stringResource(MR.strings.pref_filter_update_categories_details),
    )
    // KUN <--
}
