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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowUp
import mihon.icons.materialsymbols.rounded.Palette
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.service.chapterBlacklistKeyMetadata
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
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

/** Picks a bookmark color; choosing one also bookmarks the chapters. */
@Composable
fun BookmarkColorDialog(
    current: BookmarkColor?,
    onSelect: (BookmarkColor) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.action_bookmark_color)) },
        text = {
            Column {
                BookmarkColor.entries.forEach { color ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(color) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = color == current, onClick = { onSelect(color) })
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(color.composeColor() ?: MaterialTheme.colorScheme.primary),
                        )
                        Text(color.label())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

/** Chooses bookmark colors to filter by; an empty choice turns the filter off. */
@Composable
fun BookmarkColorFilterDialog(
    selected: Set<BookmarkColor>,
    onChanged: (Set<BookmarkColor>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(KMR.strings.bookmark_color_filter)) },
        text = {
            Column {
                Text(
                    text = stringResource(KMR.strings.bookmark_color_filter_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BookmarkColor.entries.forEach { color ->
                    val checked = color in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChanged(if (checked) selected - color else selected + color) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onChanged(if (checked) selected - color else selected + color) },
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(color.composeColor() ?: MaterialTheme.colorScheme.primary),
                        )
                        Text(color.label())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onChanged(emptySet()) }) { Text(stringResource(MR.strings.action_reset)) }
        },
    )
}

/** A filter row that opens [BookmarkColorFilterDialog]; shows the chosen colors and is highlighted while active. */
@Composable
fun BookmarkColorFilterItem(
    selected: Set<BookmarkColor>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Palette,
            contentDescription = null,
            tint = if (selected.isNotEmpty()) MaterialTheme.colorScheme.active else LocalContentColor.current,
        )
        Text(
            text = stringResource(KMR.strings.bookmark_color_filter),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        BookmarkColor.entries.filter { it in selected }.forEach { color ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color.composeColor() ?: MaterialTheme.colorScheme.primary),
            )
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
    onConfirm: (priority: List<String>, smartMergeOverride: Boolean?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val ordered = remember(scanlators, priority) {
        (priority.filter { it in scanlators } + scanlators.filterNot { it in priority }.sortedWith(String.CASE_INSENSITIVE_ORDER))
            .toMutableStateList()
    }
    var override by remember { mutableStateOf(smartMergeOverride) }
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ordered.toList(), override) }) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}
// KNS <--
