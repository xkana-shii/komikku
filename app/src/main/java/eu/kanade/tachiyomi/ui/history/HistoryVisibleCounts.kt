package eu.kanade.tachiyomi.ui.history

import dev.zacsweers.metro.Inject
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterVisibility
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

// KNS -->
/**
 * History counts under the app-wide chapter visibility rule. The History query counts every chapter;
 * for manga with a blacklist or Smart Scanlator Merge the total and read counts are recomputed from the
 * visible chapters (respecting the manga's bookmark filter like the query does), so "unfinished" means
 * the same as in the chapter list. Entries without a rule are returned as they are.
 */
@Inject
class HistoryVisibleCounts(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val chapterVisibility: ChapterVisibility,
) {
    suspend fun apply(history: List<HistoryWithRelations>): List<HistoryWithRelations> {
        if (history.isEmpty()) return history
        val withRules = history.mapTo(mutableSetOf()) { it.mangaId }
            .mapNotNull { id -> runCatching { mangaRepository.getMangaById(id) }.getOrNull() }
            .filter(chapterVisibility::hasRules)
        if (withRules.isEmpty()) return history

        val chapters = chapterRepository.getChaptersByMangaIds(withRules.map { it.id }).groupBy(Chapter::mangaId)
        val counts = withRules.associate { manga ->
            val visible = chapterVisibility.visibleChapters(manga, chapters[manga.id].orEmpty())
                .filter { it.matchesBookmarkFilter(manga) }
            manga.id to (visible.size.toLong() to visible.count { it.read }.toLong())
        }
        return history.map { item ->
            val (total, read) = counts[item.mangaId] ?: return@map item
            item.copy(totalCountCalculated = total, readCountCalculated = read)
        }
    }

    private fun Chapter.matchesBookmarkFilter(manga: Manga): Boolean = when {
        manga.chapterFlags and Manga.CHAPTER_SHOW_NOT_BOOKMARKED != 0L -> !bookmark
        manga.chapterFlags and Manga.CHAPTER_SHOW_BOOKMARKED != 0L -> bookmark
        else -> true
    }

    companion object {
        /** The History "unfinished manga" filter on the (possibly recomputed) counts; null = off. */
        fun filterUnfinished(history: List<HistoryWithRelations>, unfinished: Boolean?): List<HistoryWithRelations> =
            if (unfinished == null) {
                history
            } else {
                history.filter { (it.totalCountCalculated > it.readCountCalculated) == unfinished }
            }
    }
}
// KNS <--
