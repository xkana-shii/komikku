package eu.kanade.presentation.manga

import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter

// KNS -->
/** Chapter blacklist and bookmark color actions of the manga screen's chapter selection. */
class ChapterVisibilityActions(
    val onBlacklist: (List<Chapter>) -> Unit,
    val onUnblacklist: (List<Chapter>) -> Unit,
    val onBookmarkColor: (List<Chapter>, BookmarkColor) -> Unit,
) {
    companion object {
        val None = ChapterVisibilityActions({}, {}, { _, _ -> })
    }
}
// KNS <--
