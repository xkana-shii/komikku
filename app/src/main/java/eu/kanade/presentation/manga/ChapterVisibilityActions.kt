package eu.kanade.presentation.manga

import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter

// KNS -->
/**
 * Chapter blacklist, bookmark color and scanlator preference actions of the manga screen. The
 * scanlator preferences dialog also manages the blacklist.
 */
class ChapterVisibilityActions(
    val onBlacklist: (List<Chapter>) -> Unit,
    val onUnblacklist: (List<Chapter>) -> Unit,
    val onBookmarkColor: (List<Chapter>, BookmarkColor) -> Unit,
    val onOpenScanlatorPreferences: () -> Unit,
) {
    companion object {
        val None = ChapterVisibilityActions({}, {}, { _, _ -> }, {})
    }
}
// KNS <--
