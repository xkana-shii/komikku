package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class MangaMergeRepositoryImpl(
    private val database: Database,
) : MangaMergeRepository {

    override suspend fun getMergedManga(): List<Manga> {
        return database.mergedQueries
            .selectAllMergedMangas(MangaMapper::mapManga)
            .awaitAsList()
    }

    // SY -->
    override suspend fun getAllReferences(): List<MergedMangaReference> {
        return database.mergedQueries
            .selectAll(MergedMangaMapper::map)
            .awaitAsList()
    }
    // SY <--

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

    override suspend fun getReferencesById(id: Long): List<MergedMangaReference> {
        return database.mergedQueries
            .selectByMergeId(id, MergedMangaMapper::map)
            .awaitAsList()
    }

    override suspend fun subscribeReferencesById(id: Long): Flow<List<MergedMangaReference>> {
        return database.mergedQueries
            .selectByMergeId(id, MergedMangaMapper::map)
            .subscribeToList()
    }

    override suspend fun updateSettings(update: MergeMangaSettingsUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllSettings(values: List<MergeMangaSettingsUpdate>): Boolean {
        return try {
            partialUpdate(*values.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg values: MergeMangaSettingsUpdate) {
        database.transaction {
            val mergeIds = mutableSetOf<Long>()
            values.forEach { value ->
                // KUN -->
                // Write only when a provided value actually changes. A blanket bump would raise the
                // merge parent's version for a no-op save and make every peer re-merge for nothing.
                val dbReference = database.mergedQueries
                    .selectById(value.id, MergedMangaMapper::map)
                    .awaitAsOneOrNull()
                    ?: return@forEach
                if (!value.changesAgainst(dbReference)) return@forEach
                dbReference.mergeId?.let(mergeIds::add)
                // KUN <--
                database.mergedQueries.updateSettingsById(
                    id = value.id,
                    getChapterUpdates = value.getChapterUpdates,
                    downloadChapters = value.downloadChapters,
                    infoManga = value.isInfoManga,
                    chapterPriority = value.chapterPriority?.toLong(),
                    chapterSortMode = value.chapterSortMode?.toLong(),
                )
            }
            // KUN -->
            // The settings live in `merged`, not in the manga row, so no trigger bumps the merge
            // parent. Do it explicitly or the change is never uploaded by the delta sync.
            mergeIds.forEach { database.mangaQueries.bumpSyncVersion(it) }
            // KUN <--
        }
    }

    // KUN -->
    /** Mirrors `updateSettingsById`'s `coalesce`: only a provided (non-null) value can change. */
    private fun MergeMangaSettingsUpdate.changesAgainst(reference: MergedMangaReference): Boolean =
        (getChapterUpdates != null && getChapterUpdates != reference.getChapterUpdates) ||
            (downloadChapters != null && downloadChapters != reference.downloadChapters) ||
            (isInfoManga != null && isInfoManga != reference.isInfoManga) ||
            (chapterPriority != null && chapterPriority != reference.chapterPriority) ||
            (chapterSortMode != null && chapterSortMode != reference.chapterSortMode)
    // KUN <--

    override suspend fun insert(reference: MergedMangaReference): Long? {
        return database.transactionWithResult {
            val referenceId = database.mergedQueries
                .insert(
                    infoManga = reference.isInfoManga,
                    getChapterUpdates = reference.getChapterUpdates,
                    chapterSortMode = reference.chapterSortMode.toLong(),
                    chapterPriority = reference.chapterPriority.toLong(),
                    downloadChapters = reference.downloadChapters,
                    mergeId = reference.mergeId!!,
                    mergeUrl = reference.mergeUrl,
                    mangaId = reference.mangaId,
                    mangaUrl = reference.mangaUrl,
                    mangaSource = reference.mangaSourceId,
                )
                .awaitAsOneOrNull()
            // KUN -->
            // The reference lives in `merged`, so bump the merge parent explicitly.
            reference.mergeId?.let { database.mangaQueries.bumpSyncVersion(it) }
            // KUN <--
            referenceId
        }
    }

    override suspend fun insertAll(references: List<MergedMangaReference>) {
        database.transaction {
            references.forEach { reference ->
                database.mergedQueries.insert(
                    infoManga = reference.isInfoManga,
                    getChapterUpdates = reference.getChapterUpdates,
                    chapterSortMode = reference.chapterSortMode.toLong(),
                    chapterPriority = reference.chapterPriority.toLong(),
                    downloadChapters = reference.downloadChapters,
                    mergeId = reference.mergeId!!,
                    mergeUrl = reference.mergeUrl,
                    mangaId = reference.mangaId,
                    mangaUrl = reference.mangaUrl,
                    mangaSource = reference.mangaSourceId,
                ).awaitAsOne()
            }
            // KUN -->
            references.map { it.mergeId!! }.distinct().forEach {
                database.mangaQueries.bumpSyncVersion(it)
            }
            // KUN <--
        }
    }

    override suspend fun applyMerge(
        staleMangaId: Long?,
        mergedManga: Manga,
        categoryIds: List<Long>,
        references: List<MergedMangaReference>,
    ): Manga {
        return database.transactionWithResult {
            if (staleMangaId != null) {
                database.mergedQueries.deleteByMergeId(staleMangaId)
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

            references.forEach { reference ->
                val mangaId = if (reference.mangaSourceId == MERGED_SOURCE_ID) inserted.id else reference.mangaId
                database.mergedQueries.insert(
                    infoManga = reference.isInfoManga,
                    getChapterUpdates = reference.getChapterUpdates,
                    chapterSortMode = reference.chapterSortMode.toLong(),
                    chapterPriority = reference.chapterPriority.toLong(),
                    downloadChapters = reference.downloadChapters,
                    mergeId = inserted.id,
                    mergeUrl = reference.mergeUrl,
                    mangaId = mangaId,
                    mangaUrl = reference.mangaUrl,
                    mangaSource = reference.mangaSourceId,
                )
            }

            // KUN -->
            // The references live in `merged`, not in the manga row, so no trigger bumps the merge
            // parent. Do it explicitly or the change is never uploaded by the delta sync.
            database.mangaQueries.bumpSyncVersion(inserted.id)
            // KUN <--

            inserted
        }
    }

    override suspend fun deleteById(id: Long) {
        // KUN -->
        database.transaction {
            val mergeId = database.mergedQueries.selectMergeIdById(id).awaitAsOneOrNull()
            database.mergedQueries.deleteById(id)
            mergeId?.let { database.mangaQueries.bumpSyncVersion(it) }
        }
        // KUN <--
    }

    override suspend fun deleteByMergeId(mergeId: Long) {
        // KUN -->
        database.transaction {
            database.mergedQueries.deleteByMergeId(mergeId)
            database.mangaQueries.bumpSyncVersion(mergeId)
        }
        // KUN <--
    }

    override suspend fun getMergeMangaForDownloading(mergeId: Long): List<Manga> {
        return database.mergedQueries
            .selectMergedMangasForDownloadingById(mergeId, MangaMapper::mapManga)
            .awaitAsList()
    }
}
