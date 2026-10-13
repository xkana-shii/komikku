package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
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
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// KMK --> Adapted from Houri's responsive category-manager row.
@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onMove: () -> Unit,
    onCreateSubcategory: (() -> Unit)? = null,
    isTopLevel: Boolean = true,
    subcategoryCount: Int = 0,
    showDragHandle: Boolean = true,
    mangaCount: Int = 0,
    expanded: Boolean? = null,
    onToggleExpand: (() -> Unit)? = null,
    selected: Boolean? = null,
    onToggleSelection: (() -> Unit)? = null,
    onClickTrackStatus: (() -> Unit)? = null,
    onClickUpdateInterval: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        BoxWithConstraints {
            val useOverflow = maxWidth < INLINE_ACTIONS_BREAKPOINT

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { (onToggleSelection ?: onRename)() })
                    .padding(vertical = MaterialTheme.padding.small)
                    .padding(
                        start = MaterialTheme.padding.small,
                        end = MaterialTheme.padding.medium,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showDragHandle) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(MaterialTheme.padding.medium)
                            .draggableHandle(),
                    )
                }
                if (selected != null && onToggleSelection != null) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
                }
                if (expanded != null && onToggleExpand != null) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                if (isTopLevel && subcategoryCount > 0) {
                    BadgedBox(
                        badge = { Badge { Text(subcategoryCount.toString()) } },
                        modifier = Modifier.padding(end = MaterialTheme.padding.small),
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                    }
                }
                Text(
                    text = if (mangaCount > 0) {
                        "${BidiFormatter.getInstance().unicodeWrap(category.name)} ($mangaCount)"
                    } else {
                        category.name
                    },
                    color = LocalContentColor.current.let { if (category.hidden) it.copy(alpha = 0.6f) else it },
                    textDecoration = TextDecoration.LineThrough.takeIf { category.hidden },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (useOverflow) {
                    CategoryActionsOverflow(
                        category = category,
                        onRename = onRename,
                        onHide = onHide,
                        onMove = onMove,
                        onDelete = onDelete,
                        onCreateSubcategory = onCreateSubcategory?.takeIf { isTopLevel },
                        onClickTrackStatus = onClickTrackStatus,
                        onClickUpdateInterval = onClickUpdateInterval,
                    )
                } else {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Outlined.Edit, stringResource(MR.strings.action_rename_category))
                    }
                    if (isTopLevel && onCreateSubcategory != null) {
                        IconButton(onClick = onCreateSubcategory) {
                            Icon(Icons.Outlined.CreateNewFolder, stringResource(KMR.strings.action_create_subcategory))
                        }
                    }
                    IconButton(onClick = onMove) {
                        Icon(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(KMR.strings.category_move))
                    }
                    IconButton(onClick = onHide) {
                        Icon(
                            imageVector = if (category.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = stringResource(KMR.strings.action_hide),
                        )
                    }
                    onClickTrackStatus?.let { onClick ->
                        IconButton(onClick = onClick) {
                            Icon(
                                imageVector = Icons.Outlined.Flag,
                                contentDescription = stringResource(KMR.strings.category_track_status),
                                tint = LocalContentColor.current.let {
                                    if (category.trackStatus != null) it else it.copy(alpha = 0.6f)
                                },
                            )
                        }
                    }
                    onClickUpdateInterval?.let { onClick ->
                        IconButton(onClick = onClick) {
                            Icon(Icons.Outlined.Schedule, stringResource(KMR.strings.category_update_interval))
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, stringResource(MR.strings.action_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryActionsOverflow(
    category: Category,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCreateSubcategory: (() -> Unit)?,
    onClickTrackStatus: (() -> Unit)?,
    onClickUpdateInterval: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Outlined.MoreVert, stringResource(MR.strings.action_menu_overflow_description))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        CategoryActionItem(MR.strings.action_rename_category, Icons.Outlined.Edit, onRename) { expanded = false }
        onCreateSubcategory?.let {
            CategoryActionItem(KMR.strings.action_create_subcategory, Icons.Outlined.CreateNewFolder, it) { expanded = false }
        }
        CategoryActionItem(KMR.strings.category_move, Icons.AutoMirrored.Outlined.DriveFileMove, onMove) { expanded = false }
        onClickTrackStatus?.let {
            CategoryActionItem(KMR.strings.category_track_status, Icons.Outlined.Flag, it) { expanded = false }
        }
        onClickUpdateInterval?.let {
            CategoryActionItem(KMR.strings.category_update_interval, Icons.Outlined.Schedule, it) { expanded = false }
        }
        CategoryActionItem(
            KMR.strings.action_hide,
            if (category.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            onHide,
        ) { expanded = false }
        HorizontalDivider()
        CategoryActionItem(MR.strings.action_delete, Icons.Outlined.Delete, onDelete) { expanded = false }
    }
}

@Composable
private fun CategoryActionItem(
    label: StringResource,
    icon: ImageVector,
    action: () -> Unit,
    close: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            close()
            action()
        },
    )
}

private val INLINE_ACTIONS_BREAKPOINT = 420.dp
// KMK <--
