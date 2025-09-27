package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.sy.SYMR
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExhUtils(
    backgroundColor: Color,
    onClickRetryAll: () -> Unit,
    onClickBoostPage: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
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
            IconButton(
                onClick = onToggleBookmarked,
            ) {
                Icon(
                    imageVector = if (bookmarked) {
                        Icons.Outlined.Bookmark
                    } else {
                        Icons.Outlined.BookmarkBorder
                    },
                    contentDescription = stringResource(
                        if (bookmarked) {
                            MR.strings.action_remove_bookmark
                        } else {
                            MR.strings.action_bookmark
                        }
                    ),
                    tint = MaterialTheme.colorScheme.primary,
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
            onToggleBookmarked = {},
        )
    }
}
