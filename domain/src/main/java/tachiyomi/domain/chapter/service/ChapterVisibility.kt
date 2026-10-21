package tachiyomi.domain.chapter.service

import dev.zacsweers.metro.Inject
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.filterChapterVisibility
import tachiyomi.domain.chapter.model.resolveVisibleChapter
import tachiyomi.domain.chapter.model.smartScanlatorMergeEnabled
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga

// KNS -->
/**
 * The one chapter-visibility rule of the app: a manga's chapter blacklist and, when enabled for it,
 * Smart Scanlator Merge. Every surface that shows, opens, continues, counts or downloads chapters asks
 * this class, so they all agree on the same visible chapter set. Hidden chapters are never deleted;
 * their records, read state and bookmarks stay.
 */
@Inject
class ChapterVisibility(
    private val libraryPreferences: LibraryPreferences,
) {

    fun smartMergeEnabled(manga: Manga): Boolean =
        manga.smartScanlatorMergeEnabled(libraryPreferences.smartScanlatorMerge.get())

    /** Whether any rule can hide a chapter of [manga]; when false the list is returned as is. */
    fun hasRules(manga: Manga): Boolean = manga.blacklistedChapters.isNotEmpty() || smartMergeEnabled(manga)

    /**
     * The visible part of [chapters], in their order. [allChapters] is the complete chapter list of
     * the manga, needed to resolve blacklist entries whose chapter URL changed.
     */
    fun visibleChapters(manga: Manga, chapters: List<Chapter>, allChapters: List<Chapter> = chapters): List<Chapter> {
        if (chapters.isEmpty() || !hasRules(manga)) return chapters
        return chapters.filterChapterVisibility(manga, smartMergeEnabled(manga), allChapters)
    }

    /**
     * Where to continue from [chapterId] when it is hidden: its Smart Merge representative when one
     * is visible, otherwise the first visible chapter after it in [ordered] (the complete, reading-
     * ordered chapter list). Returns [Start.Visible] when the chapter itself is visible and
     * [Start.Unknown] when it is not part of [ordered] at all.
     */
    fun startFor(manga: Manga, chapterId: Long, ordered: List<Chapter>, visible: List<Chapter>): Start {
        if (visible.any { it.id == chapterId }) return Start.Visible(chapterId)
        val position = ordered.indexOfFirst { it.id == chapterId }
        if (position == -1) return Start.Unknown
        ordered.resolveVisibleChapter(manga, smartMergeEnabled(manga), chapterId, visible)
            ?.let { return Start.Representative(it.id) }
        val visibleIds = visible.mapTo(HashSet(), Chapter::id)
        val next = ordered.drop(position + 1).firstOrNull { it.id in visibleIds }
        return if (next != null) Start.After(next.id) else Start.End
    }

    sealed interface Start {
        /** The chapter is visible itself. */
        data class Visible(val chapterId: Long) : Start

        /** A hidden duplicate; continue with the visible release of the same chapter. */
        data class Representative(val chapterId: Long) : Start

        /** A hidden chapter; [chapterId] is the next visible one after it. */
        data class After(val chapterId: Long) : Start

        /** A hidden chapter with no visible chapter after it. */
        data object End : Start

        /** Not a chapter of this manga's list. */
        data object Unknown : Start
    }
}
// KNS <--
