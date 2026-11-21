package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupManga
import tachiyomi.domain.manga.model.Manga

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
