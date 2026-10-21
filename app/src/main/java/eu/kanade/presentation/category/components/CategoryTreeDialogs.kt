package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.track.TrackStatus
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
private val updateIntervalChoices = listOf(1, 2, 3, 7, 14, 30)

/**
 * Picks the new parent for [movingIds]: the top level or any category that is not one of them or
 * below one of them (a move must not create a cycle), shown as an indented tree.
 */
@Composable
fun CategoryParentPickerDialog(
    title: String,
    categories: List<Category>,
    movingIds: Set<Long>,
    currentParentId: Long?,
    onSelect: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val byId = categories.associateBy(Category::id)
    val excluded = movingIds + movingIds.flatMap { CategoryTree.descendantIds(it, categories) }
    val targets = CategoryTree.flattened(categories).filterNot { it.id in excluded }
    PickerDialog(title = title, onDismissRequest = onDismissRequest) {
        Choice(
            selected = currentParentId == Category.UNCATEGORIZED_ID,
            label = stringResource(KMR.strings.category_root),
            onClick = { onSelect(Category.UNCATEGORIZED_ID) },
        )
        targets.forEach { category ->
            Choice(
                selected = currentParentId == category.id,
                label = category.name,
                depth = CategoryTree.depth(category.id, byId) + 1,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

/** Picks which of the selected categories the others are merged into. */
@Composable
fun CategoryMergeTargetDialog(
    categories: List<Category>,
    selectedIds: List<Long>,
    onSelect: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val byId = categories.associateBy(Category::id)
    PickerDialog(
        title = stringResource(KMR.strings.category_manager_merge_title),
        onDismissRequest = onDismissRequest,
        message = stringResource(KMR.strings.category_manager_merge_summary),
    ) {
        CategoryTree.flattened(categories).filter { it.id in selectedIds }.forEach { category ->
            Choice(
                selected = false,
                label = CategoryTree.path(category, byId).joinToString(" / "),
                onClick = { onSelect(category.id) },
            )
        }
    }
}

/** Binds a category to a unified tracker status (Smart Category) or removes the binding. */
@Composable
fun CategoryTrackStatusDialog(
    current: Int?,
    onSelect: (Int?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    PickerDialog(
        title = stringResource(KMR.strings.category_track_status),
        onDismissRequest = onDismissRequest,
        message = stringResource(KMR.strings.category_track_status_summary),
    ) {
        Choice(
            selected = current == null,
            label = stringResource(KMR.strings.category_track_status_none),
            onClick = { onSelect(null) },
        )
        TrackStatus.entries.filterNot { it == TrackStatus.OTHER }.forEach { status ->
            Choice(
                selected = current == status.int,
                label = stringResource(status.res),
                onClick = { onSelect(status.int) },
            )
        }
    }
}

/** A category's library update interval in days, or the global schedule. */
@Composable
fun CategoryUpdateIntervalDialog(
    current: Int?,
    onSelect: (Int?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    PickerDialog(title = stringResource(KMR.strings.category_update_interval), onDismissRequest = onDismissRequest) {
        Choice(
            selected = current == null,
            label = stringResource(KMR.strings.category_update_interval_global),
            onClick = { onSelect(null) },
        )
        updateIntervalChoices.forEach { days ->
            Choice(
                selected = current == days,
                label = pluralStringResource(KMR.plurals.category_update_interval_days, days, days),
                onClick = { onSelect(days) },
            )
        }
    }
}

@Composable
fun CategoryDeleteManyDialog(
    count: Int,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.category_manager_delete_many_title)) },
        text = { Text(pluralStringResource(KMR.plurals.category_manager_delete_many, count, count)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismissRequest()
                },
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
private fun PickerDialog(
    title: String,
    onDismissRequest: () -> Unit,
    message: String? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, depth: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp * depth, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
// KNS <--
