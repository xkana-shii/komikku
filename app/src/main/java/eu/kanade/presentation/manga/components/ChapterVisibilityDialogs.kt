package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource

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

/**
 * Shared bookmark-color tri-state editor. Changes are kept locally until OK, so cancellation and
 * dismissal never mutate a screen's saved filter.
 */
@Composable
fun BookmarkColorFilterDialog(
    included: Set<BookmarkColor>,
    excluded: Set<BookmarkColor>,
    onChanged: (included: Set<BookmarkColor>, excluded: Set<BookmarkColor>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    TriStateListDialog(
        title = stringResource(KMR.strings.bookmark_color_filter),
        message = stringResource(KMR.strings.bookmark_color_filter_summary),
        items = BookmarkColor.entries,
        initialChecked = BookmarkColor.entries.filter { it in included },
        initialInversed = BookmarkColor.entries.filter { it in excluded },
        itemLabel = BookmarkColor::label,
        itemLeadingContent = { color ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color.composeColor() ?: MaterialTheme.colorScheme.primary),
            )
        },
        onDismissRequest = onDismissRequest,
        onValueChanged = { newIncluded, newExcluded ->
            onChanged(newIncluded.toSet(), newExcluded.toSet())
        },
    )
}

/** A Bookmarked tri-state control with an independent color-editor action. */
@Composable
fun BookmarkColorFilterRow(
    state: TriState,
    onBookmarkedClick: (TriState) -> Unit,
    onEditClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TriStateItem(
                label = stringResource(MR.strings.action_filter_bookmarked),
                state = state,
                onClick = onBookmarkedClick,
            )
        }
        TextButton(onClick = onEditClick) {
            Text(stringResource(MR.strings.action_edit))
        }
    }
}

// KNS <--
