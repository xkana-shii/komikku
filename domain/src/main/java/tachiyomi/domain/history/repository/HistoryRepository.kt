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
 * One entry of the history tombstone queue: a removal this device made whose SyncYomi `history`
 * tombstone the server has not confirmed yet, with the urls that tombstone's key is built from (the
 * server keys a history entry by its chapter).
 *
 * [id] is the queue row's own id — the tombstone outlives the history row it came from, so nothing
 * here points at one. [removedAtMs] is the removal time in epoch milliseconds, like `read_at`; 0 when
 * unknown. The wire field derived from it is `at`, in Unix seconds.
 */
data class PendingHistoryDeletion(
    val id: Long,
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
     * reader shows as gone. [queueForSync] also queues the entry as a SyncYomi `history` tombstone.
     *
     * The tombstone is a row of its own (`pending_history_deletion`), written in the same
     * transaction and holding no foreign key, so the removal reaches the server even if the chapter
     * or the manga is deleted before the next sync. A removal nothing could deliver queues nothing.
     * Rows without a read date are untouched either way.
     *
     * A queued removal also records *when* it happened (`pending_history_deletion.removed_at_ms`),
     * which the tombstone sends so the server can refuse it over a later read.
     */
    suspend fun removeHistory(historyIds: List<Long>, queueForSync: Boolean)

    /** The per-manga removal: [removeHistory] over every row of [mangaIds] that has a read date. */
    suspend fun removeHistoryByMangaIds(mangaIds: List<Long>, queueForSync: Boolean)

    /** "Clear all history": [removeHistory] over every row that still has a read date. */
    suspend fun removeAllHistory(queueForSync: Boolean): Boolean

    /**
     * At most [limit] queued tombstones, ordered by queue row id, starting after [afterId]. Paged so
     * a "clear all" never has to hold the whole removed history at once.
     */
    suspend fun getPendingHistoryDeletions(limit: Int, afterId: Long = 0): List<PendingHistoryDeletion>

    /**
     * Drops the queue rows the server confirmed ([PendingHistoryDeletion.id]s). The removal itself
     * stands: its history row keeps no read date, and there is nothing left to send.
     */
    suspend fun clearPendingHistoryDeletion(ids: List<Long>)

    /**
     * Drops every queued tombstone a configuration change stranded (history sync off, another
     * service, a host that is no longer v2). Their removals stand: the history rows keep no read
     * date.
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
