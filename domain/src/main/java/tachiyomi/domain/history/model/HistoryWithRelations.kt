package tachiyomi.domain.history.model

import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Instant

data class HistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val mangaId: Long,
    // SY -->
    val ogTitle: String,
    val userTitle: String? = null,
    // SY <--
    val chapterNumber: Double,
    // KMK -->
    val read: Boolean,
    val lastPageRead: Int,
    val totalCountCalculated: Long,
    val readCountCalculated: Long,
    // KMK <--
    val readAt: Instant?,
    val readDuration: Long,
    val coverData: MangaCover,
) {
    // SY -->
    val title: String = userTitle ?: ogTitle
    // SY <--

    // KMK -->
    val unreadCount
        get() = totalCountCalculated - readCountCalculated
    // KMK <--
}
