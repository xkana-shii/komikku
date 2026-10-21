package tachiyomi.domain.chapter.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.repository.ChapterRepository

// KNS -->
/** The bookmark colors each library entry uses, for the library's bookmark-color filter. */
@Inject
class GetLibraryBookmarkColors(
    private val chapterRepository: ChapterRepository,
) {
    fun subscribe(): Flow<Map<Long, Set<Int>>> = chapterRepository.getBookmarkColorsByMangaAsFlow()
}
// KNS <--
