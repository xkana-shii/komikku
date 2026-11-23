package tachiyomi.data.history

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryChapterEntry
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.history.repository.PendingHistoryDeletion
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class HistoryRepositoryImpl(
    private val database: Database,
) : HistoryRepository {

    override fun getHistory(
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
    ): Flow<List<HistoryWithRelations>> {
        return database.historyViewQueries
            .history(
                // KMK -->
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                unfinishedManga?.toLong(),
                unfinishedChapter,
                nonLibraryEntries,
                // KMK <--
                // KUN -->
                includedEmpty = includedCategories.isEmpty(),
                includedCategories = includedCategories,
                excludedEmpty = excludedCategories.isEmpty(),
                excludedCategories = excludedCategories,
                inclusionWins = inclusionWins,
                // KUN <--
                query,
                HistoryMapper::mapHistoryWithRelations,
            )
            .subscribeToList()
    }

    override suspend fun getTotalReadDuration(): Long {
        return database.historyQueries
            .getReadDuration()
            .awaitAsOne()
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<History> {
        return database.historyQueries
            .getHistoryByMangaId(mangaId, HistoryMapper::mapHistory)
            .awaitAsList()
    }

    override suspend fun getHistoryByMangaIds(mangaIds: List<Long>): Map<Long, List<History>> {
        return database.historyQueries
            .getHistoryByMangaIds(mangaIds) { mangaId, id, chapterId, readAt, readDuration ->
                mangaId to HistoryMapper.mapHistory(id, chapterId, readAt, readDuration)
            }
            .awaitAsList()
            .groupBy({ it.first }, { it.second })
    }

    // KUN -->
    override suspend fun getHistoryChapterEntriesByMangaIds(mangaIds: List<Long>): List<HistoryChapterEntry> {
        // one bind variable per id, so a whole library needs chunking to fit SQLite's limit
        return mangaIds.chunked(MAX_BOUND_IDS).flatMap { chunk ->
            database.historyQueries
                .getHistoryChapterEntriesByMangaIds(chunk) { mangaId, historyId, chapterUrl, readAt ->
                    HistoryChapterEntry(
                        historyId = historyId,
                        mangaId = mangaId,
                        chapterUrl = chapterUrl,
                        readAt = readAt,
                    )
                }
                .awaitAsList()
        }
    }

    override suspend fun removeHistory(historyIds: List<Long>, queueForSync: Boolean) {
        try {
            // KUN -->
            // Read once, outside the chunk loop, so the chunks of one removal cannot straddle it.
            val removedAtMs = if (queueForSync) Clock.System.now().toEpochMilliseconds() else 0L
            // KUN <--
            // one bind variable per id, so a select-all needs chunking to fit SQLite's variable limit;
            // the transaction keeps the chunks all-or-nothing.
            database.transaction {
                // KUN -->
                // The tombstone is queued before the rows are reset: it copies the urls out of the
                // joined chapter and manga, which the row still reaches. Same transaction, so a
                // removal is either queued and reset, or neither.
                if (queueForSync) {
                    historyIds.chunked(MAX_BOUND_IDS).forEach {
                        database.historyQueries.queuePendingHistoryDeletionsByIds(
                            removedAtMs = removedAtMs,
                            historyIds = it,
                        )
                    }
                }
                // KUN <--
                historyIds.chunked(MAX_BOUND_IDS).forEach {
                    database.historyQueries.removeHistoryByIds(it)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun removeHistoryByMangaIds(mangaIds: List<Long>, queueForSync: Boolean) {
        try {
            // KUN -->
            val removedAtMs = if (queueForSync) Clock.System.now().toEpochMilliseconds() else 0L
            // KUN <--
            database.transaction {
                // KUN -->
                if (queueForSync) {
                    mangaIds.chunked(MAX_BOUND_IDS).forEach {
                        database.historyQueries.queuePendingHistoryDeletionsByMangaIds(
                            removedAtMs = removedAtMs,
                            mangaIds = it,
                        )
                    }
                }
                // KUN <--
                mangaIds.chunked(MAX_BOUND_IDS).forEach {
                    database.historyQueries.removeHistoryByMangaIds(it)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun removeAllHistory(queueForSync: Boolean): Boolean {
        return try {
            // KUN -->
            // A soft delete: every row that still has a read date is reset and, when the configured
            // sync can deliver a tombstone, queued — the same removal as the other two paths, over
            // the whole table.
            //
            // One full-table UPDATE, and its only caller is a `viewModelScope` (Main) handler, so the
            // hop belongs here; the other two paths are reached from `appScope` (IO).
            // KUN <--
            withIOContext {
                database.transaction {
                    // KUN -->
                    if (queueForSync) {
                        database.historyQueries.queueAllPendingHistoryDeletions(
                            removedAtMs = Clock.System.now().toEpochMilliseconds(),
                        )
                    }
                    // KUN <--
                    database.historyQueries.removeAllHistory()
                }
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun getPendingHistoryDeletions(
        limit: Int,
        afterId: Long,
    ): List<PendingHistoryDeletion> {
        return database.historyQueries
            .getPendingHistoryDeletions(afterId, limit.toLong()) { id, sourceId, mangaUrl, chapterUrl, removedAtMs ->
                PendingHistoryDeletion(
                    id = id,
                    sourceId = sourceId,
                    mangaUrl = mangaUrl,
                    chapterUrl = chapterUrl,
                    removedAtMs = removedAtMs,
                )
            }
            .awaitAsList()
    }

    override suspend fun clearPendingHistoryDeletion(ids: List<Long>) {
        try {
            database.transaction {
                ids.chunked(MAX_BOUND_IDS).forEach { database.historyQueries.clearPendingHistoryDeletion(it) }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun clearAllPendingHistoryDeletions() {
        // KUN -->
        // Dispatched here rather than at the call sites: one of them is a Compose settings callback
        // on Main, and this is a full-table update whenever anything is queued.
        // KUN <--
        withIOContext {
            database.historyQueries.clearAllPendingHistoryDeletions()
        }
    }
    // KUN <--

    // KMK -->
    override suspend fun resetHistory(historyIds: List<Long>) {
        try {
            // KUN -->
            // One bind variable per id, so a select-all needs chunking to fit SQLite's variable limit;
            // the transaction keeps the chunks all-or-nothing.
            database.transaction {
                historyIds.chunked(MAX_BOUND_IDS).forEach { database.historyQueries.resetHistoryByIds(it) }
            }
            // KUN <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByMangaIds(mangaIds: List<Long>) {
        try {
            // KUN -->
            database.transaction {
                mangaIds.chunked(MAX_BOUND_IDS).forEach { database.historyQueries.resetHistoryByMangaIds(it) }
            }
            // KUN <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
    // KMK <--

    override suspend fun deleteResetHistory() {
        database.historyQueries.removeResettedHistory()
    }

    override suspend fun upsertHistory(historyUpdate: HistoryUpdate) {
        try {
            // KUN -->
            // The read supersedes a removal the server has not been told about, so the queue entry
            // goes with the write: the tombstone must not be sent over the read that replaced it.
            database.transaction {
                database.historyQueries.upsert(
                    chapterId = historyUpdate.chapterId,
                    readAt = historyUpdate.readAt,
                    readDuration = historyUpdate.sessionReadDuration,
                )
                database.historyQueries.deletePendingHistoryDeletionByChapterId(chapterId = historyUpdate.chapterId)
            }
            // KUN <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    // SY -->
    override suspend fun upsertHistory(historyUpdates: List<HistoryUpdate>) {
        try {
            database.transaction {
                historyUpdates.forEach { historyUpdate ->
                    database.historyQueries.upsert(
                        chapterId = historyUpdate.chapterId,
                        readAt = historyUpdate.readAt,
                        readDuration = historyUpdate.sessionReadDuration,
                    )
                    // KUN -->
                    database.historyQueries.deletePendingHistoryDeletionByChapterId(
                        chapterId = historyUpdate.chapterId,
                    )
                    // KUN <--
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
    // SY <--
}

// KUN -->
/** Bind ids per statement: SQLite allows 999 variables before Android 11, 32766 after. */
private const val MAX_BOUND_IDS = 900
// KUN <--
