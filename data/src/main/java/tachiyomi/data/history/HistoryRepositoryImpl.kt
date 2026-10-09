package tachiyomi.data.history

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga

// KMK -->

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
    ): Flow<List<HistoryWithRelations>> {
        return database.historyViewQueries.history(
            // KMK -->
            Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
            Manga.CHAPTER_SHOW_BOOKMARKED,
            Manga.CHAPTER_SHOW_NOT_FILLERMARKED,
            Manga.CHAPTER_SHOW_FILLERMARKED,
            unfinishedManga?.toLong(),
            unfinishedChapter,
            nonLibraryEntries,
            // KMK <--
            query,
            HistoryMapper::mapHistoryWithRelations,
        ).subscribeToList()
    }

    override suspend fun getLastHistory(): HistoryWithRelations? {
        return database.historyViewQueries.getLatestHistory(
            Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
            Manga.CHAPTER_SHOW_BOOKMARKED,
            Manga.CHAPTER_SHOW_NOT_FILLERMARKED,
            Manga.CHAPTER_SHOW_FILLERMARKED,
            HistoryMapper::mapHistoryWithRelations,
        ).awaitAsOneOrNull()
    }

    override suspend fun getTotalReadDuration(): Long {
        return database.historyQueries.getReadDuration().awaitAsOne()
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<History> {
        return database.historyQueries.getHistoryByMangaId(mangaId, HistoryMapper::mapHistory).awaitAsList()
    }

    // KMK -->
    override suspend fun resetHistory(historyIds: List<Long>) {
        try {
            database.historyQueries.resetHistoryByIds(historyIds)
            // KMK <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    // KMK -->
    override suspend fun resetHistoryByMangaIds(mangaIds: List<Long>) {
        try {
            database.historyQueries.resetHistoryByMangaIds(mangaIds)
            // KMK <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            database.historyQueries.removeAllHistory()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
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
// KMK <--
