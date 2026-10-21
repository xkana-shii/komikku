package eu.kanade.tachiyomi.ui.updates

import dev.zacsweers.metro.Inject
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterVisibility
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.updates.model.UpdatesWithRelations

// KNS -->
/**
 * Removes updates of chapters the app-wide visibility rule hides (blacklisted chapters and duplicates
 * Smart Scanlator Merge does not show), so the Updates feed lists the same chapters as the manga's
 * chapter list. Entries without a rule cost nothing; the others are read in one chapter query.
 */
@Inject
class UpdatesVisibilityFilter(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val chapterVisibility: ChapterVisibility,
) {
    suspend fun filter(updates: List<UpdatesWithRelations>): List<UpdatesWithRelations> {
        if (updates.isEmpty()) return updates
        val mangas = updates.mapTo(mutableSetOf()) { it.mangaId }
            .mapNotNull { id -> runCatching { mangaRepository.getMangaById(id) }.getOrNull() }
            .filter(chapterVisibility::hasRules)
        if (mangas.isEmpty()) return updates

        val chaptersByManga = chapterRepository.getChaptersByMangaIds(mangas.map { it.id }).groupBy(Chapter::mangaId)
        val hidden = mangas.flatMapTo(HashSet()) { manga ->
            val all = chaptersByManga[manga.id].orEmpty()
            val visible = chapterVisibility.visibleChapters(manga, all).mapTo(HashSet(), Chapter::id)
            all.asSequence().map(Chapter::id).filterNot(visible::contains).toList()
        }
        return if (hidden.isEmpty()) updates else updates.filterNot { it.chapterId in hidden }
    }
}
// KNS <--
