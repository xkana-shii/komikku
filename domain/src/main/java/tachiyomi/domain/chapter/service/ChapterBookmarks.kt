package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.updates.model.UpdatesWithRelations

// KNS -->
/**
 * The chapter updates behind the bookmark actions of the manga screen, Updates and the reader, so
 * every screen bookmarks, unbookmarks and colors chapters the same way.
 */
object ChapterBookmarks {

    /** A chapter's id and current bookmark state. */
    data class Target(val chapterId: Long, val bookmark: Boolean, val bookmarkColor: Int)

    /**
     * The ordinary bookmark action: chapters not yet in the [bookmark] state change, and their color
     * goes back to the ordinary one, so a removed bookmark never keeps a stale color.
     */
    fun toggle(targets: List<Target>, bookmark: Boolean): List<ChapterUpdate> =
        targets
            .filterNot { it.bookmark == bookmark }
            .map {
                ChapterUpdate(it.chapterId) {
                    this.bookmark = bookmark
                    bookmarkColor = BookmarkColor.DEFAULT_ID
                }
            }

    /**
     * Choosing [color] bookmarks every chapter with it ([BookmarkColor.NONE] = an ordinary bookmark);
     * chapters that already have exactly that bookmark are left alone.
     */
    fun setColor(targets: List<Target>, color: BookmarkColor): List<ChapterUpdate> =
        targets
            .filterNot { it.bookmark && it.bookmarkColor == color.value }
            .map {
                ChapterUpdate(it.chapterId) {
                    bookmark = true
                    bookmarkColor = color.value
                }
            }

    /**
     * The color a picker marks as current: the shared color when every chapter is bookmarked with
     * the same one; null for unbookmarked or mixed selections.
     */
    fun currentColor(targets: List<Target>): BookmarkColor? =
        targets
            .map { if (it.bookmark) BookmarkColor.fromValue(it.bookmarkColor) else null }
            .distinct()
            .singleOrNull()
}

fun Chapter.bookmarkTarget() = ChapterBookmarks.Target(id, bookmark, bookmarkColor)

fun UpdatesWithRelations.bookmarkTarget() = ChapterBookmarks.Target(chapterId, bookmark, bookmarkColor)
// KNS <--
