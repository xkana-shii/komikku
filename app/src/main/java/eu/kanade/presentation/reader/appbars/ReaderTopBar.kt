package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.bookmarkColorOf
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Bookmark
import mihon.icons.materialsymbols.roundedfilled.Bookmark
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderTopBar(
    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    // KNS -->
    bookmarkColor: Int = BookmarkColor.DEFAULT_ID,
    onLongClickBookmark: (() -> Unit)? = null,
    // KNS <--
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AppBar(
        modifier = modifier,
        backgroundColor = Color.Transparent,
        title = mangaTitle,
        subtitle = chapterTitle,
        navigateUp = navigateUp,
        actions = {
            AppBarActions(
                actions = buildList {
                    // KNS -->
                    val bookmarkTitle = stringResource(
                        if (bookmarked) {
                            MR.strings.action_remove_bookmark
                        } else {
                            MR.strings.action_bookmark
                        },
                    )
                    val bookmarkIcon = if (bookmarked) {
                        MaterialSymbols.RoundedFilled.Bookmark
                    } else {
                        MaterialSymbols.Rounded.Bookmark
                    }
                    val bookmarkTint = bookmarkColorOf(bookmarkColor).takeIf { bookmarked }
                    if (onLongClickBookmark != null) {
                        // A long press picks the bookmark color; the ripple stays round like an icon button.
                        add(
                            AppBar.ActionCompose(
                                title = bookmarkTitle,
                                content = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .combinedClickable(
                                                onClick = onToggleBookmarked,
                                                onLongClick = onLongClickBookmark,
                                                onLongClickLabel = stringResource(KMR.strings.action_bookmark_color),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = bookmarkIcon,
                                            tint = bookmarkTint ?: LocalContentColor.current,
                                            contentDescription = bookmarkTitle,
                                        )
                                    }
                                },
                            ),
                        )
                    } else {
                        add(
                            AppBar.Action(
                                title = bookmarkTitle,
                                icon = bookmarkIcon,
                                iconTint = bookmarkTint,
                                onClick = onToggleBookmarked,
                            ),
                        )
                    }
                    // KNS <--
                    onOpenInWebView?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_open_in_web_view),
                                onClick = it,
                            ),
                        )
                    }
                    onOpenInBrowser?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_open_in_browser),
                                onClick = it,
                            ),
                        )
                    }
                    onShare?.let {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_share),
                                onClick = it,
                            ),
                        )
                    }
                },
            )
        },
    )
}
