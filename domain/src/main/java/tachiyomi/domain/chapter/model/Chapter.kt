package tachiyomi.domain.chapter.model

import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import kotlin.time.Instant

data class Chapter(
    val id: Long,
    val mangaId: Long,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Int,
    val dateFetch: Instant,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Instant?,
    val chapterNumber: Double,
    val scanlator: String?,
    val lastModifiedAt: Long,
    val version: Long,
    val memo: JsonObject,
    // KNS -->
    /** [BookmarkColor] id; [BookmarkColor.DEFAULT_ID] for an ordinary (or no) bookmark. */
    val bookmarkColor: Int = BookmarkColor.DEFAULT_ID,
    // KNS <--
) {
    val isRecognizedNumber: Boolean
        get() = chapterNumber >= 0f

    fun copyFrom(other: Chapter): Chapter {
        return copy(
            name = other.name,
            url = other.url,
            dateUpload = other.dateUpload,
            chapterNumber = other.chapterNumber,
            scanlator = other.scanlator?.ifBlank { null },
        )
    }

    companion object {
        // KNS -->
        /**
         * A restored chapter whose payload did not say its bookmark color (older backups, or a sync
         * server that dropped the field). Never stored: restore keeps the local color instead.
         */
        const val BOOKMARK_COLOR_UNKNOWN = -1
        // KNS <--

        fun create() = Chapter(
            id = -1,
            mangaId = -1,
            read = false,
            bookmark = false,
            lastPageRead = 0,
            dateFetch = Instant.fromEpochMilliseconds(0),
            sourceOrder = 0,
            url = "",
            name = "",
            dateUpload = null,
            chapterNumber = -1.0,
            scanlator = null,
            lastModifiedAt = 0,
            version = 1,
            memo = JsonObject.EMPTY,
        )
    }
}
