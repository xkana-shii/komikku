package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.BookmarkColorDropdownMenu
import eu.kanade.presentation.manga.components.composeColor
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExhUtils(
    backgroundColor: Color,
    onClickRetryAll: () -> Unit,
    onClickBoostPage: () -> Unit,
    bookmarked: Boolean,
    bookmarkColor: BookmarkColor,
    onToggleBookmarked: () -> Unit,
    onBookmarkColorSelected: (BookmarkColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(backgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Action row for Bookmark, Retry All, and Boost Page icons (replaces arrow button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var bookmarkMenuExpanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .combinedClickable(
                        onClick = onToggleBookmarked,
                        onLongClick = { bookmarkMenuExpanded = true },
                    ),
            ) {
                Icon(
                    imageVector = if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(
                        if (bookmarked) MR.strings.action_remove_bookmark else MR.strings.action_bookmark,
                    ),
                    tint = if (bookmarked) {
                        bookmarkColor.composeColor() ?: MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                BookmarkColorDropdownMenu(
                    expanded = bookmarkMenuExpanded,
                    selected = bookmarkColor,
                    onSelect = {
                        onBookmarkColorSelected(it)
                        bookmarkMenuExpanded = false
                    },
                    onDismissRequest = { bookmarkMenuExpanded = false },
                )
            }
            // Refresh Icon (Retry All)
            IconButton(onClick = onClickRetryAll) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(SYMR.strings.eh_retry_all),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // Speed Icon (Boost Page)
            IconButton(onClick = onClickBoostPage) {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = stringResource(SYMR.strings.eh_boost_page),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
@PreviewLightDark
private fun ExhUtilsPreview() {
    Surface {
        ExhUtils(
            backgroundColor = Color.Black,
            onClickBoostPage = {},
            onClickRetryAll = {},
            bookmarked = true,
            bookmarkColor = BookmarkColor.NONE,
            onToggleBookmarked = {},
            onBookmarkColorSelected = {},
        )
    }
}
