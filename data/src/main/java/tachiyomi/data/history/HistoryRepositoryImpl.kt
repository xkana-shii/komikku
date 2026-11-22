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
            // one bind variable per id, so a select-all needs chunking to fit SQLite's variable limit;
            // the transaction keeps the chunks all-or-nothing.
            database.transaction {
                historyIds.chunked(MAX_BOUND_IDS).forEach {
                    database.historyQueries.removeHistoryByIds(queue = queueForSync.toLong(), historyIds = it)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun removeHistoryByMangaIds(mangaIds: List<Long>, queueForSync: Boolean) {
        try {
            database.transaction {
                mangaIds.chunked(MAX_BOUND_IDS).forEach {
                    database.historyQueries.removeHistoryByMangaIds(queue = queueForSync.toLong(), mangaIds = it)
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
            // the whole table (the queue lives in the rows, not in a persisted key per entry).
            // KUN <--
            database.historyQueries.removeAllHistory(queue = queueForSync.toLong())
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
            .getPendingHistoryDeletions(afterId, limit.toLong()) { historyId, sourceId, mangaUrl, chapterUrl ->
                PendingHistoryDeletion(
                    historyId = historyId,
                    sourceId = sourceId,
                    mangaUrl = mangaUrl,
                    chapterUrl = chapterUrl,
                )
            }
            .awaitAsList()
    }

    override suspend fun clearPendingHistoryDeletion(historyIds: List<Long>) {
        try {
            database.transaction {
                historyIds.chunked(MAX_BOUND_IDS).forEach { database.historyQueries.clearPendingHistoryDeletion(it) }
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
            database.historyQueries.upsert(
                chapterId = historyUpdate.chapterId,
                readAt = historyUpdate.readAt,
                readDuration = historyUpdate.sessionReadDuration,
            )
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
