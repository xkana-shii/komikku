package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft.Mode
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Block
import mihon.icons.materialsymbols.rounded.CheckBoxOutlineBlank
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.Group
import mihon.icons.materialsymbols.rounded.Public
import mihon.icons.materialsymbols.roundedfilled.Cancel
import mihon.icons.materialsymbols.roundedfilled.CheckBox
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import mihon.icons.materialsymbols.roundedfilled.Visibility
import mihon.icons.materialsymbols.roundedfilled.VisibilityOff
import sh.calvin.reorderable.ReorderableColumn
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ScanlatorCoverage
import tachiyomi.domain.chapter.model.scanlatorCoverage
import tachiyomi.domain.chapter.service.chapterBlacklistKeyMetadata
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
/**
 * Per-manga scanlator preferences and chapter blacklist in one dialog with two modes, switched with
 * the icon in the title. Both modes edit one [ScanlatorPreferencesDraft]; every edit is handed to
 * [onChanges] as it happens (a drag once it is dropped), so there is no Save step and closing the
 * dialog keeps everything.
 */
@Composable
fun ScanlatorPreferencesDialog(
    initial: ScanlatorPreferencesDraft,
    globalSmartMerge: Boolean,
    hideBlacklisted: Boolean,
    onHideBlacklistedChange: (Boolean) -> Unit,
    onChanges: (List<ScanlatorPreferencesDraft.Change>) -> Unit,
    onDismissRequest: () -> Unit,
    /** Each scanlator's chapters (all of them, not only the visible ones), as the old `main` showed. */
    scanlatorStats: Map<String, ScanlatorStats> = emptyMap(),
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val update: (ScanlatorPreferencesDraft) -> Unit = { next ->
        val changes = next.changesFrom(draft)
        draft = next
        if (changes.isNotEmpty()) onChanges(changes)
    }
    // One scroll position per mode, so switching back returns to where the user was.
    val scanlatorScroll = rememberScrollState()
    val blacklistScroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (draft.mode == Mode.Scanlators) KMR.strings.scanlator_preferences else KMR.strings.blacklisted_chapters,
                    ),
                    modifier = Modifier.weight(1f),
                )
                val (switchIcon, switchLabel) = if (draft.mode == Mode.Scanlators) {
                    MaterialSymbols.Rounded.Block to stringResource(KMR.strings.blacklisted_chapters)
                } else {
                    MaterialSymbols.Rounded.Group to stringResource(KMR.strings.scanlator_preferences)
                }
                TooltipIconButton(icon = switchIcon, label = switchLabel, onClick = { update(draft.switchMode()) })
            }
        },
        text = {
            when (draft.mode) {
                Mode.Scanlators -> Column(modifier = Modifier.verticalScroll(scanlatorScroll)) {
                    ScanlatorPreferencesContent(
                        draft = draft,
                        globalSmartMerge = globalSmartMerge,
                        scanlatorStats = scanlatorStats,
                        onDraftChange = update,
                    )
                }
                Mode.Blacklist -> Column(modifier = Modifier.verticalScroll(blacklistScroll)) {
                    BlacklistContent(
                        draft = draft,
                        hideBlacklisted = hideBlacklisted,
                        onHideBlacklistedChange = onHideBlacklistedChange,
                        onDraftChange = update,
                    )
                }
            }
        },
        // Changes are already stored: closing only dismisses.
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_close)) }
        },
    )
}

/** Smart Scanlator Merge override, the drag-sorted scanlator priority with exclusions, and ranges. */
@Composable
private fun ScanlatorPreferencesContent(
    draft: ScanlatorPreferencesDraft,
    globalSmartMerge: Boolean,
    scanlatorStats: Map<String, ScanlatorStats>,
    onDraftChange: (ScanlatorPreferencesDraft) -> Unit,
) {
    SmartMergeSelector(
        override = draft.smartMergeOverride,
        globalSmartMerge = globalSmartMerge,
        onChange = { onDraftChange(draft.copy(smartMergeOverride = it)) },
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SectionHeader(KMR.strings.scanlator_priority, KMR.strings.scanlator_priority_summary)
    if (draft.order.isEmpty()) {
        Text(stringResource(MR.strings.no_scanlators_found), modifier = Modifier.padding(vertical = 8.dp))
    } else {
        ReorderableColumn(
            list = draft.order,
            onSettle = { from, to -> onDraftChange(draft.move(from, to)) },
            modifier = Modifier.padding(top = 4.dp),
        ) { _, scanlator, isDragging ->
            key(scanlator) {
                ReorderableItem {
                    val excluded = draft.isExcluded(scanlator)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = if (isDragging) 4.dp else 0.dp,
                        shadowElevation = if (isDragging) 4.dp else 0.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                Icon(MaterialSymbols.Rounded.DragHandle, contentDescription = null)
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                                    .alpha(if (excluded) DISABLED_ALPHA else 1f),
                            ) {
                                Text(
                                    text = scanlator,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                scanlatorStats[scanlator]?.let { ScanlatorStatsText(it) }
                            }
                            IconButton(onClick = { onDraftChange(draft.toggleExcluded(scanlator)) }) {
                                Icon(
                                    imageVector = if (excluded) {
                                        MaterialSymbols.RoundedFilled.VisibilityOff
                                    } else {
                                        MaterialSymbols.RoundedFilled.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (excluded) KMR.strings.scanlator_show else KMR.strings.scanlator_hide,
                                        scanlator,
                                    ),
                                    tint = if (excluded) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    ScanlatorRangeRules(draft = draft, onDraftChange = onDraftChange)
}

/** A scanlator's chapter count and the chapter numbers it covers, as the old `main` showed them. */
@Composable
private fun ScanlatorStatsText(stats: ScanlatorStats) {
    Text(
        text = stringResource(KMR.strings.scanlator_chapter_count, stats.chapterCount),
        style = MaterialTheme.typography.bodySmall,
    )
    stats.coverage?.let { coverage ->
        Text(
            text = coverage.groups.joinToString(", ") +
                if (coverage.hiddenCount == 0) {
                    ""
                } else {
                    ", " + stringResource(KMR.strings.scanlator_coverage_more, coverage.hiddenCount)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The three Smart Scanlator Merge override states as one row of small icon buttons. */
@Composable
private fun SmartMergeSelector(override: Boolean?, globalSmartMerge: Boolean, onChange: (Boolean?) -> Unit) {
    val title = stringResource(KMR.strings.action_smart_scanlator_merge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Row(modifier = Modifier.selectableGroup()) {
            listOf(
                Triple(null, MaterialSymbols.Rounded.Public, KMR.strings.scanlator_merge_global),
                Triple(true, MaterialSymbols.RoundedFilled.CheckCircle, KMR.strings.scanlator_merge_enabled),
                Triple(false, MaterialSymbols.RoundedFilled.Cancel, KMR.strings.scanlator_merge_disabled),
            ).forEach { (value, icon, label) ->
                val isSelected = override == value
                TooltipIconButton(
                    icon = icon,
                    label = "$title: ${stringResource(label)}",
                    onClick = { onChange(value) },
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { selected = isSelected },
                )
            }
        }
    }
    Text(
        text = stringResource(
            if (override ?: globalSmartMerge) {
                KMR.strings.scanlator_merge_effective_enabled
            } else {
                KMR.strings.scanlator_merge_effective_disabled
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Chapter-range rules: inside a range, the chosen scanlator is preferred over the priority order (the
 * last matching rule wins). Rules can be added with a from/to chapter and a scanlator, and removed.
 */
@Composable
private fun ScanlatorRangeRules(draft: ScanlatorPreferencesDraft, onDraftChange: (ScanlatorPreferencesDraft) -> Unit) {
    SectionHeader(KMR.strings.scanlator_range_rules, KMR.strings.scanlator_range_rules_summary)
    draft.rangeRules.forEachIndexed { index, rule ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    KMR.strings.scanlator_range_rule,
                    formatChapterNumber(rule.from),
                    formatChapterNumber(rule.to),
                    rule.scanlator,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { onDraftChange(draft.removeRangeRule(index)) }) {
                Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(MR.strings.action_delete))
            }
        }
    }
    val scanlator = draft.rangeScanlator ?: return
    val input = draft.rangeInput
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = input.from,
            onValueChange = { onDraftChange(draft.copy(rangeInput = input.copy(from = it))) },
            label = { Text(stringResource(KMR.strings.scanlator_range_from)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = input.to,
            onValueChange = { onDraftChange(draft.copy(rangeInput = input.copy(to = it))) },
            label = { Text(stringResource(KMR.strings.scanlator_range_to)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            TextButton(onClick = { expanded = true }) {
                Text(scanlator, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                draft.order.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onDraftChange(draft.copy(rangeInput = input.copy(scanlator = option)))
                            expanded = false
                        },
                    )
                }
            }
        }
        TextButton(
            onClick = { onDraftChange(draft.addPendingRangeRule()) },
            enabled = draft.pendingRangeRule != null,
        ) { Text(stringResource(KMR.strings.scanlator_range_add)) }
    }
}

/**
 * The manga's blacklist: whether blacklisted chapters are hidden, then each entry with the chapters it
 * currently hides (an entry whose chapter is gone shows as unavailable) and its removal.
 */
@Composable
private fun BlacklistContent(
    draft: ScanlatorPreferencesDraft,
    hideBlacklisted: Boolean,
    onHideBlacklistedChange: (Boolean) -> Unit,
    onDraftChange: (ScanlatorPreferencesDraft) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(KMR.strings.hide_blacklisted_chapters),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        IconToggleButton(checked = hideBlacklisted, onCheckedChange = onHideBlacklistedChange) {
            Icon(
                imageVector = if (hideBlacklisted) {
                    MaterialSymbols.RoundedFilled.CheckBox
                } else {
                    MaterialSymbols.Rounded.CheckBoxOutlineBlank
                },
                contentDescription = stringResource(
                    if (hideBlacklisted) KMR.strings.blacklist_hiding_enabled else KMR.strings.blacklist_hiding_disabled,
                ),
                tint = if (hideBlacklisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    val entries = draft.visibleBlacklist
    if (entries.isEmpty()) {
        Text(
            text = stringResource(KMR.strings.blacklist_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
    entries.forEach { (key, chapters) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    text = chapters.joinToString().ifEmpty { blacklistEntryFallback(key) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chapters.isEmpty()) {
                    Text(
                        text = stringResource(KMR.strings.blacklist_chapter_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onDraftChange(draft.removeBlacklistEntry(key)) }) {
                Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(MR.strings.action_delete))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: StringResource, summary: StringResource) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
    Text(
        text = stringResource(summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** An icon button whose label is both its content description and a long-press tooltip. */
@Composable
private fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        focusable = false,
    ) {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(imageVector = icon, contentDescription = label, tint = tint)
        }
    }
}

private fun formatChapterNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

/** A readable name for an entry whose chapter is no longer listed by the source. */
internal fun blacklistEntryFallback(key: String): String {
    val (number, scanlator) = chapterBlacklistKeyMetadata(key)
    return listOfNotNull(number?.let { "#$it" }, scanlator).joinToString(" · ")
        .ifEmpty { key.substringAfterLast('/').ifEmpty { key } }
}

/** All of a scanlator's chapters, whatever Smart Merge and the blacklist currently show. */
data class ScanlatorStats(val chapterCount: Int, val coverage: ScanlatorCoverage?)

/** [ScanlatorStats] per scanlator name, from every chapter of the manga. */
fun scanlatorStatsOf(chapters: List<Chapter>): Map<String, ScanlatorStats> =
    chapters.mapNotNull { chapter -> chapter.scanlator?.takeIf { it.isNotBlank() }?.let { it to chapter } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> ScanlatorStats(list.size, scanlatorCoverage(list.map(Chapter::chapterNumber))) }
// KNS <--
