package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
@Composable
fun BookmarkColorDropdownMenu(
    expanded: Boolean,
    selected: BookmarkColor?,
    onSelect: (BookmarkColor) -> Unit,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        BookmarkColor.entries.forEach { color ->
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(color.composeColor() ?: MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Text(color.label())
                    }
                },
                onClick = { onSelect(color) },
            )
        }
    }
}

@Composable
private fun BookmarkColor.label(): String = when (this) {
    BookmarkColor.NONE -> stringResource(KMR.strings.bookmark_color_default)
    BookmarkColor.RED -> stringResource(KMR.strings.bookmark_color_red)
    BookmarkColor.ORANGE -> stringResource(KMR.strings.bookmark_color_orange)
    BookmarkColor.YELLOW -> stringResource(KMR.strings.bookmark_color_yellow)
    BookmarkColor.GREEN -> stringResource(KMR.strings.bookmark_color_green)
    BookmarkColor.BLUE -> stringResource(KMR.strings.bookmark_color_blue)
    BookmarkColor.PURPLE -> stringResource(KMR.strings.bookmark_color_purple)
}
// KMK <--
