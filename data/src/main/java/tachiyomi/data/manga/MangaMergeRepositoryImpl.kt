package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.manga.repository.MangaMergeRepository

/**
 * SY-specific file
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaMergeRepositoryImpl(
    private val database: Database,
) : MangaMergeRepository {

    override suspend fun getMergeSettings(mergeId: Long): MergeSettings? {
        return database.mergedQueries
            .selectMerge(mangaId = mergeId, mapper = MergedMangaMapper::mapMergeSettings)
            .awaitAsOneOrNull()
    }

    override suspend fun subscribeMergeSettings(mergeId: Long): Flow<MergeSettings?> {
        return database.mergedQueries
            .selectMerge(mangaId = mergeId, mapper = MergedMangaMapper::mapMergeSettings)
            .subscribeToOneOrNull()
    }

    override suspend fun updateMergeSettings(mergeId: Long, chapterSortMode: Int, infoMangaId: Long?): Boolean {
        return try {
            // KUN -->
            // Write only when a value actually changes. A blanket write would raise the merge
            // parent's version for a no-op save and make every peer re-merge for nothing.
            val current = getMergeSettings(mergeId)
            if (current != null &&
                current.chapterSortMode == chapterSortMode &&
                current.infoMangaId == infoMangaId
            ) {
                return true
            }
            database.transaction {
                database.mergedQueries.updateMerge(
                    mangaId = mergeId,
                    chapterSortMode = chapterSortMode.toLong(),
                    infoMangaId = infoMangaId,
                )
                // The settings live outside the manga row, so no trigger bumps the merge parent.
                database.mangaQueries.bumpSyncVersion(mergeId)
            }
            // KUN <--
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun insertMerge(mangaId: Long, chapterSortMode: Int, infoMangaId: Long?) {
        // KUN -->
        database.transaction {
            database.mergedQueries.insertMerge(
                mangaId = mangaId,
                chapterSortMode = chapterSortMode.toLong(),
                infoMangaId = infoMangaId,
            )
            database.mangaQueries.bumpSyncVersion(mangaId)
        }
        // KUN <--
    }

    override suspend fun getEntriesById(id: Long): List<MergedEntry> {
        return database.mergedQueries
            .selectEntriesByMergeId(mergeId = id, mapper = MergedMangaMapper::mapEntry)
            .awaitAsList()
    }

    override suspend fun subscribeEntriesById(id: Long): Flow<List<MergedEntry>> {
        return database.mergedQueries
            .selectEntriesByMergeId(mergeId = id, mapper = MergedMangaMapper::mapEntry)
            .subscribeToList()
    }

    override suspend fun insert(entry: MergedEntry): Long? {
        return database.transactionWithResult {
            val entryId = database.mergedQueries
                .insertEntryReturningId(
                    mergeId = entry.mergeId,
                    mangaId = entry.mangaId,
                    fetchChapterUpdates = entry.fetchChapterUpdates,
                    downloadChapters = entry.downloadChapters,
                    chapterPriority = entry.chapterPriority.toLong(),
                )
                .awaitAsOneOrNull()
            // KUN -->
            // The entry lives in `merged_entry`, so bump the merge parent explicitly.
            database.mangaQueries.bumpSyncVersion(entry.mergeId)
            // KUN <--
            entryId
        }
    }

    override suspend fun insertAll(entries: List<MergedEntry>) {
        database.transaction {
            entries.forEach { entry ->
                database.mergedQueries.insertEntry(
                    mergeId = entry.mergeId,
                    mangaId = entry.mangaId,
                    fetchChapterUpdates = entry.fetchChapterUpdates,
                    downloadChapters = entry.downloadChapters,
                    chapterPriority = entry.chapterPriority.toLong(),
                )
            }
            // KUN -->
            entries.map { it.mergeId }.distinct().forEach {
                database.mangaQueries.bumpSyncVersion(it)
            }
            // KUN <--
        }
    }

    override suspend fun updateAllEntries(entries: List<MergedEntry>): Boolean {
        return try {
            database.transaction {
                val changedMergeIds = mutableSetOf<Long>()
                entries.forEach { entry ->
                    // KUN -->
                    // Write only when a value actually changes: an unchanged save must not bump
                    // the merge parent, or every peer re-merges for nothing.
                    val current = database.mergedQueries
                        .selectEntryById(entry.id, MergedMangaMapper::mapEntry)
                        .awaitAsOneOrNull()
                        ?: return@forEach
                    if (current.fetchChapterUpdates == entry.fetchChapterUpdates &&
                        current.downloadChapters == entry.downloadChapters &&
                        current.chapterPriority == entry.chapterPriority
                    ) {
                        return@forEach
                    }
                    changedMergeIds.add(current.mergeId)
                    // KUN <--
                    database.mergedQueries.updateEntry(
                        id = entry.id,
                        fetchChapterUpdates = entry.fetchChapterUpdates,
                        downloadChapters = entry.downloadChapters,
                        chapterPriority = entry.chapterPriority.toLong(),
                    )
                }
                // KUN -->
                changedMergeIds.forEach { database.mangaQueries.bumpSyncVersion(it) }
                // KUN <--
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun deleteById(id: Long) {
        // KUN -->
        database.transaction {
            val mergeId = database.mergedQueries.selectMergeIdById(id).awaitAsOneOrNull()
            database.mergedQueries.deleteEntryById(id = id)
            mergeId?.let { database.mangaQueries.bumpSyncVersion(it) }
        }
        // KUN <--
    }

    override suspend fun deleteByMergeId(mergeId: Long) {
        // KUN -->
        database.transaction {
            database.mergedQueries.deleteEntriesByMergeId(mergeId = mergeId)
            database.mangaQueries.bumpSyncVersion(mergeId)
        }
        // KUN <--
    }

    override suspend fun getMergedManga(): List<Manga> {
        return database.mergedQueries
            .selectAllMergedMangas(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun subscribeMergedManga(): Flow<List<Manga>> {
        return database.mergedQueries
            .selectAllMergedMangas(MangaMapper::mapManga)
            .subscribeToList()
    }

    override suspend fun getMergedMangaById(id: Long): List<Manga> {
        return database.mergedQueries
            .selectMergedMangasById(id, MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun subscribeMergedMangaById(id: Long): Flow<List<Manga>> {
        return database.mergedQueries
            .selectMergedMangasById(id, MangaMapper::mapManga)
            .subscribeToList()
    }

    override suspend fun getMergeMangaForDownloading(mergeId: Long): List<Manga> {
        return database.mergedQueries
            .selectMergedMangasForDownloadingById(mergeId = mergeId, mapper = MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getAllEntries(): List<MergedEntry> {
        return database.mergedQueries
            .selectAllEntries(MergedMangaMapper::mapEntry)
            .awaitAsList()
    }

    override suspend fun applyMerge(
        staleMangaId: Long?,
        mergedManga: Manga,
        categoryIds: List<Long>,
        chapterSortMode: Int,
        infoMangaId: Long?,
        entries: List<MergedEntry>,
    ): Manga {
        return database.transactionWithResult {
            if (staleMangaId != null) {
                database.mergedQueries.deleteEntriesByMergeId(mergeId = staleMangaId)
                database.mangaQueries.deleteById(id = staleMangaId)
            }

            val inserted = database.mangaQueries.insertNetworkManga(
                sourceId = mergedManga.source,
                remoteUrl = mergedManga.url,
                remoteArtist = mergedManga.ogArtist,
                remoteAuthor = mergedManga.ogAuthor,
                remoteDescription = mergedManga.ogDescription,
                remoteGenre = mergedManga.ogGenre,
                remoteTitle = mergedManga.ogTitle,
                remoteStatus = mergedManga.ogStatus,
                remoteCover = mergedManga.ogThumbnailUrl,
                // KUN -->
                // The fork's model keeps the favourite flag and its date apart.
                userFavoriteAt = if (mergedManga.favorite) mergedManga.dateAdded else null,
                // KUN <--
                stateChapterLastUpdate = mergedManga.lastUpdate,
                stateChapterNextUpdate = mergedManga.nextUpdate,
                stateChapterFetchInterval = mergedManga.fetchInterval.toLong(),
                stateInitialized = mergedManga.initialized,
                userReaderFlags = mergedManga.viewerFlags,
                userChapterFlags = mergedManga.chapterFlags,
                stateCoverLastModified = mergedManga.coverLastModified,
                remoteUpdateStrategy = mergedManga.updateStrategy,
                remoteMemo = mergedManga.memo,
                updateTitle = mergedManga.ogTitle.isNotBlank(),
                updateCover = !mergedManga.ogThumbnailUrl.isNullOrBlank(),
                updateDetails = mergedManga.initialized,
                // KUN -->
                // The fork's insert path can refresh an existing non-library row; applyMerge builds
                // the entry from a source result, so that is what the old networkToLocalManga call did.
                updateInfo = true,
                // KUN <--
                mapper = MangaMapper::mapManga,
            ).awaitAsOne()

            database.manga_categoryQueries.deleteMangaCategoryByMangaId(inserted.id)
            categoryIds.forEach { categoryId ->
                database.manga_categoryQueries.insert(mangaId = inserted.id, categoryId = categoryId)
            }

            // Create the merge-level settings row only for a fresh merge; preserve it when reusing
            // KUN -->
            // The driver is asynchronous, so the query must be awaited: `executeAsOneOrNull()`
            // throws IllegalStateException and the first merge a user creates never lands.
            val hasMergeSettings = database.mergedQueries
                .selectMerge(mangaId = inserted.id)
                .awaitAsOneOrNull() != null
            // KUN <--
            if (!hasMergeSettings) {
                database.mergedQueries.insertMerge(
                    mangaId = inserted.id,
                    chapterSortMode = chapterSortMode.toLong(),
                    infoMangaId = infoMangaId,
                )
            }

            entries.forEach { entry ->
                database.mergedQueries.insertEntry(
                    mergeId = inserted.id,
                    mangaId = entry.mangaId,
                    fetchChapterUpdates = entry.fetchChapterUpdates,
                    downloadChapters = entry.downloadChapters,
                    chapterPriority = entry.chapterPriority.toLong(),
                )
            }

            // KUN -->
            // The entries live outside the manga row, so no trigger bumps the merge parent. Do it
            // explicitly or the change is never uploaded by the delta sync.
            database.mangaQueries.bumpSyncVersion(inserted.id)
            // KUN <--

            inserted
        }
    }
}
