package eu.kanade.tachiyomi.data.sync

import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

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
 * Whether this upload has to carry the complete library: either the user asked for it once
 * ([forceFull]) or the service needs it ([serviceNeedsFullSync], i.e. first sync, server request,
 * or the daily safety net). Services without delta support always report `true`.
 */
internal fun shouldUploadFullLibrary(forceFull: Boolean, serviceNeedsFullSync: Boolean): Boolean =
    forceFull || serviceNeedsFullSync

/**
 * A force push sends every manga payload, so a disabled manga-payload section would otherwise
 * replace the server's copy with empty values. Copy the server's fields for the disabled sections
 * into the upload; enabled sections keep the local values.
 */
internal fun preserveForcePushSections(
    localManga: List<BackupManga>,
    remoteManga: List<BackupManga>,
    settings: SyncSettings,
) {
    val remoteByKey = remoteManga.associateBy { it.source to it.url }
    localManga.forEach { local ->
        val remote = remoteByKey[local.source to local.url] ?: return@forEach
        if (!settings.history) local.history = remote.history
        if (!settings.tracking) local.tracking = remote.tracking
        if (!settings.categories) local.categories = remote.categories
        if (!settings.customInfo) {
            local.customTitle = remote.customTitle
            local.customArtist = remote.customArtist
            local.customAuthor = remote.customAuthor
            local.customThumbnailUrl = remote.customThumbnailUrl
            local.customDescription = remote.customDescription
            local.customGenre = remote.customGenre
            local.customStatus = remote.customStatus
            // KUN -->
            // The marker travels with the values: the restore half must see the same "included"
            // state the server holds, not this device's disabled-section default.
            local.customInfoIncluded = remote.customInfoIncluded
            // KUN <--
        }
    }
}

/**
 * Raises every manga, its chapters and (when [bumpCategories]) the categories to the forced sync
 * versions in the upload. Only the payload is changed: `SyncManager` bumps the database once the
 * server accepts it, so an interrupted run cannot leave epoch versions behind for an ordinary sync
 * to upload with empty disabled sections.
 *
 * Categories are left alone when their sync is disabled: the upload carries the server's copy
 * instead, and bumping it would make that copy win over the server's own version.
 */
internal fun applyForcePushVersions(
    backup: Backup,
    forceVersion: Long,
    forceModifiedAt: Long,
    forceFavoriteAt: Long,
    bumpCategories: Boolean,
) {
    backup.backupManga.forEach { manga ->
        manga.version = maxOf(manga.version, forceVersion)
        manga.lastModifiedAt = maxOf(manga.lastModifiedAt, forceModifiedAt)
        manga.favoriteModifiedAt = maxOf(manga.favoriteModifiedAt ?: 0L, forceFavoriteAt)
        manga.chapters.forEach { chapter ->
            chapter.version = maxOf(chapter.version, forceVersion)
            chapter.lastModifiedAt = maxOf(chapter.lastModifiedAt, forceModifiedAt)
        }
    }
    if (bumpCategories) {
        backup.backupCategories.forEach { category ->
            category.version = maxOf(category.version, forceVersion)
            category.lastModifiedAt = maxOf(category.lastModifiedAt, forceModifiedAt)
        }
    }
}

/**
 * Removes the manga-payload sections the user disabled from the restore half of a force push.
 *
 * The server's values for those sections were copied into the upload so the forced payloads would
 * not blank them, but they must not be written back locally either: `RestoreOptions` has no flag
 * for history, tracking or custom info, so the restorer would apply them anyway. Categories are
 * already gated by `RestoreOptions.categories`.
 */
internal fun clearDisabledSectionsForRestore(backup: Backup, settings: SyncSettings) {
    backup.backupManga.forEach { manga ->
        if (!settings.history) manga.history = emptyList()
        if (!settings.tracking) manga.tracking = emptyList()
        if (!settings.customInfo) {
            manga.customTitle = null
            manga.customArtist = null
            manga.customAuthor = null
            manga.customThumbnailUrl = null
            manga.customDescription = null
            manga.customGenre = null
            manga.customStatus = 0
            // KUN -->
            // A cleared section must not look like an explicit "the user removed every override"
            // either, or a future restore-flag regression would clear the local file entry.
            manga.customInfoIncluded = false
            // KUN <--
        }
    }
}

/**
 * Whether a remote payload differs in the fields that feed the sync version trigger.
 *
 * `chapterFlags` and `notes` are newer payload fields: an older client omits them, so they decode
 * as `0`/empty. Treating those as a change would clear the local value just because the field was
 * never sent, so a default only counts when the remote payload is genuinely newer. Every non-default
 * value that differs is a real change.
 */
internal fun mangaPayloadFieldsDiffer(local: Manga, remote: BackupManga): Boolean {
    // The legacy `viewer` field is mode-only, so only compare it when the payload carried `viewer_flags`.
    val remoteViewerFlags = remote.viewer_flags
    if (remoteViewerFlags != null && remoteViewerFlags.toLong() != local.viewerFlags) {
        return true
    }

    val remoteIsNewer = remote.version > local.version ||
        (remote.version == local.version && remote.lastModifiedAt > local.lastModifiedAt)
    val chapterFlagsDiffer = remote.chapterFlags.toLong() != local.chapterFlags &&
        (remote.chapterFlags != 0 || remoteIsNewer)
    val notesDiffer = remote.notes != local.notes &&
        (remote.notes.isNotEmpty() || remoteIsNewer)
    return chapterFlagsDiffer || notesDiffer
}

/**
 * A pending force push can only resume on SyncYomi: the raised versions mean nothing to Drive,
 * WebDAV or a v1 SyncYomi server, which would treat them as ordinary local edits.
 */
internal fun resumesPendingForcePush(pendingForcePush: Boolean, isSyncYomi: Boolean): Boolean =
    pendingForcePush && isSyncYomi

/**
 * Whether a force push that needs the server snapshot must stop instead of uploading. An
 * established server (a non-zero cursor or a recorded full sync) has sections to preserve, so a
 * 404/empty snapshot is a hard failure; a brand-new server has nothing to lose.
 */
internal fun forcePushMissingSnapshotIsFatal(
    needsSnapshot: Boolean,
    snapshotAvailable: Boolean,
    hadRemoteSync: Boolean,
): Boolean = needsSnapshot && !snapshotAvailable && hadRemoteSync

/**
 * Whether a failed force-push upload will never succeed by retrying the same request, so keeping
 * the resume marker would retry forever.
 *
 * Client errors are permanent (413 when the full upload exceeds the body limit, 400/401/403/422)
 * except a request timeout (408) and rate limiting (429), which are transient. Server errors,
 * timeouts and failures without a status are transient because the server may have committed.
 */
internal fun isPermanentForcePushFailure(status: Int?, permanent: Boolean): Boolean =
    permanent || (status != null && status in 400..499 && status != 408 && status != 429)
// KUN <--

// KNS -->
/** A category's name among its siblings; 0 = root (or a payload that does not carry the parent). */
internal fun BackupCategory.siblingNameKey(): String = "$parentUid\u0000${name.trim().lowercase()}"
// KNS <--

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
            // KNS -->
            // Names are only unique among siblings: `Shounen / Reading` and `Josei / Reading` must not
            // collapse into one category, so the name fallback (which also converges same-named roots
            // created on two devices) is scoped to the parent.
            val remoteByName = remote.associateBy { it.siblingNameKey() }
            val updated = local.map { loc ->
                (if (loc.uid != 0L) remoteByUid[loc.uid] else null)
                    ?: remoteByName[loc.siblingNameKey()]
                    ?: loc
            }
            val existingUids = updated.map { it.uid }.toSet()
            val existingNames = updated.map { it.siblingNameKey() }.toSet()
            val brandNew = remote.filter {
                (it.uid == 0L || it.uid !in existingUids) && it.siblingNameKey() !in existingNames
            }
            (updated + brandNew)
                .distinctBy { it.siblingNameKey() }
                .sortedBy { it.order }
            // KNS <--
        }
    } else {
        remote.ifEmpty { local }
    }
}
// KUN <--

// SY -->
// Ported from TachiyomiSY (jobobby04/TachiyomiSY#1679), keeping this fork's checks: the
// `completeChapterList` handling and the read/bookmark/page comparison of the chapters.
// KUN -->
internal class LocalMangaState(
    val manga: Manga,
    val chapters: List<Chapter>,
    val categoryOrders: Set<Long>,
    val lastReadByChapterUrl: Map<String, Long>,
)

internal fun needsRestore(
    local: LocalMangaState,
    remote: BackupManga,
    // A v2 response carries the manga's complete chapter list, so a local chapter that is missing
    // from it was deleted on another device.
    completeChapterList: Boolean = true,
): Boolean =
    local.manga.favorite != remote.favorite ||
        // These fields feed the version trigger, so an equal-version response that still differs
        // must be restored instead of being skipped as "up-to-date".
        mangaPayloadFieldsDiffer(local.manga, remote) ||
        chaptersDiffer(local.chapters, remote.chapters, completeChapterList) ||
        local.manga.version != remote.version ||
        // A v2 delta leaves the categories out when they did not change; an absent list is not a change.
        (remote.categories.isNotEmpty() && local.categoryOrders != remote.categories.toSet()) ||
        // A peer that read further must win even when nothing else about the entry moved.
        remote.history.any { it.lastRead > (local.lastReadByChapterUrl[it.url] ?: 0L) }

private fun chaptersDiffer(
    local: List<Chapter>,
    remote: List<BackupChapter>,
    completeChapterList: Boolean,
): Boolean {
    if (!completeChapterList && remote.isEmpty()) {
        return false
    }
    val localChapterMap = local.associateBy { it.url }
    val remoteChapterUrls = remote.mapTo(mutableSetOf()) { it.url }

    if (completeChapterList && missingRemoteChapterUrls(localChapterMap.keys, remoteChapterUrls).isNotEmpty()) {
        return true
    }

    for (remoteChapter in remote) {
        val localChapter = localChapterMap[remoteChapter.url]

        // If a matching remote chapter doesn't exist locally, or their states/versions differ
        if (localChapter == null ||
            localChapter.version != remoteChapter.version ||
            localChapter.read != remoteChapter.read ||
            localChapter.bookmark != remoteChapter.bookmark ||
            localChapter.lastPageRead.toLong() != remoteChapter.lastPageRead
        ) {
            return true
        }
    }

    return false
}
// KUN <--
// SY <--
