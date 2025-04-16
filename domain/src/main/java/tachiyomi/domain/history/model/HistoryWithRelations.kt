package tachiyomi.domain.history.model

import kotlinx.coroutines.runBlocking
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.injectLazy
import java.util.Date

data class HistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val mangaId: Long,
    // SY -->
    val ogTitle: String,
    // SY <--
    val chapterNumber: Double,
    val readAt: Date?,
    val readDuration: Long,
    val coverData: MangaCover,
) {
    // SY -->
    val title: String = customMangaManager.get(mangaId)?.title ?: ogTitle
    val chapter: Chapter? by lazy { runBlocking { getChapter.await(chapterId) } }

    companion object {
        private val customMangaManager: GetCustomMangaInfo by injectLazy()
        private val getChapter: GetChapter by injectLazy()

        suspend fun from(history: History, related: HistoryWithRelations): HistoryWithRelations {
            val chapter = getChapter.await(history.chapterId)
            return HistoryWithRelations(
                id = history.id,
                chapterId = history.chapterId,
                mangaId = related.mangaId,
                ogTitle = related.ogTitle,
                chapterNumber = chapter?.chapterNumber ?: -1.0,
                readAt = history.readAt,
                readDuration = history.readDuration,
                coverData = related.coverData,
            )
        }
    }
    // SY <--
}
