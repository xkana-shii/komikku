package tachiyomi.data.chapter

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.chapter.ChapterMapper.mapChapter
import tachiyomi.data.subscribeToList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterRemoteUpdate
import tachiyomi.domain.chapter.model.ChapterSummary
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ChapterRepositoryImpl(
    private val database: Database,
) : ChapterRepository {

    override suspend fun addAll(chapters: List<Chapter>): List<Chapter> {
        return try {
            database.transactionWithResult {
                chapters.mapNotNull { chapter ->
                    // KUN -->
                    // `ON CONFLICT DO NOTHING` returns no row when a concurrent sync already stored the
                    // chapter: skip it instead of letting awaitAsOne throw and abort the whole batch
                    val lastInsertId = database.chapterQueries.insert(
                        mangaId = chapter.mangaId,
                        remoteUrl = chapter.url,
                        remoteName = chapter.name,
                        remoteScanlator = chapter.scanlator,
                        remoteChapterNumber = chapter.chapterNumber,
                        remoteDateUpload = chapter.dateUpload,
                        remoteOrder = chapter.sourceOrder,
                        remoteMemo = chapter.memo,
                        userRead = chapter.read,
                        userBookmark = chapter.bookmark,
                        userLastPageRead = chapter.lastPageRead,
                        stateDateFetch = chapter.dateFetch,
                        // SY -->
                        lastModifiedAt = chapter.lastModifiedAt,
                        version = chapter.version,
                        isSyncing = 0L,
                        // SY <--
                    ).awaitAsOneOrNull() ?: return@mapNotNull null
                    // KUN <--
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

    override suspend fun updateAllRemote(chapterUpdates: List<ChapterRemoteUpdate>) {
        database.transaction {
            chapterUpdates.forEach { chapterUpdate ->
                database.chapterQueries.updateRemote(
                    name = chapterUpdate.name,
                    scanlator = chapterUpdate.scanlator,
                    chapterNumber = chapterUpdate.chapterNumber,
                    sourceOrder = chapterUpdate.sourceOrder,
                    dateUpload = chapterUpdate.dateUpload,
                    chapterId = chapterUpdate.id,
                    memo = chapterUpdate.memo,
                )
            }
        }
    }

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
                    database.chapterQueries.getChaptersByMangaId(
                        mangaId,
                        false.toLong(),
                        // KMK -->
                        Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                        Manga.CHAPTER_SHOW_BOOKMARKED,
                        // KMK <--
                        ChapterMapper::mapChapter,
                    )
                        .awaitAsList()
                        .map { mangaId to it.url }
                }
                .toMutableSet()

            val stored = added
                .filter { existing.add(it.mangaId to it.url) }
                .mapNotNull { chapter ->
                    // KUN -->
                    // A concurrent sync may have inserted this chapter between the query above and here:
                    // `DO NOTHING` returns no row, so skip it rather than aborting the transaction
                    val lastInsertId = database.chapterQueries.insert(
                        mangaId = chapter.mangaId,
                        remoteUrl = chapter.url,
                        remoteName = chapter.name,
                        remoteScanlator = chapter.scanlator,
                        remoteChapterNumber = chapter.chapterNumber,
                        remoteDateUpload = chapter.dateUpload,
                        remoteOrder = chapter.sourceOrder,
                        remoteMemo = chapter.memo,
                        userRead = chapter.read,
                        userBookmark = chapter.bookmark,
                        userLastPageRead = chapter.lastPageRead,
                        stateDateFetch = chapter.dateFetch,
                        // SY -->
                        lastModifiedAt = chapter.lastModifiedAt,
                        version = chapter.version,
                        isSyncing = 0L,
                        // SY <--
                    ).awaitAsOneOrNull() ?: return@mapNotNull null
                    // KUN <--
                    chapter.copy(id = lastInsertId)
                }

            updated.forEach { chapterUpdate ->
                database.chapterQueries.updateRemote(
                    name = chapterUpdate.name,
                    scanlator = chapterUpdate.scanlator,
                    chapterNumber = chapterUpdate.chapterNumber,
                    sourceOrder = chapterUpdate.sourceOrder,
                    dateUpload = chapterUpdate.dateUpload,
                    chapterId = chapterUpdate.id,
                    memo = chapterUpdate.memo,
                )
            }

            stored
        }
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        database.transaction {
            chapterUpdates.forEach { chapterUpdate ->
                database.chapterQueries.update(
                    read = chapterUpdate.read,
                    bookmark = chapterUpdate.bookmark,
                    lastPageRead = chapterUpdate.lastPageRead,
                    dateFetch = chapterUpdate.dateFetch,
                    chapterId = chapterUpdate.id,
                    version = chapterUpdate.version,
                    // SY -->
                    lastModifiedAt = null,
                    isSyncing = null,
                    // SY <--
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
        return database.chapterQueries
            .getChaptersByMangaId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
            .awaitAsList()
    }

    override suspend fun getChapterSummaryByMangaId(mangaId: Long): ChapterSummary {
        return database.chapterQueries
            .getChapterSummaryByMangaId(mangaId) { total, latestChapterNumber ->
                ChapterSummary(chapterCount = total, latestChapterNumber = latestChapterNumber)
            }
            .awaitAsOne()
    }

    override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> {
        return database.chapterQueries
            .getScanlatorsByMangaId(mangaId) { it.orEmpty() }
            .awaitAsList()
    }

    override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> {
        return database.chapterQueries
            .getScanlatorsByMangaId(mangaId) { it.orEmpty() }
            .subscribeToList()
    }

    override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> {
        return database.chapterQueries
            .getBookmarkedChaptersByMangaId(mangaId, ::mapChapter)
            .awaitAsList()
    }

    override suspend fun getChapterById(id: Long): Chapter? {
        return database.chapterQueries
            .getChapterById(id, ::mapChapter)
            .awaitAsOneOrNull()
    }

    override suspend fun getChapterByMangaIdAsFlow(mangaId: Long, applyFilter: Boolean): Flow<List<Chapter>> {
        return database.chapterQueries
            .getChaptersByMangaId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
            .subscribeToList()
    }

    override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
        return database.chapterQueries
            .getChapterByUrlAndMangaId(mangaId, url, ::mapChapter)
            .awaitAsOneOrNull()
    }

    // KUN -->
    override suspend fun getChaptersByMangaIds(mangaIds: List<Long>): List<Chapter> {
        return database.ehQueries
            .getChaptersByMangaIds(mangaIds, ChapterMapper::mapChapter)
            .awaitAsList()
    }
    // KUN <--

    // SY -->
    override suspend fun getChapterByUrl(url: String): List<Chapter> {
        return database.chapterQueries
            .getChapterByUrl(url, ::mapChapter)
            .awaitAsList()
    }

    override suspend fun getMergedChapterByMangaId(mangaId: Long, applyFilter: Boolean): List<Chapter> {
        return database.chapterQueries
            .getMergedChaptersByMangaId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
            .awaitAsList()
    }

    override suspend fun getMergedChapterByMangaIdAsFlow(
        mangaId: Long,
        applyFilter: Boolean,
    ): Flow<List<Chapter>> {
        return database.chapterQueries
            .getMergedChaptersByMangaId(
                mangaId,
                applyFilter.toLong(),
                // KMK -->
                Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                Manga.CHAPTER_SHOW_BOOKMARKED,
                // KMK <--
                ChapterMapper::mapChapter,
            )
            .subscribeToList()
    }

    override suspend fun getScanlatorsByMergeId(mangaId: Long): List<String> {
        return database.chapterQueries
            .getScanlatorsByMergeId(mangaId) { it.orEmpty() }
            .awaitAsList()
    }

    override fun getScanlatorsByMergeIdAsFlow(mangaId: Long): Flow<List<String>> {
        return database.chapterQueries
            .getScanlatorsByMergeId(mangaId) { it.orEmpty() }
            .subscribeToList()
    }
    // SY <--
}
