package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    // KMK -->
    onHide: () -> Unit,
    onTrackStatus: () -> Unit,
    onUpdateInterval: () -> Unit,
    onCreateSubcategory: () -> Unit,
    onMove: () -> Unit,
    // KMK <--
    modifier: Modifier = Modifier,
    // KMK -->
    depth: Int = 0,
    mangaCount: Int = 0,
    expanded: Boolean = true,
    selectable: Boolean = false,
    selected: Boolean = false,
    hasChildren: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    onToggleSelected: () -> Unit = {},
    // KMK <--
) {
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename)
                .padding(vertical = MaterialTheme.padding.small)
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // KMK -->
            Row(modifier = Modifier.padding(start = (depth * 20).dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectable) {
                    IconButton(onClick = onToggleSelected) {
                        Icon(
                            imageVector = if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null,
                        )
                    }
                }
                if (hasChildren) {
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                    }
                }
            }
            // KMK <--
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(MaterialTheme.padding.medium)
                    .draggableHandle(),
            )
            Text(
                text = category.name,
                // KMK -->
                color = LocalContentColor.current.let { if (category.hidden) it.copy(alpha = 0.6f) else it },
                textDecoration = TextDecoration.LineThrough.takeIf { category.hidden },
                // KMK <--
                modifier = Modifier.weight(1f),
            )
            // KMK -->
            if (mangaCount > 0) Text(text = mangaCount.toString(), style = MaterialTheme.typography.labelMedium)
            // KMK <--
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_rename_category),
                )
            }
            // KMK -->
            IconButton(
                onClick = onHide,
                content = {
                    Icon(
                        imageVector = if (category.hidden) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = stringResource(KMR.strings.action_hide),
                    )
                },
            )
            // KMK <--
            // KMK -->
            IconButton(onClick = onTrackStatus) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = stringResource(KMR.strings.category_track_status),
                    tint = LocalContentColor.current.let {
                        if (category.trackStatus == null) it.copy(alpha = 0.6f) else it
                    },
                )
            }
            IconButton(onClick = onUpdateInterval) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = stringResource(KMR.strings.category_update_interval),
                )
            }
            IconButton(onClick = onCreateSubcategory) {
                Icon(
                    imageVector = Icons.Outlined.CreateNewFolder,
                    contentDescription = stringResource(KMR.strings.category_create_subcategory),
                )
            }
            IconButton(onClick = onMove) {
                Icon(
                    imageVector = Icons.Outlined.DriveFileMove,
                    contentDescription = stringResource(KMR.strings.category_move),
                )
            }
            // KMK <--
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }
    }
}
