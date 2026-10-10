package eu.kanade.presentation.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.manga.components.composeColor
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

// KMK -->
@Composable
fun BookmarkColorFilterItem(
    selected: Set<BookmarkColor>,
    onChanged: (Set<BookmarkColor>) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clickable { showDialog = true }
            .fillMaxWidth()
            .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Palette,
            contentDescription = null,
            tint = if (selected.isNotEmpty()) MaterialTheme.colorScheme.active else LocalContentColor.current,
        )
        Text(
            text = stringResource(KMR.strings.bookmark_color_filter),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(KMR.strings.bookmark_color_filter)) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.size(width = 248.dp, height = 132.dp),
                ) {
                    items(BookmarkColor.entries) { color ->
                        val selectedColor = color in selected
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = color.composeColor() ?: MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                )
                                .border(
                                    width = if (selectedColor) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    onChanged(if (selectedColor) selected - color else selected + color)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                selectedColor -> Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (color == BookmarkColor.NONE) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        Color.White
                                    },
                                )
                                color == BookmarkColor.NONE -> Text(
                                    text = "–",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
// KMK <--
