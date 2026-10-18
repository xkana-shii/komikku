package tachiyomi.domain.updates.model

import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Instant

data class UpdatesWithRelations(
    val mangaId: Long,
    // SY -->
    val ogMangaTitle: String,
    // SY <--
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val chapterUrl: String,
    val read: Boolean,
    val bookmark: Boolean,
    // KMK -->
    val bookmarkColor: BookmarkColor,
    // KMK <--
    val lastPageRead: Int,
    val sourceId: Long,
    val dateFetch: Instant,
    val coverData: MangaCover,
) {
    // SY -->
    // KMK --> lazy so the bridge resolves on first access
    val mangaTitle: String by lazy { getCustomMangaInfo?.get(mangaId)?.title ?: ogMangaTitle }
    // KUN <--

    companion object {
        private val getCustomMangaInfo: GetCustomMangaInfo?
            get() = GetCustomMangaInfo.getInstanceOrNull()
    }
    // SY <--
}
