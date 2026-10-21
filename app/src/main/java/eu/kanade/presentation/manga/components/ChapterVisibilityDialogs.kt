package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowUp
import mihon.icons.materialsymbols.rounded.Palette
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.ScanlatorRangeRule
import tachiyomi.domain.chapter.model.parseScanlatorRangeRule
import tachiyomi.domain.chapter.service.chapterBlacklistKeyMetadata
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

// KNS -->
/** The fixed bookmark palette (same colors as the previous personal fork); null for an ordinary bookmark. */
fun BookmarkColor.composeColor(): Color? = when (this) {
    BookmarkColor.NONE -> null
    BookmarkColor.RED -> Color(0xFFD32F2F)
    BookmarkColor.ORANGE -> Color(0xFFE64A19)
    BookmarkColor.YELLOW -> Color(0xFFF9A825)
    BookmarkColor.GREEN -> Color(0xFF2E7D32)
    BookmarkColor.BLUE -> Color(0xFF1565C0)
    BookmarkColor.PURPLE -> Color(0xFF6A1B9A)
}

/** The stored color id of a chapter as a color; null for an ordinary bookmark. */
fun bookmarkColorOf(value: Int): Color? = BookmarkColor.fromValue(value).composeColor()

@Composable
fun BookmarkColor.label(): String = stringResource(
    when (this) {
        BookmarkColor.NONE -> KMR.strings.bookmark_color_default
        BookmarkColor.RED -> KMR.strings.bookmark_color_red
        BookmarkColor.ORANGE -> KMR.strings.bookmark_color_orange
        BookmarkColor.YELLOW -> KMR.strings.bookmark_color_yellow
        BookmarkColor.GREEN -> KMR.strings.bookmark_color_green
        BookmarkColor.BLUE -> KMR.strings.bookmark_color_blue
        BookmarkColor.PURPLE -> KMR.strings.bookmark_color_purple
    },
)

/**
 * Shared bookmark-color tri-state editor. Changes are kept locally until OK, so cancellation and
 * dismissal never mutate a screen's saved filter.
 */
@Composable
fun BookmarkColorFilterDialog(
    included: Set<BookmarkColor>,
    excluded: Set<BookmarkColor>,
    onChanged: (included: Set<BookmarkColor>, excluded: Set<BookmarkColor>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    TriStateListDialog(
        title = stringResource(KMR.strings.bookmark_color_filter),
        message = stringResource(KMR.strings.bookmark_color_filter_summary),
        items = BookmarkColor.entries,
        initialChecked = BookmarkColor.entries.filter { it in included },
        initialInversed = BookmarkColor.entries.filter { it in excluded },
        itemLabel = BookmarkColor::label,
        itemLeadingContent = { color ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color.composeColor() ?: MaterialTheme.colorScheme.primary),
            )
        },
        onDismissRequest = onDismissRequest,
        onValueChanged = { newIncluded, newExcluded ->
            onChanged(newIncluded.toSet(), newExcluded.toSet())
        },
    )
}

/** A Bookmarked tri-state control with an independent color-editor action. */
@Composable
fun BookmarkColorFilterRow(
    state: TriState,
    onBookmarkedClick: (TriState) -> Unit,
    onEditClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TriStateItem(
                label = stringResource(MR.strings.action_filter_bookmarked),
                state = state,
                onClick = onBookmarkedClick,
            )
        }
        TextButton(onClick = onEditClick) {
            Text(stringResource(MR.strings.action_edit))
        }
    }
}

/**
 * The manga's blacklist: each entry with the chapters it currently hides (an entry whose chapter is
 * gone shows as unavailable), removal, and whether blacklisted chapters are listed at all.
 */
@Composable
fun BlacklistedChaptersDialog(
    entries: List<Pair<String, List<String>>>,
    hideBlacklisted: Boolean,
    onHideBlacklistedChange: (Boolean) -> Unit,
    onRemove: (Collection<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.blacklisted_chapters)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHideBlacklistedChange(!hideBlacklisted) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = hideBlacklisted, onCheckedChange = onHideBlacklistedChange)
                    Text(stringResource(KMR.strings.hide_blacklisted_chapters))
                }
                HorizontalDivider()
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(KMR.strings.blacklist_empty),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                entries.forEach { (key, chapters) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(
                                text = chapters.joinToString().ifEmpty { blacklistEntryFallback(key) },
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
                        IconButton(onClick = { onRemove(listOf(key)) }) {
                            Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(MR.strings.action_delete))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_ok)) }
        },
    )
}

/**
 * Chapter-range rules: inside a range, the chosen scanlator is preferred over the priority order (the
 * last matching rule wins). Rules can be added with a from/to chapter and a scanlator, and removed.
 */
@Composable
private fun ScanlatorRangeRules(scanlators: List<String>, rules: SnapshotStateList<ScanlatorRangeRule>) {
    Text(stringResource(KMR.strings.scanlator_range_rules), style = MaterialTheme.typography.titleSmall)
    Text(
        text = stringResource(KMR.strings.scanlator_range_rules_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    rules.forEachIndexed { index, rule ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    KMR.strings.scanlator_range_rule,
                    formatChapterNumber(rule.from),
                    formatChapterNumber(rule.to),
                    rule.scanlator,
                ),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { rules.removeAt(index) }) {
                Icon(MaterialSymbols.Rounded.Delete, contentDescription = stringResource(MR.strings.action_delete))
            }
        }
    }
    if (scanlators.isEmpty()) return
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var scanlator by remember(scanlators) { mutableStateOf(scanlators.first()) }
    var expanded by remember { mutableStateOf(false) }
    val newRule = parseScanlatorRangeRule("${from.trim()}:${to.trim()}:$scanlator")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = from,
            onValueChange = { from = it },
            label = { Text(stringResource(KMR.strings.scanlator_range_from)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = to,
            onValueChange = { to = it },
            label = { Text(stringResource(KMR.strings.scanlator_range_to)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            TextButton(onClick = { expanded = true }) {
                Text(scanlator, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                scanlators.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            scanlator = option
                            expanded = false
                        },
                    )
                }
            }
        }
        TextButton(
            onClick = {
                newRule?.let { rules.add(it) }
                from = ""
                to = ""
            },
            enabled = newRule != null,
        ) { Text(stringResource(KMR.strings.scanlator_range_add)) }
    }
}

private fun formatChapterNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

/** A readable name for an entry whose chapter is no longer listed by the source. */
private fun blacklistEntryFallback(key: String): String {
    val (number, scanlator) = chapterBlacklistKeyMetadata(key)
    return listOfNotNull(number?.let { "#$it" }, scanlator).joinToString(" · ")
        .ifEmpty { key.substringAfterLast('/').ifEmpty { key } }
}

/**
 * Per-manga scanlator preferences: the Smart Scanlator Merge override (global default / on / off,
 * with the setting that currently applies) and the order in which scanlators are preferred.
 */
@Composable
fun ScanlatorPreferencesDialog(
    scanlators: List<String>,
    priority: List<String>,
    smartMergeOverride: Boolean?,
    globalSmartMerge: Boolean,
    rangeRules: List<ScanlatorRangeRule>,
    onConfirm: (priority: List<String>, smartMergeOverride: Boolean?, rangeRules: List<ScanlatorRangeRule>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val ordered = remember(scanlators, priority) {
        (priority.filter { it in scanlators } + scanlators.filterNot { it in priority }.sortedWith(String.CASE_INSENSITIVE_ORDER))
            .toMutableStateList()
    }
    var override by remember { mutableStateOf(smartMergeOverride) }
    val rules = remember(rangeRules) { rangeRules.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.scanlator_preferences)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(KMR.strings.action_smart_scanlator_merge), style = MaterialTheme.typography.titleSmall)
                listOf(
                    null to KMR.strings.scanlator_merge_global,
                    true to KMR.strings.scanlator_merge_enabled,
                    false to KMR.strings.scanlator_merge_disabled,
                ).forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { override = value },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = override == value, onClick = { override = value })
                        Text(stringResource(label))
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(stringResource(KMR.strings.scanlator_priority), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(KMR.strings.scanlator_priority_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ordered.isEmpty()) {
                    Text(stringResource(MR.strings.no_scanlators_found), modifier = Modifier.padding(vertical = 8.dp))
                }
                ordered.forEachIndexed { index, scanlator ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}. $scanlator",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { ordered.add(index - 1, ordered.removeAt(index)) },
                            enabled = index > 0,
                        ) {
                            Icon(MaterialSymbols.Rounded.KeyboardArrowUp, stringResource(KMR.strings.action_move_up))
                        }
                        IconButton(
                            onClick = { ordered.add(index + 1, ordered.removeAt(index)) },
                            enabled = index < ordered.lastIndex,
                        ) {
                            Icon(MaterialSymbols.Rounded.KeyboardArrowDown, stringResource(KMR.strings.action_move_down))
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ScanlatorRangeRules(scanlators = ordered, rules = rules)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ordered.toList(), override, rules.toList()) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}
// KNS <--
