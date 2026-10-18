package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import mihon.core.common.extensions.toByteArray
import mihon.core.common.extensions.toEpochMillisOrZero
import mihon.core.common.extensions.toInstantOrNull
import mihon.core.common.extensions.toJsonObject
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import kotlin.time.Instant

@Serializable
class BackupChapter(
    // SY -->
    @ProtoNumber(1)
    var url: String = "",

    @ProtoNumber(2)
    var name: String = "",
    // SY <--

    @ProtoNumber(3)
    var scanlator: String? = null,

    @ProtoNumber(4)
    var read: Boolean = false,

    @ProtoNumber(5)
    var bookmark: Boolean = false,

    @ProtoNumber(6)
    var lastPageRead: Long = 0,

    @ProtoNumber(7)
    var dateFetch: Long = 0,

    @ProtoNumber(8)
    var dateUpload: Long = 0,

    @ProtoNumber(9)
    var chapterNumber: Float = 0F,

    @ProtoNumber(10)
    var sourceOrder: Long = 0,

    @ProtoNumber(11)
    var lastModifiedAt: Long = 0,

    @ProtoNumber(12)
    var version: Long = 0,

    @ProtoNumber(13)
    var memo: ByteArray = JsonObjectEmptyBytes,

    // KMK -->
    // A new field number preserves older backup compatibility.
    @ProtoNumber(14)
    var bookmarkColor: Int = BookmarkColor.NONE.value,
    // KMK <--
) {

    fun toChapterImpl(): Chapter {
        return Chapter.create().copy(
            url = url,
            name = name,
            chapterNumber = chapterNumber.toDouble(),
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt(),
            dateFetch = Instant.fromEpochMilliseconds(dateFetch),
            dateUpload = dateUpload.toInstantOrNull(),
            sourceOrder = sourceOrder,
            lastModifiedAt = lastModifiedAt,
            version = version,
            memo = memo.toJsonObject(),

            // KMK -->
            bookmarkColor = BookmarkColor.fromValue(
                bookmarkColor.toLong(),
            ),
            // KMK <--
        )
    }
}

fun Chapter.toBackupChapter() = BackupChapter(
    url = url,
    name = name,
    chapterNumber = chapterNumber.toFloat(),
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead.toLong(),
    dateFetch = dateFetch.toEpochMilliseconds(),
    dateUpload = dateUpload.toEpochMillisOrZero(),
    sourceOrder = sourceOrder,
    lastModifiedAt = lastModifiedAt,
    version = version,
    memo = memo.toByteArray(),

    // KMK -->
    bookmarkColor = bookmarkColor.value,
    // KMK <--
)
