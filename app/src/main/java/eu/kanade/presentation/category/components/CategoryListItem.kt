package eu.kanade.presentation.category.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.CategoryManagerRow
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.track.TrackStatus
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowRight
import mihon.icons.materialsymbols.rounded.MoreVert
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
 * One row of the category tree: indented by depth, with an expand toggle for parents, the manga
 * count of the whole subtree, the Smart Category / update-interval settings, and an action menu.
 * Long-press starts selection; in selection mode a tap toggles the row.
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (selectionMode) actions.onToggleSelection(category) else actions.onRename(category)
                    },
                    onLongClick = { actions.onStartSelection(category) },
                )
                .padding(vertical = MaterialTheme.padding.extraSmall)
                .padding(start = MaterialTheme.padding.extraSmall, end = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { actions.onToggleSelection(category) })
            }
            if (row.hasChildren) {
                IconButton(onClick = { actions.onToggleCollapsed(category) }) {
                    Icon(
                        imageVector = if (row.collapsed) {
                            MaterialSymbols.Rounded.KeyboardArrowRight
                        } else {
                            MaterialSymbols.Rounded.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (row.collapsed) KMR.strings.category_expand else KMR.strings.category_collapse,
                        ),
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            if (showDragHandle) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.DragHandle,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = MaterialTheme.padding.small)
                        .draggableHandle(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
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
            Text(
                text = row.mangaCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
            )
            if (!selectionMode) RowMenu(category, actions)
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
            val entries = listOf(
                stringResource(MR.strings.action_rename_category) to actions.onRename,
                stringResource(KMR.strings.category_create_subcategory) to actions.onCreateSubcategory,
                stringResource(KMR.strings.category_move) to actions.onMove,
                stringResource(KMR.strings.category_track_status) to actions.onTrackStatus,
                stringResource(KMR.strings.category_update_interval) to actions.onUpdateInterval,
                stringResource(if (category.hidden) KMR.strings.action_show else KMR.strings.action_hide) to
                    actions.onHide,
                stringResource(MR.strings.action_delete) to actions.onDelete,
            )
            entries.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action(category)
                    },
                )
            }
        }
    }
}

private val INDENT = 16.dp
// KNS <--
