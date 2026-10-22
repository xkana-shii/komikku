package eu.kanade.presentation.category.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.category.CategoryManagerRow
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.track.TrackStatus
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CreateNewFolder
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.DriveFileMove
import mihon.icons.materialsymbols.rounded.Edit
import mihon.icons.materialsymbols.rounded.Flag
import mihon.icons.materialsymbols.rounded.Folder
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowUp
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.roundedfilled.Visibility
import mihon.icons.materialsymbols.roundedfilled.VisibilityOff
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
/** Per-row actions of the category manager. */
class CategoryRowActions(
    val onRename: (Category) -> Unit,
    val onDelete: (Category) -> Unit,
    val onHide: (Category) -> Unit,
    val onCreateSubcategory: (Category) -> Unit,
    val onMove: (Category) -> Unit,
    val onTrackStatus: (Category) -> Unit,
    val onUpdateInterval: (Category) -> Unit,
    val onToggleCollapsed: (Category) -> Unit,
    val onToggleSelection: (Category) -> Unit,
    val onStartSelection: (Category) -> Unit,
)

/**
 * One row of the category tree, laid out as on the previous personal fork: drag handle, expand toggle,
 * a folder with the number of subcategories, the name with the manga count of the whole subtree, and
 * the actions as icons (in a menu when the row is narrow). Rows are indented by depth; Smart Category
 * and update-interval settings show under the name. Long-press starts selection; in selection mode a
 * tap toggles the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    row: CategoryManagerRow,
    actions: CategoryRowActions,
    showDragHandle: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val category = row.category
    ElevatedCard(modifier = modifier.padding(start = INDENT * row.depth)) {
        BoxWithConstraints {
            val useOverflow = maxWidth < INLINE_ACTIONS_BREAKPOINT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) actions.onToggleSelection(category) else actions.onRename(category)
                        },
                        onLongClick = { actions.onStartSelection(category) },
                    )
                    .padding(vertical = MaterialTheme.padding.small)
                    .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showDragHandle) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.DragHandle,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(MaterialTheme.padding.medium)
                            .draggableHandle(),
                    )
                }
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { actions.onToggleSelection(category) })
                }
                if (row.hasChildren) {
                    IconButton(onClick = { actions.onToggleCollapsed(category) }) {
                        Icon(
                            imageVector = if (row.collapsed) {
                                MaterialSymbols.Rounded.KeyboardArrowDown
                            } else {
                                MaterialSymbols.Rounded.KeyboardArrowUp
                            },
                            contentDescription = stringResource(
                                if (row.collapsed) KMR.strings.category_expand else KMR.strings.category_collapse,
                            ),
                        )
                    }
                    BadgedBox(
                        badge = { Badge { Text(row.childCount.toString()) } },
                        modifier = Modifier.padding(end = MaterialTheme.padding.small),
                    ) {
                        Icon(MaterialSymbols.Rounded.Folder, contentDescription = null)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (row.mangaCount > 0) {
                            "${BidiFormatter.getInstance().unicodeWrap(category.name)} (${row.mangaCount})"
                        } else {
                            category.name
                        },
                        color = LocalContentColor.current.let { if (category.hidden) it.copy(alpha = 0.6f) else it },
                        textDecoration = TextDecoration.LineThrough.takeIf { category.hidden },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val details = listOfNotNull(
                        row.path,
                        category.trackStatus?.let { status ->
                            TrackStatus.entries.firstOrNull { it.int == status }?.let { stringResource(it.res) }
                        },
                        category.updateInterval?.let {
                            pluralStringResource(KMR.plurals.category_update_interval_days, it, it)
                        },
                    )
                    if (details.isNotEmpty()) {
                        Text(
                            text = details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when {
                    selectionMode -> Unit
                    useOverflow -> RowMenu(category, actions)
                    else -> InlineActions(category, actions)
                }
            }
        }
    }
}

/** The row's actions in the previous personal fork's order. */
private fun rowActionEntries(category: Category, actions: CategoryRowActions) = listOf(
    RowAction(MR.strings.action_rename_category, MaterialSymbols.Rounded.Edit, actions.onRename),
    RowAction(
        KMR.strings.category_create_subcategory,
        MaterialSymbols.Rounded.CreateNewFolder,
        actions.onCreateSubcategory,
    ),
    RowAction(KMR.strings.category_move, MaterialSymbols.Rounded.DriveFileMove, actions.onMove),
    RowAction(
        if (category.hidden) KMR.strings.action_show else KMR.strings.action_hide,
        if (category.hidden) MaterialSymbols.RoundedFilled.Visibility else MaterialSymbols.RoundedFilled.VisibilityOff,
        actions.onHide,
    ),
    RowAction(KMR.strings.category_track_status, MaterialSymbols.Rounded.Flag, actions.onTrackStatus, dimmed = category.trackStatus == null),
    RowAction(KMR.strings.category_update_interval, MaterialSymbols.Rounded.Schedule, actions.onUpdateInterval),
    RowAction(MR.strings.action_delete, MaterialSymbols.Rounded.Delete, actions.onDelete),
)

private class RowAction(
    val label: StringResource,
    val icon: ImageVector,
    val onClick: (Category) -> Unit,
    /** A setting that is off, e.g. the flag of a category that is not a Smart Category. */
    val dimmed: Boolean = false,
)

@Composable
private fun InlineActions(category: Category, actions: CategoryRowActions) {
    rowActionEntries(category, actions).forEach { action ->
        IconButton(onClick = { action.onClick(category) }) {
            Icon(
                imageVector = action.icon,
                contentDescription = stringResource(action.label),
                tint = LocalContentColor.current.let { if (action.dimmed) it.copy(alpha = 0.6f) else it },
            )
        }
    }
}

@Composable
private fun RowMenu(category: Category, actions: CategoryRowActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = MaterialSymbols.Rounded.MoreVert,
                contentDescription = stringResource(MR.strings.action_menu_overflow_description),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val entries = rowActionEntries(category, actions)
            entries.forEachIndexed { index, action ->
                if (index == entries.lastIndex) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        action.onClick(category)
                    },
                )
            }
        }
    }
}

private val INDENT = 16.dp
private val INLINE_ACTIONS_BREAKPOINT = 420.dp
// KNS <--
