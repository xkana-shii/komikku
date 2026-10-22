package eu.kanade.presentation.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Edit
import mihon.icons.materialsymbols.roundedfilled.Star
import mihon.icons.materialsymbols.roundedfilled.Warning
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
private const val UNSET_ALPHA = 0.5f

/**
 * The previous personal fork's card for an entry with two or more linked trackers: one row of tracker
 * icons (a red dot when a tracker's progress differs from the preferred one, a warning when its last
 * update failed, a star on the preferred tracker) with an edit-mode switch, then status, score, chapters
 * with -/+ and the reading dates of the preferred tracker. Every edit applies to all linked trackers.
 * In edit mode a tap on an icon changes that tracker's entry and a long press makes it the preferred
 * tracker; otherwise a tap opens it and a long press copies its link.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnifiedTrackerCard(
    presentation: TrackerSheetPresentation,
    untracked: List<TrackItem>,
    seriesTitle: String,
    editMode: Boolean,
    onToggleEditMode: () -> Unit,
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    onAdjustProgress: (Int) -> Unit,
    onNewSearch: (TrackItem) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onCopyLink: (TrackItem) -> Unit,
    onSetPreferredTracker: (TrackItem) -> Unit,
    onRemoveTrackers: (trackerIds: Set<Long>, remotely: Boolean) -> Unit,
    syncTarget: String,
    onSyncProgress: () -> Unit,
) {
    val primary = presentation.primary ?: return
    var showRemove by remember { mutableStateOf(false) }
    if (showRemove) {
        RemoveTrackersDialog(
            trackers = presentation.bound.map { it.tracker },
            onConfirm = { ids, remotely ->
                onRemoveTrackers(ids, remotely)
                showRemove = false
            },
            onDismissRequest = { showRemove = false },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (presentation.bound + untracked).forEach { item ->
                    UnifiedTrackerIcon(
                        item = item,
                        presentation = presentation,
                        editMode = editMode,
                        onNewSearch = onNewSearch,
                        onOpenInBrowser = onOpenInBrowser,
                        onCopyLink = onCopyLink,
                        onSetPreferredTracker = onSetPreferredTracker,
                    )
                }
            }
            IconButton(
                onClick = onToggleEditMode,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (editMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Icon(imageVector = MaterialSymbols.Rounded.Edit, contentDescription = stringResource(MR.strings.action_edit))
            }
        }
        if (seriesTitle.isNotBlank()) {
            Text(
                text = seriesTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                CardCell(weight = 1f, onClick = { onStatusClick(primary) }) {
                    Text(text = stringResource(presentation.status), style = MaterialTheme.typography.bodyMedium)
                }
                if (presentation.supportsScore) {
                    VerticalDivider()
                    CardCell(weight = 1f, onClick = { onScoreClick(primary) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val score = presentation.score
                            Text(
                                text = score ?: stringResource(MR.strings.score),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (score == null) UNSET_ALPHA else 1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (presentation.appendScoreStar) {
                                Icon(
                                    imageVector = MaterialSymbols.RoundedFilled.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                CardCell(weight = 0.15f, onClick = { onAdjustProgress(-1) }) {
                    Text(text = "−", style = MaterialTheme.typography.titleMedium)
                }
                VerticalDivider()
                CardCell(weight = 0.7f, onClick = { onChapterClick(primary) }) {
                    Text(text = presentation.chapters, style = MaterialTheme.typography.bodyMedium)
                }
                VerticalDivider()
                CardCell(weight = 0.15f, onClick = { onAdjustProgress(1) }) {
                    Text(text = "+", style = MaterialTheme.typography.titleMedium)
                }
            }
            HorizontalDivider()
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                if (presentation.supportsReadingDates) {
                    CardCell(weight = 0.425f, onClick = { onStartDateEdit(primary) }) {
                        DateText(presentation.startDate, stringResource(MR.strings.track_started_reading_date))
                    }
                    VerticalDivider()
                    CardCell(weight = 0.425f, onClick = { onEndDateEdit(primary) }) {
                        DateText(presentation.finishDate, stringResource(MR.strings.track_finished_reading_date))
                    }
                    VerticalDivider()
                } else {
                    Spacer(Modifier.weight(0.85f))
                }
                Box(Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { showRemove = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = stringResource(KMR.strings.tracker_remove_trackers),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        // The foundation's progress alignment, kept under the card.
        if (presentation.mismatchedIds.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(KMR.strings.tracker_progress_mismatch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                )
                TextButton(onClick = onSyncProgress) {
                    Text(stringResource(KMR.strings.tracker_sync_progress, syncTarget))
                }
            }
        }
    }
}

@Composable
private fun UnifiedTrackerIcon(
    item: TrackItem,
    presentation: TrackerSheetPresentation,
    editMode: Boolean,
    onNewSearch: (TrackItem) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onCopyLink: (TrackItem) -> Unit,
    onSetPreferredTracker: (TrackItem) -> Unit,
) {
    val id = item.tracker.id
    val unsyncedDescription = stringResource(KMR.strings.tracker_unsynced_a11y, item.tracker.name)
    BadgedBox(
        badge = {
            when {
                id in presentation.errorIds -> Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.RoundedFilled.Warning,
                        contentDescription = stringResource(KMR.strings.tracker_sync_error_a11y, item.tracker.name),
                        modifier = Modifier.size(12.dp),
                    )
                }
                id in presentation.mismatchedIds -> Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { contentDescription = unsyncedDescription },
                )
                id == presentation.preferredTrackerId -> Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.RoundedFilled.Star,
                        contentDescription = stringResource(KMR.strings.tracker_preferred_a11y, item.tracker.name),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            TrackLogoIcon(
                tracker = item.tracker,
                onClick = {
                    when (item.unifiedIconClickAction(editMode)) {
                        UnifiedTrackerIconAction.OPEN -> onOpenInBrowser(item)
                        UnifiedTrackerIconAction.SEARCH -> onNewSearch(item)
                        else -> Unit
                    }
                },
                onLongClick = when (item.unifiedIconLongPressAction(editMode)) {
                    UnifiedTrackerIconAction.COPY_LINK -> ({ onCopyLink(item) })
                    UnifiedTrackerIconAction.SET_PREFERRED -> ({ onSetPreferredTracker(item) })
                    else -> null
                },
            )
        }
    }
}

/** The icons of trackers not linked yet, shown under a single linked tracker; a tap binds one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UntrackedTrackerIcons(items: List<TrackItem>, onNewSearch: (TrackItem) -> Unit) {
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                TrackLogoIcon(tracker = item.tracker, onClick = { onNewSearch(item) })
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CardCell(
    weight: Float,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DateText(date: String?, placeholder: String) {
    Text(
        text = date ?: placeholder,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (date == null) UNSET_ALPHA else 1f),
    )
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
// KNS <--
