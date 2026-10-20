package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga

/**
 * Keeps only manga (and their chapters) modified at or after the last successful upload, which is all a
 * SyncYomi protocol v2 server needs. `lastModifiedAt` is in epoch seconds, set by the database triggers.
 *
 * A manga kept only because of a chapter change is sent with just its changed chapters; the server treats a
 * manga without chapters as "nothing to say about chapters", never "no chapters".
 *
 * Mutates the kept [BackupManga] instances (the backup models are plain classes); callers pass a freshly
 * built backup that is only used for the upload.
 *
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY#1648).
 */
internal fun changedSince(mangas: List<BackupManga>, since: Long): List<BackupManga> =
    mangas
        .filter { it.lastModifiedAt >= since || it.chapters.any { chapter -> chapter.lastModifiedAt >= since } }
        .onEach { manga -> manga.chapters = manga.chapters.filter { it.lastModifiedAt >= since } }

/** [BackupCategory] has identity equality, and local ids and timestamps differ between devices. */
internal fun categoriesDiffer(local: List<BackupCategory>, remote: List<BackupCategory>): Boolean =
    local.map { it.syncIdentity() }.toSet() != remote.map { it.syncIdentity() }.toSet()

private fun BackupCategory.syncIdentity() = listOf(uid, name, order, flags, version)

internal fun extensionStoresDiffer(local: List<BackupExtensionStore>, remote: List<BackupExtensionStore>): Boolean =
    local.map { it.syncIdentity() }.toSet() != remote.map { it.syncIdentity() }.toSet()

private fun BackupExtensionStore.syncIdentity() =
    listOf(indexUrl, name, badgeLabel, signingKey, contactWebsite, contactDiscord, isLegacy, extensionListUrl)

/**
 * The key the SyncYomi v2 server stores a chapter under. It must match the server's byte for byte
 * (`<source id>|<manga url>` + 0x1f + `<chapter url>`, see `backup.ChapterKey`), or the server
 * tombstones nothing.
 *
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY#1658).
 */
internal fun chapterTombstoneKey(sourceId: Long, mangaUrl: String, chapterUrl: String): String =
    "$sourceId|$mangaUrl\u001f$chapterUrl"

// KUN -->
/**
 * Chapters missing from a complete remote chapter list. A SyncYomi v2 response carries each
 * manga's complete (remaining) chapter list, so a local chapter absent from it was tombstoned on
 * the server by another device.
 */
internal fun missingRemoteChapterUrls(
    localUrls: Collection<String>,
    remoteUrls: Collection<String>,
): Set<String> = localUrls.filterNotTo(mutableSetOf()) { it in remoteUrls }
// KUN <--

// KUN -->
/**
 * Merges local and remote categories for a sync restore.
 *
 * In SyncYomi protocol v2, [remote] is a delta of changed/new categories (often empty when categories
 * did not change on the remote). An empty delta must preserve local categories rather than wiping them.
 * When remote has updates, they are merged by UID or normalized name, with any new remote categories appended.
 * Categories are deduplicated by normalized name to prevent cross-device UID drift from creating duplicate rows.
 */
internal fun mergeSyncCategories(
    local: List<BackupCategory>,
    remote: List<BackupCategory>,
    protocolV2: Boolean,
): List<BackupCategory> {
    return if (protocolV2) {
        if (remote.isEmpty()) {
            local
        } else {
            val remoteByUid = remote.filter { it.uid != 0L }.associateBy { it.uid }
            val remoteByName = remote.associateBy { it.name.trim().lowercase() }
            val updated = local.map { loc ->
                (if (loc.uid != 0L) remoteByUid[loc.uid] else null)
                    ?: remoteByName[loc.name.trim().lowercase()]
                    ?: loc
            }
            val existingUids = updated.map { it.uid }.toSet()
            val existingNames = updated.map { it.name.trim().lowercase() }.toSet()
            val brandNew = remote.filter {
                (it.uid == 0L || it.uid !in existingUids) && it.name.trim().lowercase() !in existingNames
            }
            (updated + brandNew)
                .distinctBy { it.name.trim().lowercase() }
                .sortedBy { it.order }
        }
    } else {
        remote.ifEmpty { local }
    }
}
// KUN <--
