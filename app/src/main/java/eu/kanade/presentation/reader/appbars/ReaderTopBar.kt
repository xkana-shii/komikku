package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.BookmarkColorDropdownMenu
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
    /** A long press on the bookmark opens the bookmark color menu; the choice goes here. */
    onBookmarkColorSelected: ((BookmarkColor) -> Unit)? = null,
    // KNS <--
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // KNS -->
    // Lives here, not in the action, so the open menu survives the action list being rebuilt.
    var bookmarkColorMenuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // KNS <--
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
                    if (onBookmarkColorSelected != null) {
                        // A long press opens the bookmark color menu anchored to the button (the same
                        // menu as the chapter selection bars); the ripple stays round like an icon button.
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
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    bookmarkColorMenuExpanded = true
                                                },
                                                onLongClickLabel = stringResource(KMR.strings.action_bookmark_color),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = bookmarkIcon,
                                            tint = bookmarkTint ?: LocalContentColor.current,
                                            contentDescription = bookmarkTitle,
                                        )
                                        BookmarkColorDropdownMenu(
                                            expanded = bookmarkColorMenuExpanded,
                                            selected = BookmarkColor.fromValue(bookmarkColor).takeIf { bookmarked },
                                            onSelect = {
                                                bookmarkColorMenuExpanded = false
                                                onBookmarkColorSelected(it)
                                            },
                                            onDismissRequest = { bookmarkColorMenuExpanded = false },
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
