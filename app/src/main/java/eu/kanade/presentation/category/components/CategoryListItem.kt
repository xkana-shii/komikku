package eu.kanade.presentation.category.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
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
    modifier: Modifier = Modifier,
    category: Category,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    indentLevel: Int = 0,
    isParent: Boolean = false,
    parentCategory: Category? = null,
    // KMK --> Add expand/collapse parameters
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    // KMK <--
) {
    if (isParent && indentLevel == 0) {
        // Parent category - container card layout
        ParentCategoryContainer(
            category = category,
            onRename = onRename,
            onDelete = onDelete,
            onHide = onHide,
            modifier = modifier,
            // KMK -->
            hasChildren = hasChildren,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand,
            // KMK <--
        )
    } else {
        // Child category - compact layout
        ChildCategoryRow(
            category = category,
            onRename = onRename,
            onDelete = onDelete,
            onHide = onHide,
            indentLevel = indentLevel,
            parentCategory = parentCategory,
            modifier = modifier,
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.ParentCategoryContainer(
    category: Category,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    // KMK -->
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    // KMK <--
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column {
            // Parent header with drag handle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.padding.small)
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Drag handle
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = MaterialTheme.padding.medium)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // KMK --> Folder icon for open/closed state
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Filled.FolderOpen
                    } else {
                        Icons.Outlined.Folder
                    },
                    contentDescription = if (isExpanded) {
                        "Folder Open"
                    } else {
                        "Folder Closed"
                    },
                    modifier = Modifier
                        .padding(end = MaterialTheme.padding.medium)
                        .size(24.dp)
                        .clickable(enabled = hasChildren) { onToggleExpand() },
                    tint = MaterialTheme.colorScheme.primary,
                )
                // KMK <--

                // Category name (not clickable - use edit button)
                Text(
                    text = category.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.let {
                        if (category.hidden) it.copy(alpha = 0.6f) else it
                    },
                    textDecoration = TextDecoration.LineThrough.takeIf { category.hidden },
                )

                // Action buttons
                IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(MR.strings.action_rename_category),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onHide, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (category.hidden) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = stringResource(KMR.strings.action_hide),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(MR.strings.action_delete),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.ChildCategoryRow(
    modifier: Modifier = Modifier,
    category: Category,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    indentLevel: Int = 0,
    parentCategory: Category? = null,
) {
    val startIndent = 8.dp + (indentLevel.coerceAtLeast(0) * 16).dp

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startIndent, end = MaterialTheme.padding.small)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Tree connector line visual
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(1.dp),
                    )
                    .padding(end = 8.dp),
            )

            // Category name with strikethrough if hidden
            Text(
                text = category.name,
                modifier = Modifier
                    .padding(start = 8.dp, end = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.let {
                    if (category.hidden) it.copy(alpha = 0.6f) else it
                },
                textDecoration = TextDecoration.LineThrough.takeIf { category.hidden },
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            // Action buttons (compact)
            IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_rename_category),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onHide, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (category.hidden) {
                        Icons.Outlined.Visibility
                    } else {
                        Icons.Outlined.VisibilityOff
                    },
                    contentDescription = stringResource(KMR.strings.action_hide),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
