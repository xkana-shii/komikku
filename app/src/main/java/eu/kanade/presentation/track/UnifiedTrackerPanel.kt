package eu.kanade.presentation.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.manga.track.UnifiedTrackField
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Warning
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
/**
 * Edits every linked tracker of a manga at once (status, chapters, score, dates), picks the preferred
 * tracker, and offers to align trackers whose progress differs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedTrackerPanel(
    trackers: List<Tracker>,
    preferredTrackerId: Long?,
    onPreferredChange: (Long?) -> Unit,
    progressMismatch: Boolean,
    syncTarget: String,
    onSyncProgress: () -> Unit,
    onEdit: (UnifiedTrackField) -> Unit,
    onRemove: (trackerIds: Set<Long>, remotely: Boolean) -> Unit,
) {
    var showRemove by remember { mutableStateOf(false) }
    if (showRemove) {
        RemoveTrackersDialog(
            trackers = trackers,
            onConfirm = { ids, remotely ->
                onRemove(ids, remotely)
                showRemove = false
            },
            onDismissRequest = { showRemove = false },
        )
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(KMR.strings.tracker_all_trackers), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(KMR.strings.tracker_all_trackers_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PreferredTrackerMenu(trackers, preferredTrackerId, onPreferredChange)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onEdit(UnifiedTrackField.STATUS) }, label = { Text(stringResource(MR.strings.status)) })
                AssistChip(
                    onClick = { onEdit(UnifiedTrackField.CHAPTERS) },
                    label = { Text(stringResource(MR.strings.chapters)) },
                )
                if (trackers.any { it.getScoreList().isNotEmpty() }) {
                    AssistChip(onClick = { onEdit(UnifiedTrackField.SCORE) }, label = { Text(stringResource(MR.strings.score)) })
                }
                AssistChip(
                    onClick = { showRemove = true },
                    label = { Text(stringResource(KMR.strings.tracker_remove_trackers)) },
                )
                if (trackers.any { it.supportsReadingDates }) {
                    AssistChip(
                        onClick = { onEdit(UnifiedTrackField.START_DATE) },
                        label = { Text(stringResource(MR.strings.track_started_reading_date)) },
                    )
                    AssistChip(
                        onClick = { onEdit(UnifiedTrackField.END_DATE) },
                        label = { Text(stringResource(MR.strings.track_finished_reading_date)) },
                    )
                }
            }
            if (progressMismatch) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbols.RoundedFilled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(KMR.strings.tracker_progress_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    TextButton(onClick = onSyncProgress) {
                        Text(stringResource(KMR.strings.tracker_sync_progress, syncTarget))
                    }
                }
            }
        }
    }
}

/** Picks linked trackers to unlink, and whether to also remove the entries on the services. */
@Composable
private fun RemoveTrackersDialog(
    trackers: List<Tracker>,
    onConfirm: (trackerIds: Set<Long>, remotely: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val selected = remember { mutableStateListOf<Long>() }
    var remotely by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.tracker_remove_trackers)) },
        text = {
            Column {
                trackers.forEach { tracker ->
                    LabeledCheckbox(
                        label = tracker.name,
                        checked = tracker.id in selected,
                        onCheckedChange = { if (it) selected.add(tracker.id) else selected.remove(tracker.id) },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LabeledCheckbox(
                    label = stringResource(KMR.strings.tracker_remove_remotely),
                    checked = remotely,
                    onCheckedChange = { remotely = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toSet(), remotely) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(MR.strings.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
private fun PreferredTrackerMenu(
    trackers: List<Tracker>,
    preferredTrackerId: Long?,
    onPreferredChange: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                trackers.find { it.id == preferredTrackerId }?.name
                    ?: stringResource(KMR.strings.tracker_preferred_none),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = stringResource(KMR.strings.tracker_preferred),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(KMR.strings.tracker_preferred_none)) },
                onClick = {
                    onPreferredChange(null)
                    expanded = false
                },
            )
            trackers.forEach { tracker ->
                DropdownMenuItem(
                    text = { Text(tracker.name) },
                    onClick = {
                        onPreferredChange(tracker.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
// KNS <--
