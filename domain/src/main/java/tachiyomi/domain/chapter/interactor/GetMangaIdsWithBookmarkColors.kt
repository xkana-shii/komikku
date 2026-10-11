package tachiyomi.domain.chapter.interactor

// KMK -->
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetMangaIdsWithBookmarkColors(
    private val chapterRepository: ChapterRepository,
) {
    fun subscribe(bookmarkColors: Set<BookmarkColor>): Flow<Set<Long>> {
        return chapterRepository.getMangaIdsWithBookmarkColors(bookmarkColors)
    }
}
// KMK <--
