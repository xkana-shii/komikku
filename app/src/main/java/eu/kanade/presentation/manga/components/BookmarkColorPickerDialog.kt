package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
@Composable
fun BookmarkColorPickerDialog(
    current: BookmarkColor,
    onSelect: (BookmarkColor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.bookmark_color_dialog_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.size(width = 248.dp, height = 132.dp),
            ) {
                items(BookmarkColor.entries) { color ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = color.composeColor() ?: MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                            )
                            .border(
                                width = if (color == current) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { onSelect(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            color == current -> Icon(
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
// KMK <--
