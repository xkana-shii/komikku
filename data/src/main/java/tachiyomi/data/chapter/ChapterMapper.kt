package tachiyomi.data.chapter

import kotlinx.serialization.json.JsonObject
import tachiyomi.domain.chapter.model.Chapter
import kotlin.time.Instant

object ChapterMapper {
    fun mapChapter(
        id: Long,
        mangaId: Long,
        remoteUrl: String,
        remoteName: String,
        remoteScanlator: String?,
        remoteChapterNumber: Double,
        remoteDateUpload: Instant?,
        remoteOrder: Long,
        remoteMemo: JsonObject,
        userRead: Boolean,
        userBookmark: Boolean,
        userLastPageRead: Int,
        stateDateFetch: Instant,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
        // KNS -->
        userBookmarkColor: Long,
        // KNS <--
    ): Chapter = Chapter(
        id = id,
        mangaId = mangaId,
        read = userRead,
        bookmark = userBookmark,
        lastPageRead = userLastPageRead,
        dateFetch = stateDateFetch,
        sourceOrder = remoteOrder,
        url = remoteUrl,
        name = remoteName,
        dateUpload = remoteDateUpload,
        chapterNumber = remoteChapterNumber,
        scanlator = remoteScanlator,
        lastModifiedAt = lastModifiedAt,
        version = version,
        memo = remoteMemo,
        // KNS -->
        bookmarkColor = userBookmarkColor.toInt(),
        // KNS <--
    )
}
