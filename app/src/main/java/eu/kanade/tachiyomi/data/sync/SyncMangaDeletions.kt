package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupManga
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.sync.repository.SyncRepository

/**
 * Only a full v2 snapshot may imply deletions: a delta omits unchanged entries, so absence there
 * means nothing. Library sync must be on, or the response is not a statement about the library.
 */
internal fun canApplySnapshotDeletions(
    full: Boolean,
    protocolV2: Boolean,
    libraryEntries: Boolean,
): Boolean = full && protocolV2 && libraryEntries

/**
 * Local library manga (favourites) that the server did not return in a full snapshot.
 *
 * [remoteManga] must be the whole snapshot, including `favorite = false` entries: a row the
 * server still knows as a non-favourite is NOT deleted. An empty snapshot returns nothing
 * (fail closed) because treating "server returned no manga" as "delete the whole library" is
 * the destructive case; this guard is deliberate and tested.
 *
 * [uploadedManga] is the local upload that this snapshot answers. A candidate must also be in it:
 * a favourite added (or first favourited) after the upload was built, or one the backup creator
 * omitted, was never sent to the server, so the server cannot have tombstoned it. Without this
 * check, a race between the upload and the post-round-trip database read would hard-delete a
 * manga the user just added.
 */
internal fun mangaIdsMissingFromFullSnapshot(
    localManga: List<Manga>,
    remoteManga: List<BackupManga>,
    uploadedManga: List<BackupManga>,
    fullSnapshot: Boolean,
): List<Long> {
    if (!fullSnapshot || remoteManga.isEmpty()) return emptyList()
    val remoteKeys = remoteManga.mapTo(HashSet()) { it.source to it.url }
    val uploadedKeys = uploadedManga.mapTo(HashSet()) { it.source to it.url }
    return localManga
        .filter { it.favorite && (it.source to it.url) in uploadedKeys && (it.source to it.url) !in remoteKeys }
        .map { it.id }
}

/**
 * Whether a response's history sections may be read as complete, so an entry missing from a manga's
 * list was removed elsewhere. Only a full v2 response can say that: a delta omits the entries that
 * did not change.
 *
 * Both sections that feed the comparison have to be enabled. History sync must be on, or this device
 * never uploaded the rows the server's list would be compared against; library sync must be on too,
 * because a disabled library uploads no manga at all, so the response's history sections answer for
 * rows this device never sent.
 */
internal fun canApplySnapshotHistoryPrune(
    full: Boolean,
    protocolV2: Boolean,
    libraryEntries: Boolean,
    historyEntries: Boolean,
): Boolean = full && protocolV2 && libraryEntries && historyEntries

/**
 * Whether this run's upload of a manga outranked the server's copy of it, using the same
 * version-first rule the merge and the restore decision use: a higher version wins and an equal
 * version is broken by `lastModifiedAt`, with the upload keeping the tie.
 *
 * Only the history prune consults this. Since SyncYomi 1.7.3 a history entry is its own item, so the
 * manga's version no longer governs history at all; on 1.3-1.7.2 history still rides inside the manga
 * payload and the version merge can discard the uploaded section. The client cannot tell the two
 * apart, so this is the safety gate for the old shape: when the upload lost, the response's list is
 * the server's own copy and says nothing about local reads. It only ever skips a prune.
 */
internal fun uploadedMangaWonMerge(uploaded: BackupManga, remote: BackupManga): Boolean =
    uploaded.version > remote.version ||
        (uploaded.version == remote.version && uploaded.lastModifiedAt >= remote.lastModifiedAt)

/**
 * Local history rows of [remoteManga] that the full snapshot no longer lists.
 *
 * Only a manga the response carries is considered: its absence from the response as a whole says
 * nothing about history, and the manga absence check owns that case. Only a manga this run uploaded
 * counts as well: a manga read without being added to the library is never uploaded, so the server's
 * list for it is not a statement about the local reads either, and one whose upload lost the version
 * merge answers for the server's copy rather than ours ([uploadedMangaWonMerge]). An entry of such a
 * manga whose chapter url is missing from its history list was removed by another device.
 *
 * A row read at or after [syncStart] (epoch seconds) survives: it was read during this sync, may be
 * missing from the upload the server answered, and is therefore not a deletion. Rows without a read
 * date are already removed locally and are not candidates again.
 */
internal suspend fun historyIdsMissingFromFullSnapshot(
    full: Boolean,
    protocolV2: Boolean,
    libraryEntries: Boolean,
    historySyncEnabled: Boolean,
    syncStart: Long,
    remoteManga: List<BackupManga>,
    uploadedManga: List<BackupManga>,
    syncRepository: SyncRepository,
    historyRepository: HistoryRepository,
): List<Long> {
    if (!canApplySnapshotHistoryPrune(full, protocolV2, libraryEntries, historySyncEnabled)) return emptyList()
    if (remoteManga.isEmpty()) return emptyList()
    val remoteByKey = remoteManga.associateBy { it.source to it.url }
    val uploadedByKey = uploadedManga.associateBy { it.source to it.url }
    // `read_at` is stored in epoch millis, the sync epoch in seconds.
    val syncStartMillis = syncStart * 1000

    val remoteUrlsByMangaId = syncRepository.getAllManga().mapNotNull { manga ->
        val key = manga.source to manga.url
        val remote = remoteByKey[key] ?: return@mapNotNull null
        val uploaded = uploadedByKey[key] ?: return@mapNotNull null
        if (!uploadedMangaWonMerge(uploaded, remote)) return@mapNotNull null
        manga.id to remote.history.mapTo(HashSet()) { it.url }
    }.toMap()
    if (remoteUrlsByMangaId.isEmpty()) return emptyList()

    return historyRepository.getHistoryChapterEntriesByMangaIds(remoteUrlsByMangaId.keys.toList())
        .mapNotNull { entry ->
            val readAt = entry.readAt ?: return@mapNotNull null
            if (readAt.toEpochMilliseconds() >= syncStartMillis) return@mapNotNull null
            val remoteUrls = remoteUrlsByMangaId[entry.mangaId] ?: return@mapNotNull null
            entry.historyId.takeIf { entry.chapterUrl !in remoteUrls }
        }
        .distinct()
}
