package tachiyomi.domain.chapter.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository

class GetChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long, applyFilter: Boolean = false): List<Chapter> {
        return try {
            chapterRepository.getChapterByMangaId(mangaId, applyFilter)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    // KMK -->
    suspend fun subscribe(mangaId: Long, applyFilter: Boolean = false): Flow<List<Chapter>> {
        return chapterRepository.getChapterByMangaIdAsFlow(mangaId, applyFilter)
    }
    // KMK <--
}
