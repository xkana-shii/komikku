package tachiyomi.data.backup

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import exh.metadata.metadata.base.FlatMetadata
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tachiyomi.data.Database
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.manga.repository.MangaMetadataRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import kotlin.math.max
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RestoreRepositoryImpl(
    private val database: Database,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val trackRepository: TrackRepository,
    // SY -->
    private val mangaMetadataRepository: MangaMetadataRepository,
    private val customMangaRepository: CustomMangaRepository,
    // SY <--
) : RestoreRepository {

    override suspend fun getMangaUrlsBySourceId(): Map<Long, Set<String>> {
        return database.mangaQueries
            .getAllMangaSourceAndUrl()
            .awaitAsList()
            .groupBy({ it.source_id }, { it.remote_url })
            .mapValues { it.value.toSet() }
    }

    override suspend fun restoreManga(
        entries: List<RestoredManga>,
        isSync: Boolean,
        // KUN -->
        restoreCategories: Boolean,
        // KUN <--
        update: suspend (Manga) -> MangaUpdate,
    ) {
        // SY -->
        // 1 while applying a sync so the triggers neither bump versions nor stamp last_modified_at
        val syncingFlag = if (isSync) 1L else 0L
        // SY <--
        database.transaction {
            entries.forEach { entry ->
                // A cancelled restore stops between entries and rolls the whole batch back
                currentCoroutineContext().ensureActive()
                // KUN -->
                mangaRepository.update(update(restore(entry, isSync, syncingFlag, restoreCategories)))
                // KUN <--
            }
            if (isSync) {
                database.mangaQueries.resetIsSyncing()
                database.chapterQueries.resetIsSyncing()
            }
        }
    }

    override suspend fun updateManga(manga: Manga, isSync: Boolean) {
        updateManga(manga, isSync, if (isSync) 1L else 0L)
    }

    private suspend fun restore(
        entry: RestoredManga,
        isSync: Boolean,
        syncingFlag: Long,
        // KUN -->
        restoreCategories: Boolean,
        // KUN <--
    ): Manga {
        val dbManga = mangaRepository.getMangaByUrlAndSourceId(entry.manga.url, entry.manga.source)
        val manga = if (dbManga == null) {
            entry.manga.copy(id = insertManga(entry.manga, syncingFlag))
        } else {
            restoreExistingManga(entry.manga, dbManga, isSync, syncingFlag)
        }

        // SY -->
        // For a sync restore an empty list must clear the assignment, or removing a manga's only
        // category never reaches the other devices (the stale assignment stays forever).
        // KUN -->
        // But an empty category list in a sync delta must only clear local categories if the incoming
        // manga is strictly newer (i.e. explicitly uncategorized by user on another device).
        // If versions/timestamps are equal (such as after restoring a regular backup on both devices),
        // or if categories were not modified, local categories must be preserved rather than wiped.
        val incomingIsStrictlyNewer = dbManga == null ||
            entry.manga.version > dbManga.version ||
            (entry.manga.version == dbManga.version && entry.manga.lastModifiedAt > dbManga.lastModifiedAt)
        val incomingIsNewerOrEqual = incomingIsStrictlyNewer ||
            (entry.manga.version == dbManga.version && entry.manga.lastModifiedAt == dbManga.lastModifiedAt)

        val categoryIds = entry.categoryIds
        if (restoreCategories && categoryIds != null) {
            if (categoryIds.isNotEmpty() && incomingIsNewerOrEqual) {
                mangaRepository.setMangaCategories(manga.id, categoryIds)
            } else if (categoryIds.isEmpty() && isSync && incomingIsStrictlyNewer) {
                mangaRepository.setMangaCategories(manga.id, emptyList())
            }
        }
        // KUN <--
        // SY <--
        restoreChapters(manga, entry.chapters, isSync, syncingFlag)
        restoreTracking(manga, entry.tracks)
        restoreHistory(manga, entry.history)
        restoreExcludedScanlators(manga, entry.excludedScanlators)
        // SY -->
        restoreMergedMangaReferencesForManga(manga.id, entry.mergedMangaReferences)
        entry.flatMetadata?.let { restoreFlatMetadata(manga.id, it) }
        // KUN -->
        if (incomingIsNewerOrEqual) {
            restoreEditedInfo(entry.customMangaInfo?.copy(id = manga.id))
        }
        // KUN <--
        // SY <--
        return manga
    }

    private suspend fun restoreExistingManga(
        manga: Manga,
        dbManga: Manga,
        isSync: Boolean,
        syncingFlag: Long,
    ): Manga {
        // KUN -->
        val incomingIsNewer = manga.version > dbManga.version ||
            (manga.version == dbManga.version && manga.lastModifiedAt > dbManga.lastModifiedAt)
        return if (incomingIsNewer) {
            updateManga(dbManga.copyFrom(manga).copy(id = dbManga.id), isSync, syncingFlag)
        } else {
            updateManga(manga.copyFrom(dbManga).copy(id = dbManga.id), isSync, syncingFlag)
        }
        // KUN <--
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            dateAdded = if (this.favorite || newer.favorite) {
                listOfNotNull(this.dateAdded, newer.dateAdded)
                    .filter { it > Manga.UNKNOWN_FAVORITE_AT }
                    .minOrNull()
                    ?: Manga.UNKNOWN_FAVORITE_AT
            } else {
                null
            },
            // SY -->
            // KUN -->
            ogTitle = newer.ogTitle.ifBlank { newer.title.ifBlank { this.ogTitle } },
            // KUN <--
            ogAuthor = newer.author,
            ogArtist = newer.artist,
            ogDescription = newer.description,
            ogGenre = newer.genre,
            ogThumbnailUrl = newer.thumbnailUrl,
            ogStatus = newer.status,
            // SY <--
            initialized = this.initialized || newer.initialized,
            version = newer.version,
            // SY -->
            lastModifiedAt = newer.lastModifiedAt,
            // SY <--
            nextUpdate = this.nextUpdate ?: newer.nextUpdate,
            // KUN -->
            viewerFlags = newer.viewerFlags,
            chapterFlags = newer.chapterFlags,
            notes = newer.notes,
            memo = newer.memo,
            updateStrategy = newer.updateStrategy,
            favoriteModifiedAt = max(this.favoriteModifiedAt ?: 0L, newer.favoriteModifiedAt ?: 0L).takeIf { it > 0L },
            // KUN <--
        )
    }

    private suspend fun updateManga(manga: Manga, isSync: Boolean, syncingFlag: Long): Manga {
        database.mangaQueries.updateFromBackup(
            source = manga.source,
            url = manga.url,
            // SY -->
            artist = manga.ogArtist,
            author = manga.ogAuthor,
            description = manga.ogDescription,
            genre = manga.ogGenre,
            title = manga.ogTitle,
            status = manga.ogStatus,
            thumbnailUrl = manga.ogThumbnailUrl,
            // SY <--
            userFavoriteAtSet = true,
            userFavoriteAt = if (manga.favorite) manga.dateAdded else null,
            lastUpdate = manga.lastUpdate,
            nextUpdate = null,
            calculateInterval = null,
            initialized = manga.initialized,
            viewer = manga.viewerFlags,
            chapterFlags = manga.chapterFlags,
            coverLastModified = manga.coverLastModified,
            mangaId = manga.id,
            updateStrategy = manga.updateStrategy,
            version = manga.version,
            // SY -->
            lastModifiedAt = if (isSync) manga.lastModifiedAt else null,
            isSyncing = syncingFlag,
            // SY <--
            notes = manga.notes,
            memo = manga.memo,
        )
        return manga
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    private suspend fun insertManga(manga: Manga, syncingFlag: Long): Long {
        return database.mangaQueries.insert(
            sourceId = manga.source,
            remoteUrl = manga.url,
            // SY -->
            remoteArtist = manga.ogArtist,
            remoteAuthor = manga.ogAuthor,
            remoteDescription = manga.ogDescription,
            remoteGenre = manga.ogGenre,
            remoteTitle = manga.ogTitle,
            remoteStatus = manga.ogStatus,
            remoteCover = manga.ogThumbnailUrl,
            // SY <--
            remoteUpdateStrategy = manga.updateStrategy,
            remoteMemo = manga.memo,
            userFavoriteAt = if (manga.favorite) manga.dateAdded else null,
            userNotes = manga.notes,
            userReaderFlags = manga.viewerFlags,
            userChapterFlags = manga.chapterFlags,
            stateChapterLastUpdate = manga.lastUpdate,
            stateChapterNextUpdate = null,
            stateChapterFetchInterval = 0L,
            stateCoverLastModified = manga.coverLastModified,
            stateInitialized = manga.initialized,
            // SY -->
            lastModifiedAt = manga.lastModifiedAt,
            version = manga.version,
            isSyncing = syncingFlag,
            // SY <--
        )
            .awaitAsOne()
    }

    private suspend fun restoreChapters(
        manga: Manga,
        restoredChapters: List<Chapter>,
        isSync: Boolean,
        syncingFlag: Long,
    ) {
        val dbChaptersByUrl = chapterRepository.getChapterByMangaId(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = restoredChapters
            .groupBy { it.url }
            .map { (_, copies) ->
                copies.reduce { kept, other ->
                    kept.copy(
                        read = kept.read || other.read,
                        bookmark = kept.bookmark || other.bookmark,
                        lastPageRead = max(kept.lastPageRead, other.lastPageRead),
                        // KUN -->
                        // Duplicate copies of one chapter may carry different sync counters; keep the
                        // newest so a folded copy cannot roll a synced chapter back.
                        version = max(kept.version, other.version),
                        lastModifiedAt = max(kept.lastModifiedAt, other.lastModifiedAt),
                        // KUN <--
                    )
                }
            }
            .mapNotNull { backupChapter ->
                val chapter = backupChapter.copy(mangaId = manga.id)
                val dbChapter = dbChaptersByUrl[chapter.url]

                when {
                    dbChapter == null -> chapter // New chapter
                    // KUN -->
                    isSync && (chapter.version != dbChapter.version || chapter.lastModifiedAt != dbChapter.lastModifiedAt) ->
                        updateChapterBasedOnSyncState(chapter, dbChapter, isSync)
                    // KUN <--
                    chapter.forComparison() == dbChapter.forComparison() -> null // Same state; skip
                    else -> updateChapterBasedOnSyncState(chapter, dbChapter, isSync) // Update existed chapter
                }
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters, syncingFlag)
        updateExistingChapters(existingChapters, isSync, syncingFlag)
    }

    private fun updateChapterBasedOnSyncState(chapter: Chapter, dbChapter: Chapter, isSync: Boolean): Chapter {
        return if (isSync) {
            // KUN -->
            val incomingIsNewer = chapter.version > dbChapter.version ||
                (chapter.version == dbChapter.version && chapter.lastModifiedAt > dbChapter.lastModifiedAt)
            val localIsNewer = dbChapter.version > chapter.version ||
                (chapter.version == dbChapter.version && dbChapter.lastModifiedAt > chapter.lastModifiedAt)

            val base = when {
                incomingIsNewer -> chapter.copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark || dbChapter.bookmark,
                    read = chapter.read || dbChapter.read,
                    lastPageRead = max(chapter.lastPageRead, dbChapter.lastPageRead),
                    version = chapter.version,
                    lastModifiedAt = chapter.lastModifiedAt,
                )
                localIsNewer -> dbChapter.copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark || dbChapter.bookmark,
                    read = chapter.read || dbChapter.read,
                    lastPageRead = max(chapter.lastPageRead, dbChapter.lastPageRead),
                    version = dbChapter.version,
                    lastModifiedAt = dbChapter.lastModifiedAt,
                )
                else -> chapter.copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark || dbChapter.bookmark,
                    read = chapter.read || dbChapter.read,
                    lastPageRead = max(chapter.lastPageRead, dbChapter.lastPageRead),
                    version = max(chapter.version, dbChapter.version),
                    lastModifiedAt = max(chapter.lastModifiedAt, dbChapter.lastModifiedAt),
                )
            }
            base.copy(
                sourceOrder = if (incomingIsNewer) chapter.sourceOrder else dbChapter.sourceOrder,
                dateUpload = listOfNotNull(chapter.dateUpload, dbChapter.dateUpload).minOrNull(),
            )
            // KUN <--
        } else {
            chapter.copyFrom(dbChapter)
                // KUN -->
                .copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark || dbChapter.bookmark,
                    sourceOrder = max(chapter.sourceOrder, dbChapter.sourceOrder),
                    dateUpload = listOfNotNull(chapter.dateUpload, dbChapter.dateUpload).minOrNull(),
                )
                // KUN <--
                .let {
                    when {
                        dbChapter.read && !it.read -> it.copy(read = true, lastPageRead = dbChapter.lastPageRead)
                        it.lastPageRead == 0 && dbChapter.lastPageRead != 0 -> it.copy(
                            lastPageRead = dbChapter.lastPageRead,
                        )
                        else -> it
                    }
                }
        }
    }

    private fun Chapter.forComparison() =
        this.copy(
            id = 0L,
            mangaId = 0L,
            dateFetch = Instant.DISTANT_PAST,
            // KUN -->
            // dateUpload = 0L, some time source loses dateUpload so we overwrite with backup
            // sourceOrder = 0L, although sourceOrder will be updated on refresh, we want to avoid order mixed up anyway
            // KUN <--
            lastModifiedAt = 0L,
            version = 0L,
        )

    private suspend fun insertNewChapters(chapters: List<Chapter>, syncingFlag: Long) {
        database.transaction {
            chapters.forEach { chapter ->
                database.chapterQueries.insert(
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
                    isSyncing = syncingFlag,
                    // SY <--
                ).awaitAsOneOrNull()
            }
        }
    }

    private suspend fun updateExistingChapters(
        chapters: List<Chapter>,
        isSync: Boolean,
        syncingFlag: Long,
    ) {
        database.transaction {
            chapters.forEach { chapter ->
                database.chapterQueries.updateFromBackup(
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    // KUN -->
                    sourceOrder = chapter.sourceOrder,
                    dateUpload = chapter.dateUpload,
                    // KUN <--
                    chapterId = chapter.id,
                    version = chapter.version,
                    // SY -->
                    lastModifiedAt = if (isSync) chapter.lastModifiedAt else null,
                    isSyncing = syncingFlag,
                    // SY <--
                    memo = chapter.memo,
                )
            }
        }
    }

    private suspend fun restoreHistory(manga: Manga, restoredHistory: List<RestoredHistory>) {
        if (restoredHistory.isEmpty()) return
        // Read the entry's chapters and history once and match the backup entries in memory, instead
        // of looking up the history row (and, failing that, the chapter) for every backup item.
        val chapterIdsByUrl = chapterRepository.getChapterByMangaId(manga.id).associate { it.url to it.id }
        val dbHistoryByChapterId = database.historyQueries
            .getHistoryByMangaId(manga.id)
            .awaitAsList()
            .associateBy { it.chapter_id }

        val toUpdate = restoredHistory
            // Chapter doesn't exist; skip
            .filter { it.chapterUrl in chapterIdsByUrl }
            // A backup of a library that still had duplicate chapters carries a history entry for
            // each copy; they are one chapter now, read as late as any copy and for as long as all
            .groupBy { chapterIdsByUrl.getValue(it.chapterUrl) }
            .map { (chapterId, copies) ->
                // KUN -->
                // A removed/unread history entry is backed up with lastRead == 0 => readAt == null.
                // Writing NULL would make `removeResettedHistory` drop the tombstone, so a removed
                // history entry would not survive a restore; write epoch 0 (as master did) instead.
                val readAt = copies.mapNotNull { it.readAt }.maxOrNull() ?: Instant.fromEpochMilliseconds(0)
                // KUN <--
                val readDuration = copies.sumOf { it.readDuration }
                val dbHistory = dbHistoryByChapterId[chapterId]
                    // New history entry
                    ?: return@map Triple(chapterId, readAt, readDuration)

                // Update history entry
                Triple(
                    chapterId,
                    listOfNotNull(readAt, dbHistory.read_at).maxOrNull(),
                    max(readDuration, dbHistory.read_duration) - dbHistory.read_duration,
                )
            }

        if (toUpdate.isEmpty()) return
        database.transaction {
            toUpdate.forEach { (chapterId, readAt, readDuration) ->
                database.historyQueries.upsert(
                    chapterId = chapterId,
                    readAt = readAt,
                    readDuration = readDuration,
                )
            }
        }
    }

    private suspend fun restoreTracking(manga: Manga, restoredTracks: List<Track>) {
        val dbTrackByTrackerId = trackRepository.getTracksByMangaId(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = restoredTracks
            .mapNotNull { track ->
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        mangaId = manga.id,
                    )

                // KUN -->
                val mergedLastChapterRead = max(track.lastChapterRead, dbTrack.lastChapterRead)
                val mergedScore = if (track.score > 0.0) track.score else dbTrack.score
                val mergedStatus = if (track.lastChapterRead >= dbTrack.lastChapterRead && track.status != 0L) {
                    track.status
                } else if (dbTrack.status != 0L) {
                    dbTrack.status
                } else {
                    track.status
                }
                val mergedStartDate = listOfNotNull(track.startDate, dbTrack.startDate).minOrNull()
                val mergedFinishDate = listOfNotNull(track.finishDate, dbTrack.finishDate).maxOrNull()
                val mergedTotalChapters = max(track.totalChapters, dbTrack.totalChapters)
                val mergedPrivate = track.private || dbTrack.private

                val updatedTrack = dbTrack.copy(
                    title = if (track.title.isNotBlank()) track.title else dbTrack.title,
                    remoteUrl = if (track.remoteUrl.isNotBlank()) track.remoteUrl else dbTrack.remoteUrl,
                    remoteId = if (track.remoteId > 0) track.remoteId else dbTrack.remoteId,
                    libraryId = track.libraryId ?: dbTrack.libraryId,
                    lastChapterRead = mergedLastChapterRead,
                    totalChapters = mergedTotalChapters,
                    status = mergedStatus,
                    score = mergedScore,
                    startDate = mergedStartDate,
                    finishDate = mergedFinishDate,
                    private = mergedPrivate,
                )

                if (updatedTrack == dbTrack) return@mapNotNull null
                updatedTrack
                // KUN <--
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            trackRepository.upsertAll(newTracks)
        }

        if (existingTracks.isEmpty()) return
        database.transaction {
            existingTracks.forEach { track ->
                database.manga_trackQueries.update(
                    mangaId = track.mangaId,
                    trackerId = track.trackerId,
                    remoteId = track.remoteId,
                    remoteUrl = track.remoteUrl,
                    libraryId = track.libraryId,
                    title = track.title,
                    lastChapterRead = track.lastChapterRead,
                    totalChapters = track.totalChapters,
                    status = track.status,
                    score = track.score,
                    startDate = track.startDate,
                    finishDate = track.finishDate,
                    private = track.private,
                    id = track.id,
                )
            }
        }
    }

    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = database.excluded_scanlatorQueries
            .getExcludedScanlatorsByMangaId(manga.id)
            .awaitAsList()
            .toSet()
        val toInsert = excludedScanlators.toSet().subtract(existingExcludedScanlators)
        if (toInsert.isNotEmpty()) {
            database.transaction {
                toInsert.forEach {
                    database.excluded_scanlatorQueries.insert(manga.id, it)
                }
            }
        }
    }

    // SY -->
    /**
     * Restore the merged manga references for the merged manga.
     *
     * @param mergeMangaId the merged manga for the references
     * @param mergedMangaReferences the references for the merged manga
     */
    private suspend fun restoreMergedMangaReferencesForManga(
        mergeMangaId: Long,
        mergedMangaReferences: List<MergedMangaReference>,
    ) {
        if (mergedMangaReferences.isEmpty()) return
        // Get merged manga references from db
        val dbMergedMangaReferences =
            database.mergedQueries.selectAll(MergedMangaMapper::map)
                .awaitAsList()

        mergedMangaReferences.forEach { reference ->
            // If the reference isn't in the db, remove the id and insert a new reference
            if (dbMergedMangaReferences.none {
                    reference.mergeUrl == it.mergeUrl &&
                        reference.mangaUrl == it.mangaUrl
                }
            ) {
                // KUN -->
                val mergedManga = database.mangaQueries
                    // KUN <--
                    .getMangaByUrlAndSource(
                        reference.mangaSourceId,
                        reference.mangaUrl,
                        MangaMapper::mapManga,
                    )
                    // KUN -->
                    .awaitAsOneOrNull()
                    // KUN <--
                    ?: return@forEach
                database.mergedQueries.insert(
                    infoManga = reference.isInfoManga,
                    getChapterUpdates = reference.getChapterUpdates,
                    chapterSortMode = reference.chapterSortMode.toLong(),
                    chapterPriority = reference.chapterPriority.toLong(),
                    downloadChapters = reference.downloadChapters,
                    mergeId = mergeMangaId,
                    mergeUrl = reference.mergeUrl,
                    mangaId = mergedManga.id,
                    mangaUrl = reference.mangaUrl,
                    mangaSource = reference.mangaSourceId,
                ).awaitAsOneOrNull()
            }
        }
    }

    private suspend fun restoreFlatMetadata(mangaId: Long, flatMetadata: FlatMetadata) {
        // KUN -->
        // Mirrors the previous GetFlatMetadataById/InsertFlatMetadata interactors, which swallowed
        // failures so a broken metadata entry could not abort the whole restore.
        val existing = try {
            mangaMetadataRepository.getMetadataById(mangaId)
        } catch (e: Exception) {
            null
        }
        if (existing != null) return
        try {
            mangaMetadataRepository.insertFlatMetadata(
                flatMetadata.copy(
                    metadata = flatMetadata.metadata.copy(mangaId = mangaId),
                    tags = flatMetadata.tags.map { it.copy(mangaId = mangaId) },
                    titles = flatMetadata.titles.map { it.copy(mangaId = mangaId) },
                ),
            )
        } catch (e: Exception) {
            // Empty
        }
        // KUN <--
    }

    private fun restoreEditedInfo(mangaInfo: CustomMangaInfo?) {
        mangaInfo ?: return
        customMangaRepository.set(mangaInfo)
    }
    // SY <--
}
