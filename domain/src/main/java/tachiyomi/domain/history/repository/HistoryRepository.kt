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

/**
 * One history row the user removed on this device whose `history` tombstone the server has not
 * confirmed yet, with the urls that tombstone's key is built from (the server keys a history entry
 * by its chapter).
 *
 * [removedAtMs] is the removal time in epoch milliseconds, like `read_at`; 0 when unknown. The wire
 * field derived from it is `at`, in Unix seconds.
 */
data class PendingHistoryDeletion(
    val historyId: Long,
    val sourceId: Long,
    val mangaUrl: String,
    val chapterUrl: String,
    val removedAtMs: Long,
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

    /**
     * Removes [historyIds] from history: each row loses its read date, which is what every history
     * reader shows as gone. [queueForSync] also queues the row as a SyncYomi `history` tombstone.
     *
     * A removal that no configured sync could ever deliver is reset *and* un-queued instead: a
     * marker nothing will send blocks the row from being restored over, keeps it out of "clear
     * database", and pins its chapter against a remote deletion. Rows without a read date are
     * untouched either way.
     *
     * A queued removal also records *when* it happened (`history.removed_at_ms`), which the tombstone
     * sends so the server can refuse it over a later read.
     */
    suspend fun removeHistory(historyIds: List<Long>, queueForSync: Boolean)

    /** The per-manga removal: [removeHistory] over every row of [mangaIds] that has a read date. */
    suspend fun removeHistoryByMangaIds(mangaIds: List<Long>, queueForSync: Boolean)

    /** "Clear all history": [removeHistory] over every row that still has a read date. */
    suspend fun removeAllHistory(queueForSync: Boolean): Boolean

    /**
     * At most [limit] rows still awaiting a server tombstone, ordered by row id, starting after
     * [afterId]. Paged so a "clear all" never has to hold the whole removed history at once.
     */
    suspend fun getPendingHistoryDeletions(limit: Int, afterId: Long = 0): List<PendingHistoryDeletion>

    /** Drops the queue flag of the rows the server confirmed; their read date stays as it is. */
    suspend fun clearPendingHistoryDeletion(historyIds: List<Long>)

    /**
     * Drops every marker a configuration change stranded (history sync off, another service, a host
     * that is no longer v2). Their removals stand: the rows keep no read date.
     */
    suspend fun clearAllPendingHistoryDeletions()
    // KUN <--

    // KMK -->
    suspend fun resetHistory(historyIds: List<Long>)

    suspend fun resetHistoryByMangaIds(mangaIds: List<Long>)
    // KMK <--

    // KUN -->
    // Removed `deleteAllHistory`: "clear all" is a soft delete that reports whether the rows could
    // be queued, so it is removeAllHistory(queueForSync) above.
    // KUN <--

    suspend fun deleteResetHistory()

    suspend fun upsertHistory(historyUpdate: HistoryUpdate)

    // SY -->
    suspend fun upsertHistory(historyUpdates: List<HistoryUpdate>)
    // SY <--
}
