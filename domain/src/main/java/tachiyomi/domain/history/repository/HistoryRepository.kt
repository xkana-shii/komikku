package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import kotlin.time.Instant

// KUN -->
/**
 * One history row of a library manga with the chapter url its `history` tombstone key is built from.
 * A full-snapshot sync compares these against the server's per-manga lists, so it needs one
 * projection instead of every chapter of every manga.
 */
data class HistoryChapterEntry(
    val historyId: Long,
    val mangaId: Long,
    val chapterUrl: String,
    val readAt: Instant?,
)
// KUN <--

interface HistoryRepository {

    fun getHistory(
        query: String,
        // KMK -->
        unfinishedManga: Boolean?,
        unfinishedChapter: Boolean?,
        nonLibraryEntries: Boolean?,
        // KMK <--
        // KUN -->
        includedCategories: List<Long>,
        excludedCategories: List<Long>,
        inclusionWins: Boolean,
        // KUN <--
    ): Flow<List<HistoryWithRelations>>

    suspend fun getTotalReadDuration(): Long

    suspend fun getHistoryByMangaId(mangaId: Long): List<History>

    suspend fun getHistoryByMangaIds(mangaIds: List<Long>): Map<Long, List<History>>

    // KUN -->
    /** [HistoryChapterEntry] rows of [mangaIds], in one query joining each row to its chapter. */
    suspend fun getHistoryChapterEntriesByMangaIds(mangaIds: List<Long>): List<HistoryChapterEntry>
    // KUN <--

    // KMK -->
    suspend fun resetHistory(historyIds: List<Long>)

    suspend fun resetHistoryByMangaIds(mangaIds: List<Long>)
    // KMK <--

    suspend fun deleteAllHistory(): Boolean

    suspend fun deleteResetHistory()

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)

    // SY -->
    suspend fun upsertHistory(historyUpdates: List<HistoryUpdate>)
    // SY <--
}
