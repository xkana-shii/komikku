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
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.history.HistoryMapper
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.domain.backup.model.RestoredForkState
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.model.RestoredMergeSettings
import tachiyomi.domain.backup.model.RestoredMergedEntry
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.model.History
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.model.toMangaUserInfoUpdate
import tachiyomi.domain.manga.repository.MangaMergeRepository
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
        /* SY --> */
        isSync: Boolean /* SY <-- */,
        // KUN -->
        restoreCategories: Boolean,
        restoreTracking: Boolean,
        restoreHistory: Boolean,
        restoreCustomInfo: Boolean,
        removeMissingChaptersBefore: Long?,
        // KUN <--
        update: suspend (Manga) -> MangaUpdate,
    ) {
        // SY -->
        // 1 while applying a sync so the triggers neither bump versions nor stamp last_modified_at
        val syncingFlag = if (isSync) SYNCING else NOT_SYNCING
        // SY <--
        database.transaction {
            entries.forEach { entry ->
                // A cancelled restore stops between entries and rolls the whole batch back
                currentCoroutineContext().ensureActive()
                // KUN -->
                mangaRepository.update(
                    update(
                        restore(
                            entry,
                            isSync,
                            syncingFlag,
                            restoreCategories,
                            restoreTracking,
                            restoreHistory,
                            restoreCustomInfo,
                            removeMissingChaptersBefore,
                        ),
                    ),
                )
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
        /* SY --> */
        isSync: Boolean,
        syncingFlag: Long /* SY <-- */,
        // KUN -->
        restoreCategories: Boolean,
        restoreTracking: Boolean,
        restoreHistory: Boolean,
        restoreCustomInfo: Boolean,
        removeMissingChaptersBefore: Long?,
        // KUN <--
    ): Manga {
        val dbManga = mangaRepository.getMangaByUrlAndSourceId(entry.manga.url, entry.manga.source)
        val manga = if (dbManga == null) {
            entry.manga.copy(id = insertManga(entry.manga, isSync, syncingFlag))
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
        // KUN -->
        restoreChapters(manga, entry.chapters, isSync, syncingFlag, removeMissingChaptersBefore)
        restoreTracking(
            manga,
            entry.tracks,
            restoreTracking,
        )
        restoreHistory(
            manga,
            entry.history,
            isSync,
            incomingIsNewerOrEqual,
            restoreHistory,
        )
        restoreExcludedScanlators(
            manga,
            entry.excludedScanlators,
            isSync,
            incomingIsNewerOrEqual,
            incomingIsStrictlyNewer,
        )
        // KUN <--
        // KNS -->
        entry.forkState?.let { restoreForkState(manga, it, isSync, incomingIsNewerOrEqual, incomingIsStrictlyNewer) }
        // KNS <--
        // SY -->
        restoreMergedManga(
            manga.id,
            entry.mergedEntries,
            entry.mergeSettings,
            // KUN -->
            isSync,
            incomingIsNewerOrEqual,
            entry.mergedMangaReferencesIncluded,
            // KUN <--
        )
        entry.flatMetadata?.let { restoreFlatMetadata(manga.id, it) }
        // KUN -->
        if (restoreCustomInfo) {
            val incoming = entry.manga.toMangaUserInfoUpdate().copy(id = manga.id)
            // The overrides as they were before this entry's row was written, so the merge compares
            // against what the user had rather than against what the payload just brought in.
            val stored = dbManga?.toMangaUserInfoUpdate()
            // A marker means the sender's payload carries the complete override set, including the
            // empty one that means "cleared". Only then may the receiver replace instead of merge,
            // and a sync payload must still win the version comparison: a stale payload must never
            // remove overrides edited locally since. A manual restore of a marked backup is
            // authoritative regardless of the version comparison.
            val replaceOverrides = entry.customInfoIncluded == true &&
                (!isSync || incomingIsNewerOrEqual)
            if (replaceOverrides) {
                // Writing every column, NULLs included, is exactly the "clear every override" write
                // an all-null payload asks for. A marked-but-unchanged entry is the common case
                // (every synced manga carries the marker), so skip the write unless the normalized
                // value actually differs from what is stored.
                if (incoming.normalized() != stored.normalized()) {
                    mangaRepository.updateUserInfoAll(listOf(incoming.normalizedOrCleared()))
                }
            } else if (incomingIsNewerOrEqual) {
                // A legacy/unmarked payload is partial, not authoritative: merge field by field with
                // what is stored, so omitting a field never drops a local override.
                mergeEditedInfo(stored, incoming)
            }
        }
        // KUN <--
        // SY <--
        return manga
    }

    private suspend fun restoreExistingManga(
        manga: Manga,
        dbManga: Manga,
        /* SY --> */
        isSync: Boolean,
        syncingFlag: Long /* SY <-- */,
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
            // Read the raw remote fields, not the custom-info aware accessors (`title`, `author`,
            // ...). This merge also runs for the local-wins branch with a Manga loaded from the
            // database, whose accessors may return custom metadata; writing that back would copy
            // the user's overrides into the remote columns, which a later refresh could then
            // propagate to the source or to other devices.
            ogTitle = newer.ogTitle.ifBlank { this.ogTitle },
            ogAuthor = newer.ogAuthor,
            ogArtist = newer.ogArtist,
            ogDescription = newer.ogDescription,
            ogGenre = newer.ogGenre,
            ogThumbnailUrl = newer.ogThumbnailUrl,
            ogStatus = newer.ogStatus,
            // KUN <--
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

    private suspend fun updateManga(manga: Manga, /* SY --> */ isSync: Boolean, syncingFlag: Long /* SY <-- */): Manga {
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
            // KUN -->
            // Only a sync restore carries an authoritative favorite timestamp. Ordinary restore keeps
            // the existing timestamp so it does not look like a fresh favorite change.
            favoriteModifiedAt = if (isSync) manga.favoriteModifiedAt else null,
            // KUN <--
            // KNS -->
            // Fork-owned per-manga state is reconciled by its own feature restore (blacklist,
            // scanlator rules, Smart Merge override); this generic update keeps the stored values.
            blacklistedChapters = null,
            scanlatorPriority = null,
            scanlatorRangeRules = null,
            smartMergeOverrideSet = false,
            smartMergeOverride = null,
            // KNS <--
        )
        return manga
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    private suspend fun insertManga(manga: Manga, /* SY --> */ isSync: Boolean, syncingFlag: Long /* SY <-- */): Long {
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
            // KUN -->
            // Only a sync restore carries an authoritative favorite timestamp. An ordinary restore
            // passes NULL so the insert trigger stamps `now`, matching `updateManga`.
            favoriteModifiedAt = if (isSync) manga.favoriteModifiedAt else null,
            // KUN <--
            // KNS -->
            userBlacklistedChapters = manga.blacklistedChapters,
            userScanlatorPriority = manga.scanlatorPriority,
            userScanlatorRangeRules = manga.scanlatorRangeRules,
            userSmartMergeOverride = manga.smartMergeOverride,
            // KNS <--
        )
            .awaitAsOne()
    }

    private suspend fun restoreChapters(
        manga: Manga,
        restoredChapters: List<Chapter>,
        /* SY --> */
        isSync: Boolean,
        syncingFlag: Long /* SY <-- */,
        // KUN -->
        removeMissingChaptersBefore: Long?,
        // KUN <--
    ) {
        val dbChaptersByUrl = chapterRepository.getChapterByMangaId(manga.id)
            .associateBy { it.url }

        // KUN -->
        // In a SyncYomi v2 response every manga carries its complete chapter list, so a local
        // chapter missing from it was deleted elsewhere (and tombstoned on the server). Restore
        // only upserts, so without this the other devices keep the chapter forever. An empty list
        // is authoritative: if the server has no live chapters left for the manga, drop them all.
        //
        // Only chapters that existed before the upload snapshot are candidates. A library update
        // can add a chapter while this sync is in flight; it is absent from both the upload and
        // the response, but it was never tombstoned and must survive. Its lastModifiedAt is newer
        // than the snapshot, so the next delta can upload it.
        //
        // Downloaded files are left on disk; BackupRestorer's cache invalidation surfaces them as
        // invalid downloads, which the user can clear from storage settings.
        if (removeMissingChaptersBefore != null) {
            val incomingUrls = restoredChapters.mapTo(mutableSetOf()) { it.url }
            val removedIds = dbChaptersByUrl.values
                .filterNot { it.url in incomingUrls || it.lastModifiedAt >= removeMissingChaptersBefore }
                .map { it.id }
            if (removedIds.isNotEmpty()) {
                // One bind per id: SQLite below 3.32 (API 26-29) limits a statement to 999 variables.
                removedIds.chunked(500).forEach { chunk ->
                    chapterRepository.removeChaptersWithIds(chunk)
                }
                logcat(LogPriority.DEBUG) {
                    "Sync dropped ${removedIds.size} tombstoned chapter(s) of manga ${manga.id}"
                }
            }
        }
        // KUN <--

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
                // The newer writer owns the state outright: an unread chapter, a removed bookmark or
                // lower progress from another device must reach this one instead of being merged away.
                incomingIsNewer -> chapter.copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark,
                    read = chapter.read,
                    // An explicit unread/reset wins; when both sides are read, keep the furthest
                    // progress rather than rolling the reader backwards.
                    lastPageRead = if (chapter.read && dbChapter.read) {
                        max(chapter.lastPageRead, dbChapter.lastPageRead)
                    } else {
                        chapter.lastPageRead
                    },
                    version = chapter.version,
                    lastModifiedAt = chapter.lastModifiedAt,
                )
                // The local write is newer; keep it as-is, including a local read/bookmark/progress reset.
                localIsNewer -> dbChapter
                // No winner: keep the union of both sides.
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
                // KNS -->
                bookmarkColor = when {
                    // The newer writer owns the color too (a removed bookmark has no color); an
                    // unknown color keeps the local one.
                    incomingIsNewer -> when {
                        !base.bookmark -> BookmarkColor.DEFAULT_ID
                        chapter.bookmarkColor == Chapter.BOOKMARK_COLOR_UNKNOWN -> dbChapter.bookmarkColor
                        else -> chapter.bookmarkColor
                    }
                    localIsNewer -> dbChapter.bookmarkColor
                    else -> chapter.bookmarkColor.takeIf { it > BookmarkColor.DEFAULT_ID } ?: dbChapter.bookmarkColor
                },
                // KNS <--
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
                    // An ordinary restore merges backup state but must not move the sync version
                    // backwards: an older backup would otherwise leave a newer stored timestamp next
                    // to an old version, and the next sync would treat that stale version as newer.
                    version = max(chapter.version, dbChapter.version),
                    // KNS -->
                    // A backup only brings a color in; it never resets a local color.
                    bookmarkColor = chapter.bookmarkColor.takeIf { it > BookmarkColor.DEFAULT_ID }
                        ?: dbChapter.bookmarkColor,
                    // KNS <--
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
                    // KNS -->
                    userBookmarkColor = chapter.bookmarkColor.coerceAtLeast(BookmarkColor.DEFAULT_ID).toLong(),
                    // KNS <--
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
                    // Existing chapters kept their old title/scanlator/number, so a restore only
                    // refreshed new URLs. Direct assignment is safe for them: the merged chapter is
                    // the incoming copy when it wins on a sync restore, and an explicit null must
                    // clear a stale scanlator. dateFetch is gated by isSync so an ordinary restore of
                    // an older backup cannot move the Updates feed chronology backwards.
                    name = chapter.name,
                    scanlator = chapter.scanlator,
                    chapterNumber = chapter.chapterNumber,
                    dateFetch = if (isSync) chapter.dateFetch else null,
                    // KUN <--
                    chapterId = chapter.id,
                    version = chapter.version,
                    // SY -->
                    lastModifiedAt = if (isSync) chapter.lastModifiedAt else null,
                    isSyncing = syncingFlag,
                    // SY <--
                    memo = chapter.memo,
                    // KNS -->
                    bookmarkColor = chapter.bookmarkColor.takeIf { it >= 0 }?.toLong(),
                    // KNS <--
                )
            }
        }
    }

    // KUN -->
    private suspend fun restoreHistory(
        manga: Manga,
        restoredHistory: List<RestoredHistory>,
        isSync: Boolean,
        incomingIsNewerOrEqual: Boolean,
        restoreHistory: Boolean,
    ) {
        // A disabled section is a hard no-op: no reads, no writes, no version/timestamp changes.
        if (!restoreHistory) return
        // Read the entry's chapters and history once and match the backup entries in memory, instead
        // of looking up the history row (and, failing that, the chapter) for every backup item.
        val chapterIdsByUrl = chapterRepository.getChapterByMangaId(manga.id).associate { it.url to it.id }
        val dbHistoryByChapterId = database.historyQueries
            .getHistoryByMangaId(manga.id, HistoryMapper::mapHistory)
            .awaitAsList()
            .associateBy { it.chapterId }

        // A backup of a library that still had duplicate chapters carries a history entry for each
        // copy; they are one chapter now, read as late as any copy and for as long as all of them.
        // A null readAt is an explicit tombstone for that entry, not an absent one.
        val incomingByChapterId = restoredHistory
            // The chapter is unknown locally; ignore it instead of guessing an id.
            .filter { it.chapterUrl in chapterIdsByUrl }
            .groupBy { chapterIdsByUrl.getValue(it.chapterUrl) }
            .mapValues { (_, copies) ->
                copies.mapNotNull { it.readAt }.maxOrNull() to copies.sumOf { it.readDuration }
            }

        // Only an explicit tombstone (lastRead == 0 / readAt == null) removes an entry, and only a
        // winning sync payload may write it. The payload is never treated as a complete list: the
        // manga payload is versioned per manga rather than per history section, so a peer with
        // history disabled can upload an empty section and any manga edit carries the whole
        // section. Absent entries are therefore preserved; deletion propagation needs a
        // protocol-level tombstone design.
        val payloadWins = isSync && incomingIsNewerOrEqual
        database.transaction {
            applyHistoryEntries(
                incomingByChapterId,
                dbHistoryByChapterId,
                allowRemoval = payloadWins,
            )
        }
    }

    private suspend fun applyHistoryEntries(
        incomingByChapterId: Map<Long, Pair<Instant?, Long>>,
        dbHistoryByChapterId: Map<Long, History>,
        allowRemoval: Boolean,
    ) {
        incomingByChapterId.forEach { (chapterId, incoming) ->
            val (incomingReadAt, incomingDuration) = incoming
            val dbHistory = dbHistoryByChapterId[chapterId]
            when {
                dbHistory == null -> {
                    // A tombstone for an entry that never existed locally has nothing to preserve.
                    if (incomingReadAt != null) {
                        database.historyQueries.upsert(
                            chapterId = chapterId,
                            readAt = incomingReadAt,
                            readDuration = incomingDuration,
                        )
                    }
                }
                incomingReadAt == null -> {
                    if (allowRemoval) {
                        // lastRead == 0 means this specific entry was removed. Write NULL, never
                        // epoch 0, and keep the duration so the removal is not mistaken for a
                        // chapter that was never read. Match on the chapter id, never the history
                        // row id: the two differ.
                        database.historyQueries.setHistoryFromRestore(
                            readAt = null,
                            readDuration = incomingDuration,
                            chapterId = chapterId,
                        )
                    }
                    // Manual/losing payload: an old backup predates the local read, so skip it.
                }
                else -> {
                    database.historyQueries.setHistoryFromRestore(
                        // Never move a read timestamp backwards, even when the payload wins: an
                        // older payload could otherwise overwrite a newer local read date.
                        readAt = listOfNotNull(incomingReadAt, dbHistory.readAt).maxOrNull(),
                        readDuration = incomingDuration,
                        chapterId = chapterId,
                    )
                }
            }
        }
    }
    // KUN <--

    // KUN -->
    private suspend fun restoreTracking(
        manga: Manga,
        restoredTracks: List<Track>,
        restoreTracking: Boolean,
    ) {
        // A disabled section is a hard no-op: no reads, no writes, no version/timestamp changes.
        if (!restoreTracking) return
        val dbTrackByTrackerId = trackRepository.getTracksByMangaId(manga.id).associateBy { it.trackerId }
        // A backup may carry several rows for one tracker. Keep the last one and normalise the
        // legacy `libraryId = 0` placeholder to "not in a tracker list" (null).
        val incomingTracks = restoredTracks
            .map { it.copy(libraryId = it.libraryId.takeIf { id -> id != 0L }) }
            .associateBy { it.trackerId }
            .values

        // Merge monotonically and never delete. Tracks absent from the payload are preserved: the
        // manga payload is versioned per manga, not per tracking section, so a peer with tracking
        // disabled can upload an empty section and any manga edit carries the whole section.
        // Deletion propagation needs protocol tombstones/per-tracker versions; until then an empty
        // or partial list must never be treated as delete-all.
        val (existingTracks, newTracks) = incomingTracks
            .mapNotNull { track ->
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        mangaId = manga.id,
                    )

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

    // SY -->
    // KUN -->
    /**
     * Restore a merged entry's children and its merge-level settings.
     *
     * An ordinary restore and a losing sync payload only add missing entries and refresh the ones
     * they know: an older payload cannot know which children the user merged afterwards. A winning
     * sync payload can also delete, but only when its marker says the list is complete
     * ([mergedMangaReferencesIncluded]) and every incoming entry resolves to a local manga: an
     * unresolvable entry means the payload was built against a library this device does not have.
     * A legacy payload that omitted the field entirely must never delete — absence is "unknown",
     * not "no children".
     *
     * The writes go straight to the merged tables, never through `MangaMergeRepository`: this is a
     * sync apply, so the merge parent's version must not be bumped.
     */
    private suspend fun restoreMergedManga(
        mergeMangaId: Long,
        backupMergedEntries: List<RestoredMergedEntry>,
        mergeSettings: RestoredMergeSettings?,
        isSync: Boolean,
        incomingIsNewerOrEqual: Boolean,
        mergedMangaReferencesIncluded: Boolean?,
    ) {
        val payloadWins = isSync && incomingIsNewerOrEqual
        val mayDelete = mergedMangaReferencesIncluded == true
        if (!payloadWins && backupMergedEntries.isEmpty() && mergeSettings == null) return
        if (payloadWins && !mayDelete && backupMergedEntries.isEmpty()) return

        // Only this merge's entries: the tables are shared by every merge, so reconciling against
        // them all would touch unrelated merges.
        val dbEntries = database.mergedQueries
            .selectEntriesByMergeId(mergeMangaId, MergedMangaMapper::mapEntry)
            .awaitAsList()

        // Resolve every incoming entry to a local child first: the identity of an entry is the
        // manga row, not the (source, url) pair the payload carries.
        val resolved = backupMergedEntries.mapNotNull { incoming ->
            val childId = database.mangaQueries
                .getMangaByUrlAndSource(incoming.mangaSourceId, incoming.mangaUrl, MangaMapper::mapManga)
                .awaitAsOneOrNull()
                ?.id
                ?: return@mapNotNull null
            childId to incoming
        }
        // An incoming child this device does not have means the payload was built against another
        // library, so it may neither delete nor rewrite what is here: add-only, like a payload that
        // lost the version comparison.
        val addOnly = !payloadWins || resolved.size != backupMergedEntries.size
        if (addOnly && payloadWins && resolved.size != backupMergedEntries.size) {
            logcat(LogPriority.DEBUG) {
                "Sync kept merged entries of manga $mergeMangaId: an incoming child is not local"
            }
        }

        // Settings: the info entry is stored by id, so it is resolved the same way. A losing sync
        // payload must not rewrite them; a manual restore is authoritative.
        if (!isSync || incomingIsNewerOrEqual) {
            mergeSettings?.let { settings ->
                val infoMangaId = if (settings.infoMangaSourceId != 0L) {
                    database.mangaQueries
                        .getMangaByUrlAndSource(settings.infoMangaSourceId, settings.infoMangaUrl, MangaMapper::mapManga)
                        .awaitAsOneOrNull()
                        ?.id
                } else {
                    null
                }
                database.mergedQueries.insertMerge(
                    mangaId = mergeMangaId,
                    chapterSortMode = settings.chapterSortMode.toLong(),
                    infoMangaId = infoMangaId,
                )
            }
        }

        val dbByChildId = dbEntries.associateBy { it.mangaId }
        val incomingChildIds = resolved.mapTo(mutableSetOf()) { it.first }
        database.transaction {
            // Delete only when the payload said its list is complete and every incoming entry could
            // be resolved; a legacy or partial payload is add-only.
            if (!addOnly && mayDelete) {
                dbEntries.forEach { entry ->
                    if (entry.mangaId !in incomingChildIds) {
                        database.mergedQueries.deleteEntryById(entry.id)
                    }
                }
            }
            resolved.forEach { (childId, incoming) ->
                val dbEntry = dbByChildId[childId]
                if (dbEntry == null) {
                    database.mergedQueries.insertEntry(
                        mergeId = mergeMangaId,
                        mangaId = childId,
                        fetchChapterUpdates = incoming.fetchChapterUpdates,
                        downloadChapters = incoming.downloadChapters,
                        chapterPriority = incoming.chapterPriority.toLong(),
                    )
                } else if (
                    !addOnly &&
                    (
                        dbEntry.fetchChapterUpdates != incoming.fetchChapterUpdates ||
                            dbEntry.downloadChapters != incoming.downloadChapters ||
                            dbEntry.chapterPriority != incoming.chapterPriority
                        )
                ) {
                    database.mergedQueries.updateEntryFromRestore(
                        id = dbEntry.id,
                        fetchChapterUpdates = incoming.fetchChapterUpdates,
                        downloadChapters = incoming.downloadChapters,
                        chapterPriority = incoming.chapterPriority.toLong(),
                        mangaId = childId,
                    )
                }
            }
        }
    }
    // KUN <--

    // KNS -->
    /**
     * The chapter blacklist, scanlator priority / range rules and Smart Merge override follow the same
     * rules as the excluded scanlators above. A sync payload that wins the version comparison is
     * authoritative (it may also clear values; an empty one only when strictly newer); a losing one
     * cannot remove local entries, so its blacklist is only added. An ordinary backup only adds
     * blacklist entries and replaces the scanlator rules it explicitly carries.
     */
    private suspend fun restoreForkState(
        manga: Manga,
        state: RestoredForkState,
        isSync: Boolean,
        incomingIsNewerOrEqual: Boolean,
        incomingIsStrictlyNewer: Boolean,
    ) {
        val local = mangaRepository.getMangaById(manga.id)
        val incomingEmpty = state.blacklistedChapters.isEmpty() && state.scanlatorPriority.isEmpty() &&
            state.scanlatorRangeRules.isEmpty()
        val authoritative = isSync && state.scanlatorSettingsPresent &&
            (if (incomingEmpty) incomingIsStrictlyNewer else incomingIsNewerOrEqual)

        val blacklist = when {
            authoritative -> state.blacklistedChapters
            else -> (local.blacklistedChapters + state.blacklistedChapters).distinct()
        }
        val replaceRules = authoritative || (!isSync && state.scanlatorSettingsPresent)
        val priority = if (replaceRules) state.scanlatorPriority else local.scanlatorPriority
        val rangeRules = if (replaceRules) state.scanlatorRangeRules else local.scanlatorRangeRules
        val writeOverride = state.smartMergeOverridePresent && (!isSync || incomingIsNewerOrEqual)

        if (blacklist == local.blacklistedChapters && priority == local.scanlatorPriority &&
            rangeRules == local.scanlatorRangeRules &&
            (!writeOverride || state.smartMergeOverride == local.smartMergeOverride)
        ) {
            return
        }
        database.mangaQueries.updateForkState(
            blacklistedChapters = blacklist,
            scanlatorPriority = priority,
            scanlatorRangeRules = rangeRules,
            smartMergeOverrideSet = writeOverride,
            smartMergeOverride = state.smartMergeOverride,
            mangaId = manga.id,
        )
    }
    // KNS <--

    private suspend fun restoreExcludedScanlators(
        manga: Manga,
        excludedScanlators: List<String>,
        // KUN -->
        isSync: Boolean,
        incomingIsNewerOrEqual: Boolean,
        incomingIsStrictlyNewer: Boolean,
        // KUN <--
    ) {
        val existingExcludedScanlators = database.excluded_scanlatorQueries
            .getExcludedScanlatorsByMangaId(manga.id)
            .awaitAsList()
            .toSet()
        val incoming = excludedScanlators.toSet()

        // KUN -->
        if (isSync) {
            // A sync response carries the complete filter for the manga, including an empty list
            // when the user cleared it. Replacing is only safe when the incoming payload wins the
            // version comparison: a stale payload must never delete a filter the user edited
            // locally. Restores write the child table directly (no manga version bump), so this
            // cannot echo back as a new local edit.
            when {
                // Non-empty payload that wins (or ties): make the local set match it.
                incoming.isNotEmpty() && incomingIsNewerOrEqual -> {
                    replaceExcludedScanlators(manga, existingExcludedScanlators, incoming)
                    return
                }
                // Empty payload only clears when it is strictly newer (explicitly filtered out on
                // another device); a tie may merely mean both devices already saw the same state.
                incoming.isEmpty() && incomingIsStrictlyNewer -> {
                    replaceExcludedScanlators(manga, existingExcludedScanlators, emptySet())
                    return
                }
                // Otherwise this payload lost the comparison: fall through to the add-only union
                // below, which never deletes local exclusions.
            }
        }
        // KUN <--

        // KUN -->
        // An ordinary backup is not authoritative for removals: older backups predate exclusions
        // the user added later, so only add (never delete) local exclusions.
        // KUN <--
        if (incoming.isEmpty()) return
        val toInsert = incoming.subtract(existingExcludedScanlators)
        if (toInsert.isNotEmpty()) {
            database.transaction {
                toInsert.forEach {
                    database.excluded_scanlatorQueries.insert(manga.id, it)
                }
            }
        }
    }

    // KUN -->
    private suspend fun replaceExcludedScanlators(
        manga: Manga,
        existingExcludedScanlators: Set<String>,
        incoming: Set<String>,
    ) {
        val toAdd = incoming.subtract(existingExcludedScanlators)
        val toRemove = existingExcludedScanlators.subtract(incoming)
        if (toAdd.isEmpty() && toRemove.isEmpty()) return
        database.transaction {
            if (toRemove.isNotEmpty()) {
                database.excluded_scanlatorQueries.remove(manga.id, toRemove)
            }
            toAdd.forEach {
                database.excluded_scanlatorQueries.insert(manga.id, it)
            }
        }
    }
    // KUN <--

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

    // KUN -->
    /**
     * Field-by-field merge for a legacy/unmarked payload. An explicit write replaces the whole
     * override set, so writing the incoming value directly would drop every local override the
     * payload omitted (it may have been produced by a client that never synced custom info). A null
     * incoming field keeps the local one, including the genre list.
     */
    private suspend fun mergeEditedInfo(stored: MangaUserInfoUpdate?, incoming: MangaUserInfoUpdate) {
        // An all-null value from an unmarked payload says nothing: it must not clear anything.
        if (!incoming.hasOverrides()) return
        val merged = MangaUserInfoUpdate(
            id = incoming.id,
            title = incoming.title ?: stored?.title,
            author = incoming.author ?: stored?.author,
            artist = incoming.artist ?: stored?.artist,
            thumbnailUrl = incoming.thumbnailUrl ?: stored?.thumbnailUrl,
            description = incoming.description ?: stored?.description,
            genre = incoming.genre ?: stored?.genre,
            status = incoming.status ?: stored?.status,
        )
        // Skip a merge that reproduces the stored value.
        if (merged.normalized() != stored.normalized()) {
            mangaRepository.updateUserInfoAll(listOf(merged.normalizedOrCleared()))
        }
    }

    /** Whether the value overrides anything at all. */
    private fun MangaUserInfoUpdate.hasOverrides(): Boolean =
        title != null ||
            author != null ||
            artist != null ||
            thumbnailUrl != null ||
            description != null ||
            genre != null ||
            status != null

    /**
     * Value identity for an override write: a blank title and a zero status are not overrides, and a
     * value with no override at all is the same as no stored value.
     */
    private fun MangaUserInfoUpdate?.normalized(): MangaUserInfoUpdate? {
        val info = this ?: return null
        val normalized = info.copy(
            title = info.title?.takeUnless { it.isBlank() },
            status = info.status?.takeUnless { it == 0L },
        )
        return normalized.takeIf { it.hasOverrides() }
    }

    /**
     * The value written back: the normalized override set, or the all-null value that clears every
     * column when the payload asked to remove the overrides.
     */
    private fun MangaUserInfoUpdate.normalizedOrCleared(): MangaUserInfoUpdate =
        normalized() ?: copy(
            title = null,
            author = null,
            artist = null,
            thumbnailUrl = null,
            description = null,
            genre = null,
            status = null,
        )
    // KUN <--
    // SY <--
}

// SY -->
private const val SYNCING = 1L
private const val NOT_SYNCING = 0L
// SY <--
