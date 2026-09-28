package tachiyomi.data.chapter

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterRemoteUpdate
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga

// KMK -->

class ChapterRepositoryImpl(
    private val database: Database,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            database.transactionWithResult {
                chapters.map { chapter ->
                    val lastInsertId = database.chapterQueries.insertReturningId(
                        mangaId = chapter.mangaId,
                        remoteUrl = chapter.url,
                        remoteName = chapter.name,
                        remoteScanlator = chapter.scanlator,
                        userRead = chapter.read,
                        userBookmark = chapter.bookmark,
                        userFillermark = chapter.fillermark,
                        userLastPageRead = chapter.lastPageRead,
                        remoteChapterNumber = chapter.chapterNumber,
                        remoteOrder = chapter.sourceOrder,
                        stateDateFetch = chapter.dateFetch,
                        remoteDateUpload = chapter.dateUpload,
                        stateVersion = chapter.version,
                        remoteMemo = chapter.memo,
                    ).awaitAsOne()
                    chapter.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
    override suspend fun update(chapterUpdate: ChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    // KMK -->
    override suspend fun updateFromRemote(
        removedIds: List<Long>,
        added: List<Chapter>,
        updated: List<ChapterRemoteUpdate>,
    ): List<Chapter> {
        return database.transactionWithResult {
            if (removedIds.isNotEmpty()) {
                database.chapterQueries.removeChaptersWithIds(removedIds)
            }
            val existing = added.map { it.mangaId }
                .distinct()
                .flatMap { mangaId ->
                    database.chapterQueries
                        .getChaptersByMangaId(
                            mangaId,
                            false.toLong(),
                            Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                            Manga.CHAPTER_SHOW_BOOKMARKED,
                            Manga.CHAPTER_SHOW_NOT_FILLERMARKED,
                            Manga.CHAPTER_SHOW_FILLERMARKED,
                            ChapterMapper::mapChapter,
                        )
                        .awaitAsList()
                        .map { mangaId to it.url }
                }
                .toMutableSet()
            val stored = added.filter { existing.add(it.mangaId to it.url) }.map { chapter ->
                val chapterId = database.chapterQueries.insertReturningId(
                    mangaId = chapter.mangaId,
                    remoteUrl = chapter.url,
                    remoteName = chapter.name,
                    remoteScanlator = chapter.scanlator,
                    userRead = chapter.read,
                    userBookmark = chapter.bookmark,
                    userFillermark = chapter.fillermark,
                    userLastPageRead = chapter.lastPageRead,
                    remoteChapterNumber = chapter.chapterNumber,
                    remoteOrder = chapter.sourceOrder,
                    stateDateFetch = chapter.dateFetch,
                    remoteDateUpload = chapter.dateUpload,
                    stateVersion = chapter.version,
                    remoteMemo = chapter.memo,
                )
                    .awaitAsOne()
                chapter.copy(id = chapterId)
            }
            updated.forEach { chapterUpdate ->
                database.chapterQueries.updateRemote(
                    remoteName = chapterUpdate.name,
                    remoteScanlator = chapterUpdate.scanlator,
                    remoteChapterNumber = chapterUpdate.chapterNumber,
                    remoteOrder = chapterUpdate.sourceOrder,
                    remoteDateUpload = chapterUpdate.dateUpload,
                    id = chapterUpdate.id,
                    remoteMemo = chapterUpdate.memo,
                )
            }
            stored
        }
    }
    // KMK <--
    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        database.transaction {
            chapterUpdates.forEach { chapterUpdate ->
                database.chapterQueries.update(
                    mangaId = chapterUpdate.mangaId,
                    remoteUrl = chapterUpdate.url,
                    remoteName = chapterUpdate.name,
                    remoteScanlator = chapterUpdate.scanlator,
                    userRead = chapterUpdate.read,
                    userBookmark = chapterUpdate.bookmark,
                    userFillermark = chapterUpdate.fillermark,
                    userLastPageRead = chapterUpdate.lastPageRead,
                    remoteChapterNumber = chapterUpdate.chapterNumber,
                    remoteOrder = chapterUpdate.sourceOrder,
                    stateDateFetch = chapterUpdate.dateFetch,
                    remoteDateUpload = chapterUpdate.dateUpload,
                    stateVersion = chapterUpdate.version,
                    stateIsSyncing = 0,
                    remoteMemo = chapterUpdate.memo,
                    id = chapterUpdate.id,
                )
            }
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
        try {
            database.chapterQueries.removeChaptersWithIds(chapterIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByMangaId(mangaId: Long, applyFilter: Boolean): List<Chapter> {
        return database.chapterQueries.getChaptersByMangaId(
            mangaId,
            applyFilter.toLong(),
            // KMK -->
            Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
            Manga.CHAPTER_SHOW_BOOKMARKED,
            Manga.CHAPTER_SHOW_NOT_FILLERMARKED,
            Manga.CHAPTER_SHOW_FILLERMARKED,
            // KMK <--
            ChapterMapper::mapChapter,
        ).awaitAsList()
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> {
        return database.chapterQueries.getScanlatorsByMangaId(mangaId) { it.orEmpty() }.awaitAsList()
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return database.chapterQueries.getScanlatorsByMangaId(mangaId) { it.orEmpty() }.subscribeToList()
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return database.chapterQueries.getBookmarkedChaptersByMangaId(
            mangaId,
            ChapterMapper::mapChapter,
        ).awaitAsList()
    }

    override suspend fun getFillermarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return database.chapterQueries.getFillermarkedChaptersByMangaId(
            mangaId,
            ChapterMapper::mapChapter,
        ).awaitAsList()
    }

    override suspend fun getChapterById(id: Long): Chapter? {
        return database.chapterQueries.getChapterById(id, ChapterMapper::mapChapter).awaitAsOneOrNull()
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyFilter: Boolean): Flow<List<Chapter>> {
        return database.chapterQueries.getChaptersByMangaId(
            mangaId,
            applyFilter.toLong(),
            // KMK -->
            Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
            Manga.CHAPTER_SHOW_BOOKMARKED,
            Manga.CHAPTER_SHOW_NOT_FILLERMARKED,
            Manga.CHAPTER_SHOW_FILLERMARKED,
            // KMK <--
            ChapterMapper::mapChapter,
        ).subscribeToList()
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
        return database.chapterQueries.getChapterByUrlAndMangaId(
            remoteUrl = url,
            mangaId = mangaId,
            mapper = ChapterMapper::mapChapter,
        ).awaitAsOneOrNull()
    }

    // SY -->
    override suspend fun getChapterByUrl(url: String): List<Chapter> {
        return database.chapterQueries.getChapterByUrl(url, ChapterMapper::mapChapter).awaitAsList()
    }

    override suspend fun getMergedChapterByMangaId(mangaId: Long, applyFilter: Boolean): List<Chapter> {
        return database.chapterQueries.getMergedChaptersByMangaId(
            mangaId,
            applyFilter.toLong(),
            // KMK -->
            Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
            Manga.CHAPTER_SHOW_BOOKMARKED,
            Manga.CHAPTER_SHOW_NOT_FILLERMARKED,
            Manga.CHAPTER_SHOW_FILLERMARKED,
            // KMK <--
            ChapterMapper::mapChapter,
        ).awaitAsList()
    }

    override suspend fun getMergedChapterByMangaIdAsFlow(
        mangaId: Long,
        applyFilter: Boolean,
    ): Flow<List<Chapter>> {
        return database.chapterQueries.getMergedChaptersByMangaId(
            mangaId,
            applyFilter.toLong(),
            // KMK -->
            Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
            Manga.CHAPTER_SHOW_BOOKMARKED,
            Manga.CHAPTER_SHOW_NOT_FILLERMARKED,
            Manga.CHAPTER_SHOW_FILLERMARKED,
            // KMK <--
            ChapterMapper::mapChapter,
        ).subscribeToList()
    }

    override suspend fun getScanlatorsByMergeId(mangaId: Long): List<String> {
        return database.chapterQueries.getScanlatorsByMergeId(mangaId) { it.orEmpty() }.awaitAsList()
    }

    override fun getScanlatorsByMergeIdAsFlow(mangaId: Long): Flow<List<String>> {
        return database.chapterQueries.getScanlatorsByMergeId(mangaId) { it.orEmpty() }.subscribeToList()
    }
    // SY <--
}
// KMK <--
