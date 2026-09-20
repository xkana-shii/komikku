package eu.kanade.presentation.track

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.track.service.TrackerProgressSync
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun TrackInfoDialogHome(
    trackItems: List<TrackItem>,
    dateFormat: DateTimeFormatter,
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    onNewSearch: (TrackItem) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onRemoved: (TrackItem) -> Unit,
    onCopyLink: (TrackItem) -> Unit,
    onTogglePrivate: (TrackItem) -> Unit,
    header: @Composable () -> Unit = {},
    preferredId: Long? = null,
    onAdjustProgress: (Int) -> Unit = {},
    onRemoveTracking: (List<TrackItem>) -> Unit = {},
    errorTrackerIds: Set<Long> = emptySet(),
    busy: Boolean = false,
) {
    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        header()
        val bound = trackItems.filter { it.track != null }
        if (bound.size >= 2) {
            UnifiedTrackerCard(
                trackItems = trackItems, preferredId = preferredId, dateFormat = dateFormat,
                onAdjustProgress = onAdjustProgress, busy = busy, onStatusClick = onStatusClick,
                onChapterClick = onChapterClick, onScoreClick = onScoreClick,
                onStartDateEdit = onStartDateEdit, onEndDateEdit = onEndDateEdit,
                onNewSearch = onNewSearch, onRemoved = onRemoved, onOpenInBrowser = onOpenInBrowser,
                onCopyLink = onCopyLink, onRemoveTracking = onRemoveTracking, errorTrackerIds = errorTrackerIds,
            )
            if (TrackerProgressSync.hasMismatch(bound.mapNotNull { it.track })) {
                Text(stringResource(KMR.strings.track_unsynced_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (errorTrackerIds.isNotEmpty()) {
                Text(stringResource(KMR.strings.track_sync_error_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        } else {
            bound.forEach { item ->
                if (item.track != null) {
                    val supportsScoring = item.tracker.getScoreList().isNotEmpty()
                    val supportsReadingDates = item.tracker.supportsReadingDates
                    val supportsPrivate = item.tracker.supportsPrivateTracking
                    TrackInfoItem(
                        title = item.track.title,
                        tracker = item.tracker,
                        status = item.tracker.getStatus(item.track.status),
                        onStatusClick = { onStatusClick(item) },
                        chapters = "${item.track.lastChapterRead.toInt()}".let {
                            val totalChapters = item.track.totalChapters
                            if (totalChapters > 0) {
                                // Add known total chapter count
                                "$it / $totalChapters"
                            } else {
                                it
                            }
                        },
                        onChaptersClick = { onChapterClick(item) },
                        score = item.tracker.displayScore(item.track)
                            .takeIf { supportsScoring && item.track.score != 0.0 },
                        onScoreClick = { onScoreClick(item) }
                            .takeIf { supportsScoring },
                        startDate = remember(item.track.startDate) { dateFormat.format(item.track.startDate.toLocalDate()) }
                            .takeIf { supportsReadingDates && item.track.startDate != 0L },
                        onStartDateClick = { onStartDateEdit(item) } // TODO
                            .takeIf { supportsReadingDates },
                        endDate = dateFormat.format(item.track.finishDate.toLocalDate())
                            .takeIf { supportsReadingDates && item.track.finishDate != 0L },
                        onEndDateClick = { onEndDateEdit(item) }
                            .takeIf { supportsReadingDates },
                        onNewSearch = { onNewSearch(item) },
                        onOpenInBrowser = { onOpenInBrowser(item) },
                        onRemoved = { onRemoved(item) },
                        onCopyLink = { onCopyLink(item) },
                        private = item.track.private,
                        onTogglePrivate = { onTogglePrivate(item) }
                            .takeIf { supportsPrivate },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                trackItems.filter { it.track == null }.forEach { item ->
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(4.dp)) {
                        TrackLogoIcon(tracker = item.tracker, onClick = { onNewSearch(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackInfoItem(
    title: String,
    tracker: Tracker,
    status: StringResource?,
    onStatusClick: () -> Unit,
    chapters: String,
    onChaptersClick: () -> Unit,
    score: String?,
    onScoreClick: (() -> Unit)?,
    startDate: String?,
    onStartDateClick: (() -> Unit)?,
    endDate: String?,
    onEndDateClick: (() -> Unit)?,
    onNewSearch: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onRemoved: () -> Unit,
    onCopyLink: () -> Unit,
    private: Boolean,
    onTogglePrivate: (() -> Unit)?,
) {
    val context = LocalContext.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = {
                    if (private) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.absoluteOffset(x = (-5).dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = stringResource(MR.strings.tracked_privately),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                },
            ) {
                TrackLogoIcon(
                    tracker = tracker,
                    onClick = onOpenInBrowser,
                    onLongClick = onCopyLink,
                )
            }
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .weight(1f)
                    .combinedClickable(
                        onClick = onNewSearch,
                        onLongClick = {
                            context.copyToClipboard(title, title)
                        },
                    )
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            VerticalDivider()
            TrackInfoItemMenu(
                onOpenInBrowser = onOpenInBrowser,
                onRemoved = onRemoved,
                onCopyLink = onCopyLink,
                private = private,
                onTogglePrivate = onTogglePrivate,
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Column {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TrackDetailsItem(
                        modifier = Modifier.weight(1f),
                        text = status?.let { stringResource(it) } ?: "",
                        onClick = onStatusClick,
                    )
                    VerticalDivider()
                    TrackDetailsItem(
                        modifier = Modifier.weight(1f),
                        text = chapters,
                        onClick = onChaptersClick,
                    )
                    if (onScoreClick != null) {
                        VerticalDivider()
                        TrackDetailsItem(
                            modifier = Modifier.weight(1f),
                            text = score,
                            placeholder = stringResource(MR.strings.score),
                            onClick = onScoreClick,
                        )
                    }
                }

                if (onStartDateClick != null && onEndDateClick != null) {
                    HorizontalDivider()
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        TrackDetailsItem(
                            modifier = Modifier.weight(1F),
                            text = startDate,
                            placeholder = stringResource(MR.strings.track_started_reading_date),
                            onClick = onStartDateClick,
                        )
                        VerticalDivider()
                        TrackDetailsItem(
                            modifier = Modifier.weight(1F),
                            text = endDate,
                            placeholder = stringResource(MR.strings.track_finished_reading_date),
                            onClick = onEndDateClick,
                        )
                    }
                }
            }
        }
    }
}

private const val UNSET_TEXT_ALPHA = 0.5F

@Composable
private fun TrackDetailsItem(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxHeight()
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text ?: placeholder,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (text == null) UNSET_TEXT_ALPHA else 1f),
        )
    }
}

@Composable
private fun TrackInfoItemEmpty(
    tracker: Tracker,
    onNewSearch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackLogoIcon(tracker)
        TextButton(
            onClick = onNewSearch,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(text = stringResource(MR.strings.add_tracking))
        }
    }
}

@Composable
private fun TrackInfoItemMenu(
    onOpenInBrowser: () -> Unit,
    onRemoved: () -> Unit,
    onCopyLink: () -> Unit,
    private: Boolean,
    onTogglePrivate: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(MR.strings.label_more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_open_in_browser)) },
                onClick = {
                    onOpenInBrowser()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_copy_link)) },
                onClick = {
                    onCopyLink()
                    expanded = false
                },
            )
            if (onTogglePrivate != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (private) {
                                    MR.strings.action_toggle_private_off
                                } else {
                                    MR.strings.action_toggle_private_on
                                },
                            ),
                        )
                    },
                    onClick = {
                        onTogglePrivate()
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_remove)) },
                onClick = {
                    onRemoved()
                    expanded = false
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TrackInfoDialogHomePreviews(
    @PreviewParameter(TrackInfoDialogHomePreviewProvider::class)
    content: @Composable () -> Unit,
) {
    TachiyomiPreviewTheme {
        Surface {
            content()
        }
    }
}

// KMK -->
@Composable
private fun UnifiedTrackerCard(
    trackItems: List<TrackItem>,
    preferredId: Long?,
    dateFormat: DateTimeFormatter,
    onAdjustProgress: (Int) -> Unit,
    busy: Boolean,
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    // KMK --> needed so untracked icons in the card can bind a 3rd+ tracker
    onNewSearch: (TrackItem) -> Unit,
    // KMK <--
    onRemoved: (TrackItem) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onCopyLink: (TrackItem) -> Unit,
    // KMK --> bulk removal goes through one confirmation; error ids badge failed refreshes
    onRemoveTracking: (List<TrackItem>) -> Unit = {},
    errorTrackerIds: Set<Long> = emptySet(),
    // KMK <--
) {
    val domainTracksForSync = remember(trackItems) { trackItems.mapNotNull { it.track } }
    val resolved = remember(domainTracksForSync, preferredId) {
        TrackerProgressSync.resolvePreferredTrack(domainTracksForSync, preferredId)
    }
    val primary = remember(trackItems, resolved) {
        resolved?.let { r -> trackItems.find { it.track?.trackerId == r.trackerId } } ?: trackItems.firstOrNull { it.track != null } ?: trackItems.firstOrNull()
    } ?: return
    val scoreItem = primary.takeIf { it.tracker.getScoreList().isNotEmpty() } ?: trackItems.firstOrNull { it.track != null && it.tracker.getScoreList().isNotEmpty() }
    val dateItem = primary.takeIf { it.tracker.supportsReadingDates } ?: trackItems.firstOrNull { it.track != null && it.tracker.supportsReadingDates }
    val displayTrack = primary.track
    val displayTracker = primary.tracker
    val isMismatched = remember(domainTracksForSync, preferredId) {
        TrackerProgressSync.mismatchedIds(domainTracksForSync, preferredId).isNotEmpty()
    }
    val statusText = displayTrack?.let { displayTracker.getStatus(it.status)?.let { stringResource(it) } } ?: "—"
    val scoreText = scoreItem?.track?.let { scoreItem.tracker.displayScore(it) }?.takeIf { it.isNotBlank() } ?: "—"
    val chaptersRead = displayTrack?.lastChapterRead?.toInt() ?: 0
    val totalChapters = displayTrack?.totalChapters ?: 0
    val chaptersText = if (totalChapters > 0) {
        "$chaptersRead/$totalChapters"
    } else if (chaptersRead > 0) {
        "$chaptersRead"
    } else {
        "0"
    }
    val startDate = dateItem?.track?.startDate?.takeIf { it != 0L }?.let { dateFormat.format(it.toLocalDate()) }
    val finishDate = dateItem?.track?.finishDate?.takeIf { it != 0L }?.let { dateFormat.format(it.toLocalDate()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            // KMK --> denser card to shorten the tracker menu
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // KMK --> per-tracker unsynced dot (vs preferred) + untracked icons bind via onNewSearch
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            trackItems.forEach { item ->
                val itemTrack = item.track
                val isUnsynced = itemTrack != null && resolved != null &&
                    abs(itemTrack.lastChapterRead - resolved.lastChapterRead) > 0.01
                // KMK --> refresh failure outranks the unsynced dot; legend explains both
                val isErrored = item.tracker.id in errorTrackerIds
                // KMK <--
                BadgedBox(
                    badge = {
                        if (isErrored) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        } else if (isUnsynced) {
                            Badge(containerColor = MaterialTheme.colorScheme.error)
                        }
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TrackLogoIcon(
                            tracker = item.tracker,
                            onClick = { if (itemTrack != null) onOpenInBrowser(item) else onNewSearch(item) },
                            onLongClick = if (itemTrack != null) ({ onCopyLink(item) }) else null,
                        )
                    }
                }
            }
        }
        // KMK <--
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .fillMaxWidth(),
            color = if (isMismatched) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !busy) { onStatusClick(primary) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
                    }
                    VerticalDivider()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !busy && scoreItem != null) { scoreItem?.let(onScoreClick) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = scoreText, style = MaterialTheme.typography.bodyMedium)
                            Text(text = "★", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.15f)
                            .clickable(enabled = !busy) { onAdjustProgress(-1) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "−", style = MaterialTheme.typography.titleMedium)
                    }
                    VerticalDivider()
                    Box(
                        modifier = Modifier
                            .weight(0.7f)
                            .clickable(enabled = !busy) { onChapterClick(primary) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = chaptersText, style = MaterialTheme.typography.bodyMedium)
                    }
                    VerticalDivider()
                    Box(
                        modifier = Modifier
                            .weight(0.15f)
                            .clickable(enabled = !busy) { onAdjustProgress(1) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "+", style = MaterialTheme.typography.titleMedium)
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.425f)
                            .clickable(enabled = !busy && dateItem != null) { dateItem?.let(onStartDateEdit) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = startDate ?: stringResource(MR.strings.track_started_reading_date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (startDate == null) UNSET_TEXT_ALPHA else 1f),
                        )
                    }
                    VerticalDivider()
                    Box(
                        modifier = Modifier
                            .weight(0.425f)
                            .clickable(enabled = !busy && dateItem != null) { dateItem?.let(onEndDateEdit) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = finishDate ?: stringResource(MR.strings.track_finished_reading_date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (finishDate == null) UNSET_TEXT_ALPHA else 1f),
                        )
                    }
                    VerticalDivider()
                    Box(Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { onRemoveTracking(trackItems.filter { it.track != null }) }, enabled = !busy, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(KMR.strings.track_remove_selection), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// KMK <--
