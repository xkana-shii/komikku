package tachiyomi.domain.chapter.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository

// KMK -->
@Inject
class GetChaptersByMangaIds(
    private val chapterRepository: ChapterRepository,
) {
    suspend fun await(mangaIds: List<Long>, applyFilter: Boolean = false): List<Chapter> {
        return chapterRepository.getChapterByMangaIds(mangaIds, applyFilter)
    }
}
// KMK <--
