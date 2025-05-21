package tachiyomi.domain.chapter.model

data class ChapterUpdate(
    val id: Long,
    val mangaId: Long? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    val fillermark: Boolean? = null,
    val lastPageRead: Long? = null,
    val dateFetch: Long? = null,
    val sourceOrder: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val dateUpload: Long? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val version: Long? = null,
)

fun Chapter.toChapterUpdate(): ChapterUpdate {
    return ChapterUpdate(
        id,
        mangaId,
        read,
        bookmark,
        fillermark,
        lastPageRead,
        dateFetch,
        sourceOrder,
        url,
        name,
        dateUpload,
        chapterNumber,
        scanlator,
        version,
    )
}
