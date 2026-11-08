package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Tri-state category list shared by the History and Updates filter dialogs.
 *
 * A category is [TriState.ENABLED_IS] while it is in [includedCategories], [TriState.ENABLED_NOT]
 * while it is in [excludedCategories], and [TriState.DISABLED] otherwise. Tapping a row advances it
 * through that cycle; the persisted state is owned by [onCycleCategory].
 *
 * [inclusionWins] and [onInclusionWinsChange] optionally render an include-over-exclude precedence
 * switch above the list. [onReset] renders a "reset filter" button independently of the switch,
 * whenever either list is non-empty. A caller that passes neither (Updates) gets exactly the list.
 */
@Composable
fun ColumnScope.CategoryFilterSheetContent(
    categories: List<Category>,
    includedCategories: List<Long>,
    excludedCategories: List<Long>,
    onCycleCategory: (Category) -> Unit,
    description: String,
    inclusionWins: Boolean? = null,
    onInclusionWinsChange: ((Boolean) -> Unit)? = null,
    onReset: (() -> Unit)? = null,
) {
    Text(
        description,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
    )

    HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.extraSmall))

    if (onInclusionWinsChange != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsItemsPaddings.Horizontal,
                    vertical = SettingsItemsPaddings.Vertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(KMR.strings.pref_history_filter_inclusion_wins),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(KMR.strings.pref_history_filter_inclusion_wins_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Switch(
                checked = inclusionWins == true,
                onCheckedChange = onInclusionWinsChange,
            )
        }
    }

    if (onReset != null && (includedCategories.isNotEmpty() || excludedCategories.isNotEmpty())) {
        TextButton(
            onClick = onReset,
            modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal),
        ) {
            Text(stringResource(KMR.strings.pref_history_filter_reset))
        }
    }

    if (onInclusionWinsChange != null || onReset != null) {
        HorizontalDivider(modifier = Modifier.padding(MaterialTheme.padding.extraSmall))
    }

    if (categories.isEmpty()) {
        // since it includes the system category, this should only happen when loading is required
        LoadingScreen(modifier = Modifier.padding(16.dp))
        return
    }

    // Derive the tri-state from the persisted lists on every recomposition instead of snapshotting
    // it: a change made elsewhere (sync, another dialog instance, category deletion) must show while
    // this sheet is open, and the preference write on tap recomposes the caller with the new lists.
    Column {
        categories.fastForEach { category ->
            TriStateItem(
                label = category.visualName,
                state = when (category.id) {
                    in includedCategories -> TriState.ENABLED_IS
                    in excludedCategories -> TriState.ENABLED_NOT
                    else -> TriState.DISABLED
                },
                onClick = { onCycleCategory(category) },
            )
        }
    }
}
