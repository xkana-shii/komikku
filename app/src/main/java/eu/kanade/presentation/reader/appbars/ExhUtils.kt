package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.BookmarkColorDropdownMenu
import eu.kanade.presentation.manga.components.bookmarkColorOf
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.RocketLaunch
import mihon.icons.materialsymbols.roundedfilled.Bookmark
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
/**
 * The reader's action row under the top bar, as on the previous personal fork: Bookmark (a tap
 * toggles it, a long press opens the bookmark color menu), Retry all and Boost page, evenly spaced.
 * It replaced the collapsible E-Hentai tools panel; reader automation has its own controls.
 */
@Composable
fun ExhUtils(
    backgroundColor: Color,
    onClickRetryAll: () -> Unit,
    onClickBoostPage: () -> Unit,
    bookmarked: Boolean,
    bookmarkColor: Int,
    onToggleBookmarked: () -> Unit,
    onBookmarkColorSelected: (BookmarkColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var bookmarkMenuExpanded by remember { mutableStateOf(false) }
        val haptic = LocalHapticFeedback.current
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onToggleBookmarked,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        bookmarkMenuExpanded = true
                    },
                    onLongClickLabel = stringResource(KMR.strings.action_bookmark_color),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (bookmarked) MaterialSymbols.RoundedFilled.Bookmark else MaterialSymbols.Rounded.Bookmark,
                contentDescription = stringResource(
                    if (bookmarked) MR.strings.action_remove_bookmark else MR.strings.action_bookmark,
                ),
                tint = bookmarkColorOf(bookmarkColor).takeIf { bookmarked } ?: MaterialTheme.colorScheme.primary,
            )
            BookmarkColorDropdownMenu(
                expanded = bookmarkMenuExpanded,
                selected = BookmarkColor.fromValue(bookmarkColor).takeIf { bookmarked },
                onSelect = {
                    bookmarkMenuExpanded = false
                    onBookmarkColorSelected(it)
                },
                onDismissRequest = { bookmarkMenuExpanded = false },
            )
        }
        IconButton(onClick = onClickRetryAll) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Refresh,
                contentDescription = stringResource(SYMR.strings.eh_retry_all),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onClickBoostPage) {
            Icon(
                imageVector = MaterialSymbols.Rounded.RocketLaunch,
                contentDescription = stringResource(SYMR.strings.eh_boost_page),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun ExhUtilsPreview() {
    Surface {
        ExhUtils(
            backgroundColor = Color.Black,
            onClickRetryAll = {},
            onClickBoostPage = {},
            bookmarked = true,
            bookmarkColor = BookmarkColor.DEFAULT_ID,
            onToggleBookmarked = {},
            onBookmarkColorSelected = {},
        )
    }
}
// KNS <--
