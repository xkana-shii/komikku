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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsViewModel
import kotlinx.collections.immutable.persistentListOf
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
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            FilterSheet(viewModel = viewModel)
        }
    }
}

@Composable
private fun ColumnScope.FilterSheet(
    viewModel: UpdatesSettingsViewModel,
) {
    val filterDownloaded by viewModel.updatesPreferences.filterDownloaded().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = filterDownloaded,
        onClick = { viewModel.toggleFilter(UpdatesPreferences::filterDownloaded) },
    )

    val filterUnread by viewModel.updatesPreferences.filterUnread().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = filterUnread,
        onClick = { viewModel.toggleFilter(UpdatesPreferences::filterUnread) },
    )

    val filterStarted by viewModel.updatesPreferences.filterStarted().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = filterStarted,
        onClick = { viewModel.toggleFilter(UpdatesPreferences::filterStarted) },
    )

    val filterBookmarked by viewModel.updatesPreferences.filterBookmarked().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = { viewModel.toggleFilter(UpdatesPreferences::filterBookmarked) },
    )

    HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.small))

    val filterExcludedScanlators by viewModel.updatesPreferences.filterExcludedScanlators().collectAsState()

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

    val panoramaCover by viewModel.updatesPreferences.usePanoramaCover().collectAsState()

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
