package tachiyomi.domain.chapter.interactor

import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.filterChapterVisibility
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga

// KMK -->
/**
 * Resolves chapter visibility for several entries without issuing a chapter query per regular
 * manga. Callers provide the manga records so configuration remains reactive at the UI boundary.
 */
class GetVisibleChaptersByMangaIds(
    private val getChaptersByMangaIds: GetChaptersByMangaIds,
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    private val libraryPreferences: LibraryPreferences,
) {
    suspend fun await(
        mangas: Collection<Manga>,
        applyFilter: Boolean = true,
    ): Map<Long, List<Chapter>> {
        val regularMangaIds = mangas
            .filterNot { it.source == MERGED_SOURCE_ID }
            .map(Manga::id)
        val regularChapters = if (regularMangaIds.isEmpty()) {
            emptyMap()
        } else {
            getChaptersByMangaIds.await(regularMangaIds, applyFilter).groupBy(Chapter::mangaId)
        }

        return mangas.associate { manga ->
            val chapters = if (manga.source == MERGED_SOURCE_ID) {
                getMergedChaptersByMangaId.await(manga.id, applyFilter = applyFilter)
            } else {
                regularChapters[manga.id].orEmpty()
            }
            manga.id to chapters.filterChapterVisibility(
                manga = manga,
                smartScanlatorMergeEnabled = libraryPreferences.smartScanlatorMergeEnabled(manga.id),
            )
        }
    }
}
// KMK <--
