package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import tachiyomi.domain.chapter.model.BookmarkColor

// KNS -->
/**
 * The long-press bookmark color menu of the chapter selection bars and the reader (the previous
 * personal fork's menu): every palette entry as a swatch and its name, with a check on [selected].
 * [selected] is null when there is no single current color (unbookmarked or mixed selections).
 */
@Composable
fun BookmarkColorDropdownMenu(
    expanded: Boolean,
    selected: BookmarkColor?,
    onSelect: (BookmarkColor) -> Unit,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        BookmarkColor.entries.forEach { color ->
            DropdownMenuItem(
                text = { BookmarkColorLabel(color) },
                trailingIcon = {
                    if (color == selected) {
                        Icon(imageVector = MaterialSymbols.Rounded.Check, contentDescription = null)
                    }
                },
                onClick = { onSelect(color) },
            )
        }
    }
}

/** A palette entry as its swatch and name, spaced like the bookmark color menu. */
@Composable
fun BookmarkColorLabel(color: BookmarkColor, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookmarkColorSwatch(color)
        Text(color.label())
    }
}

/** A 20dp rounded square in the bookmark color; the ordinary bookmark uses the surface variant. */
@Composable
fun BookmarkColorSwatch(color: BookmarkColor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(MaterialTheme.shapes.small)
            .background(color.composeColor() ?: MaterialTheme.colorScheme.surfaceVariant),
    )
}
// KNS <--
