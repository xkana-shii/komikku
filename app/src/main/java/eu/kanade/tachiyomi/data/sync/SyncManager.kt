package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.net.Uri
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreWorker
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.sync.service.BackupRequestBody
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.data.sync.service.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncResult
import eu.kanade.tachiyomi.data.sync.service.SyncYomiSyncService
import eu.kanade.tachiyomi.data.sync.service.WebDavSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okio.buffer
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.PruneCategoryReferences
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SavedSearchRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.sync.repository.SyncRepository
import tachiyomi.i18n.kmk.KMR
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.system.measureTimeMillis
import kotlin.time.Clock

/**
 * A manager to handle synchronization tasks in the app, such as updating
 * sync preferences and performing synchronization with a remote server.
 *
 * @property context The application context.
 */
class SyncManager(
    private val context: Context,
    private val syncRepository: SyncRepository,
    private val chapterRepository: ChapterRepository,
    private val historyRepository: HistoryRepository,
    private val categoryRepository: CategoryRepository,
    private val syncPreferences: SyncPreferences,
    private val getCategories: GetCategories,
    // KUN -->
    private val pruneCategoryReferences: PruneCategoryReferences,
    /** Owns the files and queued chapters of a manga row removed by a sync. */
    private val downloadManager: DownloadManager,
    private val sourceManager: SourceManager,
    /** Selected by a `saved_search` tombstone: one local row per name and source. */
    private val savedSearchRepository: SavedSearchRepository,
    // KUN <--
    private val backupCreatorFactory: BackupCreator.Factory,
    private val notifier: SyncNotifier,
    restorer: MangaRestorer,
    private var json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    private val backupCreator: BackupCreator = backupCreatorFactory.create(isAutoBackup = false)
    private val mangaRestorer: MangaRestorer = restorer.also { it.isSync = true }

    enum class SyncService(val value: Int) {
        NONE(0),
        SYNCYOMI(1),
        GOOGLE_DRIVE(2),
        // KMK -->
        WEB_DAV(3),
        // KMK <--
        ;

        companion object {
            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
        }
    }

    /**
     * Syncs data with a sync service.
     *
     * This function retrieves local data (favorites, manga, extensions, and categories)
     * from the database using the BackupManager, then synchronizes the data with a sync service.
     *
     * KUN -->
     * @param forceFull one-shot request to upload the whole library even when the service would accept
     * a delta. No-op for services that always upload everything (Google Drive, WebDAV, v1).
     * @param forcePush one-shot SyncYomi v2 repair: raise this device's sync versions so its library,
     * chapters and read state win the version-first merge. It requires library entries and chapters.
     * Disabled manga-payload sections (history, tracking, custom info) and categories are copied from
     * the server snapshot into the upload so the forced payloads do not blank them. The raised
     * versions live only in the upload until the server accepts it; an interrupted run resumes on a
     * later sync through [SyncPreferences.forcePushPending].
     * KUN <--
     */
    suspend fun syncData(
        // KUN -->
        forceFull: Boolean = false,
        forcePush: Boolean = false,
        // KUN <--
    ) {
        // SY -->
        // Epoch seconds. Everything modified before this instant is in this upload; the next delta starts here.
        val syncStart = Clock.System.now().epochSeconds
        // SY <--

        // Reset isSyncing in case it was left over or failed syncing during restore.
        syncRepository.resetSyncing()

        // KUN -->
        // A force push interrupted after the server accepted it left this flag set. Resume it, so a
        // later ordinary sync cannot upload the bumped rows with empty disabled sections.
        val pendingForcePush = syncPreferences.forcePushPending.get()
        // KUN <--

        val syncOptions = syncPreferences.getSyncSettings()

        val backupOptions = BackupOptions(
            libraryEntries = syncOptions.libraryEntries,
            categories = syncOptions.categories,
            chapters = syncOptions.chapters,
            tracking = syncOptions.tracking,
            history = syncOptions.history,
            extensionStores = syncOptions.extensionStores,
            appSettings = syncOptions.appSettings,
            sourceSettings = syncOptions.sourceSettings,
            privateSettings = syncOptions.privateSettings,

            // SY -->
            customInfo = syncOptions.customInfo,
            readEntries = syncOptions.readEntries,
            // KMK -->
            savedSearchesFeeds = syncOptions.savedSearchesFeeds,
            // KMK <--
            // SY <--
        )

        // Handle sync based on the selected service
        val syncService = when (val syncService = SyncService.fromInt(syncPreferences.syncService.get())) {
            SyncService.SYNCYOMI -> {
                SyncYomiSyncService(
                    context,
                    json,
                    syncPreferences,
                    notifier,
                    // KUN -->
                    historyRepository = historyRepository,
                    // KUN <--
                )
            }

            SyncService.GOOGLE_DRIVE -> {
                GoogleDriveSyncService(context, json, syncPreferences)
            }

            // KMK -->
            SyncService.WEB_DAV -> {
                WebDavSyncService(context, json, syncPreferences, notifier)
            }
            // KMK <--

            else -> {
                logcat(LogPriority.ERROR) { "Invalid sync service type: $syncService" }
                null
            }
        }

        // SY -->
        // Whether to upload the whole library or only what changed since the last successful sync (SyncYomi v2).
        // KUN -->
        // A force push is a SyncYomi v2-only repair: Drive/WebDAV always upload everything and v1
        // merges locally, so there is nothing to make authoritative there. Probe v2 even though
        // force push already implies a full upload; `needsFullSync()` alone would short-circuit.
        //
        // A pending force push is only meaningful for SyncYomi. On another service it is ignored for
        // this run but kept: a previous repair may already have written the database bump, and
        // clearing the marker would let an ordinary SyncYomi sync later upload those still-high
        // versions with empty disabled sections. Switching back to SyncYomi resumes the repair.
        val resumingForcePush = resumesPendingForcePush(
            pendingForcePush = pendingForcePush,
            isSyncYomi = syncService is SyncYomiSyncService,
        )
        if (resumingForcePush) {
            logcat(LogPriority.DEBUG) { "Resuming unfinished force push" }
        }
        val effectiveForcePush = forcePush || resumingForcePush
        var forcePushV2 = false
        val serviceNeedsFull = try {
            val service = syncService
            if (effectiveForcePush && service is SyncYomiSyncService) {
                forcePushV2 = service.supportsV2(forceProbe = true)
            }
            service?.needsFullSync() ?: true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to probe sync server" }
            notifier.showSyncError(e.message)
            return
        }
        val full = forcePushV2 || shouldUploadFullLibrary(forceFull, serviceNeedsFull)
        // KUN <--

        // KUN -->
        // A row queued for a SyncYomi tombstone that this configuration can no longer deliver would
        // stay blocked for the rest of its life: a restore payload skips it, "Clear database" keeps
        // it, and it pins its chapter against a remote deletion. Only a *verdict* may drop the queue
        // though, so this runs after the capability probe above: a host that has not answered yet
        // (a changed host, a fresh install) is UNKNOWN, and clearing there would discard a queue
        // that this run's probe may well turn out to deliver.
        val tombstoneDeliverability = syncPreferences.historyTombstoneDeliverability()
        if (dropsStrandedHistoryTombstones(tombstoneDeliverability)) {
            logcat(LogPriority.DEBUG) { "Dropping history tombstones no configured sync can deliver" }
            historyRepository.clearAllPendingHistoryDeletions()
        }
        // KUN <--

        // KUN -->
        // A force push is a SyncYomi v2-only repair, and it needs the library and chapters. Other
        // manga-payload sections may stay disabled: their server-side values are copied into the
        // upload below so the forced payloads do not replace them with empty data. An unresumable
        // pending force push keeps its flag and uploads nothing.
        if (effectiveForcePush && !forcePushV2) {
            notifier.showSyncError(context.stringResource(KMR.strings.pref_sync_force_push_requires_v2))
            return
        }
        if (forcePushV2 && !syncOptions.supportsForcePush) {
            notifier.showSyncError(context.stringResource(KMR.strings.pref_sync_force_push_requires_sections))
            return
        }

        // Shared by the in-memory upload bump and the database bump that server acceptance triggers.
        var forceVersion = 0L
        var forceModifiedAt = 0L
        // KUN <--

        val databaseManga = if (full) {
            getAllMangaThatNeedsSync()
        } else {
            getMangasModifiedSince(syncPreferences.lastPushedAt.get())
        }
        // SY <--

        logcat(LogPriority.DEBUG) { "Begin create backup (full=$full)" }
        val backupManga = backupCreator.backupMangas(databaseManga, backupOptions)
            .toList()
            // SY -->
            .let { if (full) it else changedSince(it, syncPreferences.lastPushedAt.get()) }
        // SY <--
        val backup = Backup(
            backupManga = backupManga,
            backupCategories = backupCreator.backupCategories(backupOptions),
            backupSources = backupCreator.backupSources(backupManga.mapTo(mutableSetOf()) { it.source }),
            // SY -->
            // KUN -->
            // Exclude derived flags, unmapped category IDs, device-specific hardware/layout settings,
            // and credentials to prevent cross-device ping-pong and corruption.
            backupPreferences = filterSyncableAppPreferences(
                backupCreator.backupAppPreferences(backupOptions),
                syncOptions.syncPinnedSources,
            ),
            // KUN <--
            // SY <--
            backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),
            backupExtensionStores = backupCreator.backupExtensionStores(backupOptions),

            // SY -->
            backupSavedSearches = backupCreator.backupSavedSearches(backupOptions),
            // SY <--

            // KMK -->
            backupFeeds = backupCreator.backupFeeds(backupOptions),
            // KMK <--
        )
        logcat(LogPriority.DEBUG) { "End create backup" }

        // KUN -->
        // Fetch the server's copy only once the upload snapshot is built, which keeps the window
        // between reading the server and sending the forced payload short. It is still fetched
        // before any DB mutation, so a failure can be retried without half a repair applied.
        var remoteSnapshot: Backup? = null
        if (forcePushV2 && syncOptions.forcePushNeedsSnapshot) {
            remoteSnapshot = try {
                (syncService as SyncYomiSyncService).fetchSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch snapshot for force push" }
                notifier.showSyncError(e.message)
                return
            }
        }

        // A 404 or empty snapshot means the server cannot tell us what to preserve. A brand-new
        // server has nothing to lose; an established one would be wiped by the forced payloads, so
        // fail closed instead of uploading.
        if (forcePushV2 && syncOptions.forcePushNeedsSnapshot && remoteSnapshot == null) {
            val hadRemoteSync = syncPreferences.syncCursor.get() != 0L ||
                syncPreferences.lastFullSync.get() != 0L
            if (forcePushMissingSnapshotIsFatal(
                    needsSnapshot = syncOptions.forcePushNeedsSnapshot,
                    snapshotAvailable = false,
                    hadRemoteSync = hadRemoteSync,
                )
            ) {
                logcat(LogPriority.ERROR) { "Force push aborted: server snapshot unavailable" }
                notifier.showSyncError(
                    context.stringResource(KMR.strings.pref_sync_force_push_snapshot_unavailable),
                )
                return
            }
        }

        // A forced payload replaces every manga field, so put the server's copy back for sections
        // the user deliberately does not sync.
        if (forcePushV2) {
            if (remoteSnapshot != null) {
                if (!syncOptions.categories) {
                    backup.backupCategories = remoteSnapshot.backupCategories
                }
                preserveForcePushSections(backup.backupManga, remoteSnapshot.backupManga, syncOptions)
            }

            // Raise the versions in the upload only. The database is bumped once the server accepts
            // it, so an interrupted run cannot leave epoch versions behind for an ordinary sync to
            // upload with empty disabled sections.
            forceVersion = Clock.System.now().toEpochMilliseconds()
            forceModifiedAt = (syncStart - 1).coerceAtLeast(0)
            applyForcePushVersions(
                backup = backup,
                forceVersion = forceVersion,
                forceModifiedAt = forceModifiedAt,
                forceFavoriteAt = Clock.System.now().epochSeconds,
                bumpCategories = syncOptions.categories,
            )
        }
        // KUN <--

        // Create the SyncData object
        val syncData = SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = backup,
        )

        // KUN -->
        // A category that is live locally again must not be tombstoned on the server. This has to
        // run before the sync sends its pending tombstones.
        syncPreferences.forgetDeletedCategory(getCategories.await().map { it.uid })
        // KUN <--

        // KUN -->
        // Persist the intent before the upload: if the process dies after the server accepted it,
        // the next run resumes the force push instead of uploading the raised versions as an
        // ordinary delta with empty disabled sections.
        if (forcePushV2) {
            syncPreferences.forcePushPending.set(true)
        }
        // KUN <--

        // SY -->
        val result: SyncResult = syncService?.doSync(syncData, full) ?: return
        val remoteBackup = result.backup
        // SY <--

        if (remoteBackup == null) {
            logcat(LogPriority.DEBUG) { "Skip restore due to network issues" }
            // KUN -->
            // Keep forcePushPending for transient failures: a null result can mean the server
            // accepted the merge and only the response was lost. A permanent rejection (the forced
            // payload can never fit, bad credentials) would otherwise retry forever, so drop it.
            if (forcePushV2) {
                if (isPermanentForcePushFailure(result.failureStatus, result.permanentFailure)) {
                    syncPreferences.forcePushPending.set(false)
                    logcat(LogPriority.WARN) {
                        "Force push permanently rejected (status=${result.failureStatus}); dropping resume state"
                    }
                } else {
                    logcat(LogPriority.DEBUG) {
                        "Force push not completed (status=${result.failureStatus}); keeping resume state"
                    }
                }
            }
            notifier.showSyncError("Sync failed to get remote data")
            // KUN <--
            return
        }

        // KUN -->
        // A v2 full response is the only one whose omissions mean deletion; `full` is the local
        // signal that the request asked for a full snapshot. A delta omits unchanged entries, so
        // its absences say nothing, and a non-library response is not about the library at all.
        //
        // This heuristic is the fallback for a server that predates the explicit tombstone list
        // (`Backup.deletions`): a delta that carries the list says what was deleted, and the list
        // wins, so the snapshot comparison does not even have to run.
        val mangaIdsToDelete = if (
            remoteBackup.deletions.isEmpty() &&
            canApplySnapshotDeletions(full, result.protocolV2, syncOptions.libraryEntries)
        ) {
            mangaIdsMissingFromFullSnapshot(
                localManga = syncRepository.getAllManga(),
                remoteManga = remoteBackup.backupManga,
                // The upload built before the round trip: a local favourite that was never sent
                // cannot have been tombstoned by the server, so it is never a deletion candidate.
                uploadedManga = backup.backupManga,
                fullSnapshot = true,
            )
        } else {
            emptyList()
        }

        // A full v2 response lists every live history entry of each manga it carries, so an entry
        // missing from that list was removed on a peer. The push in this mode is a full upload, so
        // the server holds every local row the comparison may delete. Gated and applied like the
        // manga deletions: a delta, a disabled library or history section, a manga this run never
        // uploaded, or a failed restore prunes nothing.
        val historyIdsToPrune = historyIdsMissingFromFullSnapshot(
            full = full,
            protocolV2 = result.protocolV2,
            libraryEntries = syncOptions.libraryEntries,
            historySyncEnabled = syncOptions.history,
            syncStart = syncStart,
            remoteManga = remoteBackup.backupManga,
            // The upload built before the round trip. Only a manga it carried, and whose copy the
            // server kept (the old per-manga shape can discard the uploaded history section), has a
            // history list on the server this device is answering for; a manga read without being
            // added to the library is never uploaded, so its local reads cannot be judged either.
            uploadedManga = backup.backupManga,
            syncRepository = syncRepository,
            historyRepository = historyRepository,
        )
        // KUN <--

        // KUN -->
        // The server accepted the forced upload. Persist the raised versions now, before the
        // restore branches, so the local rows keep outranking peers even if the restore fails.
        if (forcePushV2) {
            syncRepository.markForForcePush(
                version = forceVersion,
                lastModifiedAt = forceModifiedAt,
                bumpCategories = syncOptions.categories,
            )
            logcat(LogPriority.DEBUG) { "Force push: local sync versions raised above every peer" }
        }
        // KUN <--

        // SY -->
        if (
            skipRemoteResponse(
                ownBackup = remoteBackup === syncData.backup,
                protocolV2 = result.protocolV2,
                changed = result.changed,
                deletions = remoteBackup.deletions,
            )
        ) {
            // nothing changed
            logcat(LogPriority.DEBUG) { "Skip restore, nothing new on the remote" }
            // KUN -->
            commitRemoteState(result)
            markFullSyncCompleted(full, result)
            // This branch runs no restore, so it cannot be a partial restore: deletions are safe.
            applySnapshotMangaDeletions(mangaIdsToDelete)
            applySnapshotHistoryPrune(historyIdsToPrune)
            // KUN <--
            finishWithSuccess(syncStart, "Sync completed successfully", forcePushCompleted = forcePushV2)
            return
        }

        // KUN -->
        // Apply the delta's explicit tombstones before the restore reads the library, so a row the
        // server deleted elsewhere is never restored back out of this payload. The gate above never
        // skips a response that carries tombstones, so a delta whose only change is a deletion
        // reaches this line instead of being dropped as "nothing new on the remote".
        //
        // Parsed once: the deletions below apply the plan, and the category merge further down needs
        // its identities to keep a category this run deletes out of the restore.
        val tombstones = deletionPlan(remoteBackup.deletions)
        if (remoteBackup.deletions.isNotEmpty()) {
            val skippedKinds = applyDeletions(
                plan = tombstones,
                syncRepository = syncRepository,
                chapterRepository = chapterRepository,
                historyRepository = historyRepository,
                categoryRepository = categoryRepository,
                pruneCategoryReferences = pruneCategoryReferences,
                savedSearchRepository = savedSearchRepository,
                // Downloads and queued chapters go before the row that names them disappears.
                deleteManga = { deleteMangaRow(it) },
                // The same user settings the snapshot fallback honours: a section the user disabled
                // is not deleted on the server's word.
                librarySyncEnabled = syncOptions.libraryEntries,
                historySyncEnabled = syncOptions.history,
                chaptersEnabled = syncOptions.chapters,
                categoriesEnabled = syncOptions.categories,
                savedSearchesEnabled = syncOptions.savedSearchesFeeds,
            )
            logcat(LogPriority.DEBUG) {
                "Applied ${remoteBackup.deletions.size} remote tombstones" +
                    if (skippedKinds.isEmpty()) "" else "; not applying kinds ${skippedKinds.joinToString()}"
            }
        }
        // KUN <--

        // KUN -->
        // Legacy SY had checks here that aborted sync on empty remote manga list or skipped restore
        // on first sync. That broke first-sync restores and prevented syncing non-manga data.
        // KUN <--
        // SY <--

        // KUN -->
        // A v2 response carries each manga's complete chapter list, so a local chapter missing
        // from it was deleted elsewhere and counts as a change to restore.
        val completeChapterList = result.protocolV2 && syncOptions.chapters
        // KUN <--
        val (filteredFavorites, nonFavorites) = filterFavoritesAndNonFavorites(remoteBackup, completeChapterList)
        // KUN -->
        // Applying remote non-favorites unfavorites local entries. That is only meaningful when the
        // user syncs library entries; a full response lists every non-favorite manga, so running this
        // with library sync off would silently drop favorites.
        if (syncOptions.libraryEntries) {
            updateNonFavorites(nonFavorites)
        }
        // KUN <--
        // SY -->
        // the restore below may not run, so its reset of the sync flag cannot be relied on
        syncRepository.resetSyncing()
        // SY <--

        // KUN -->
        // A full v2 response lists every live category, so the server's copy replaces the local
        // union: a local category missing from it was deleted on a peer, and the restore must not
        // re-insert the row the prune below removes. A delta only carries the categories that
        // changed, so it keeps the union. An empty list fails closed, like the v1 fallback.
        val mergedBackupCategories = if (full && result.protocolV2 && syncOptions.categories) {
            remoteBackup.backupCategories.ifEmpty { backup.backupCategories }
        } else {
            mergeSyncCategories(
                // The upload snapshot was taken before the tombstones above ran, so a category this
                // run deleted is still in it. Drop it, or the merge hands it to the restore and the
                // restore re-inserts the row the tombstone just removed.
                local = if (syncOptions.categories) {
                    withoutTombstonedCategories(backup.backupCategories, tombstones.categories)
                } else {
                    backup.backupCategories
                },
                remote = remoteBackup.backupCategories,
                protocolV2 = result.protocolV2,
            )
        }
        // KUN <--

        val newSyncData = backup.copy(
            backupManga = filteredFavorites,
            // KUN -->
            backupCategories = mergedBackupCategories,
            // KUN <--
            // SY -->
            // a v2 delta only carries changed sources
            backupSources = remoteBackup.backupSources.ifEmpty { backup.backupSources },
            // SY <--
            // KUN -->
            // The server may still carry the device-local pinned-source key from when it was
            // synced; drop it when the set is not synced so the comparison below converges.
            backupPreferences = withoutDeviceLocalPinnedSources(
                remoteBackup.backupPreferences,
                syncOptions.syncPinnedSources,
            ),
            // KUN <--
            backupSourcePreferences = remoteBackup.backupSourcePreferences,
            backupExtensionStores = remoteBackup.backupExtensionStores,

            // SY -->
            // KUN -->
            // A saved search this run tombstoned is already deleted locally, so the payload the
            // restore applies must not name it again (the restorer would insert it back).
            backupSavedSearches = withoutTombstonedSavedSearches(
                remoteBackup.backupSavedSearches,
                tombstones.savedSearches,
            ),
            // KUN <--
            // SY <--

            // KMK -->
            // KUN -->
            // A feed that still names a tombstoned saved search would have the restorer recreate
            // both: the server keeps feeds in its opaque `extra` blob, which a `saved_search`
            // tombstone does not rewrite.
            backupFeeds = withoutFeedsOfTombstonedSavedSearches(
                remoteBackup.backupFeeds,
                tombstones.savedSearches,
            ),
            // KUN <--
            // KMK <--
        )

        // KUN -->
        // The upload carried the server's values for disabled sections so the forced payload would
        // not blank them. They must not be written back locally either. `RestoreOptions` now carries
        // per-section flags for history, tracking and custom info, so the restore already skips
        // them; clearing them here additionally keeps the cached payload honest for any path that
        // does not, instead of re-applying the server's copy as if it were a local change.
        //
        // `newSyncData.backupManga` holds the same instances as `remoteBackup.backupManga`, so this
        // mutates those too. It must run only after the upload; nothing below reads the cleared
        // fields (the change checks look at list sizes and the category/preference sections, and the
        // restore is what consumes this payload).
        if (forcePushV2) {
            clearDisabledSectionsForRestore(newSyncData, syncOptions)
        }
        // KUN <--

        // SY -->
        val hasMangaChanges = filteredFavorites.isNotEmpty()
        // KUN -->
        val hasCategoryChanges = if (result.protocolV2) {
            remoteBackup.backupCategories.isNotEmpty()
        } else {
            categoriesDiffer(backup.backupCategories, remoteBackup.backupCategories)
        }
        // KUN <--
        // a v2 delta leaves out the sections nothing changed in; an absent section is not a change
        val hasSourceChanges = newSyncData.backupSources != backup.backupSources
        // KUN -->
        // Compare the same filtered view the upload used, or a server that still carries the
        // device-local pinned key would look changed on every sync and force the restore path.
        val hasPreferenceChanges = hasSyncPreferenceChanges(
            remotePreferences = remoteBackup.backupPreferences,
            uploadedPreferences = backup.backupPreferences,
            syncPinnedSources = syncOptions.syncPinnedSources,
        )
        // KUN <--
        val hasSourcePreferenceChanges = remoteBackup.backupSourcePreferences.isNotEmpty() &&
            remoteBackup.backupSourcePreferences != backup.backupSourcePreferences
        val hasExtensionRepoChanges = remoteBackup.backupExtensionStores.isNotEmpty() &&
            extensionStoresDiffer(backup.backupExtensionStores, remoteBackup.backupExtensionStores)
        val hasSavedSearchChanges = remoteBackup.backupSavedSearches.isNotEmpty() &&
            remoteBackup.backupSavedSearches != backup.backupSavedSearches
        // KUN -->
        val hasFeedChanges = remoteBackup.backupFeeds.isNotEmpty() &&
            remoteBackup.backupFeeds != backup.backupFeeds
        // KUN <--

        if (!hasMangaChanges && !hasCategoryChanges && !hasSourceChanges &&
            !hasPreferenceChanges && !hasSourcePreferenceChanges &&
            !hasExtensionRepoChanges && !hasSavedSearchChanges &&
            // KUN -->
            !hasFeedChanges
            // KUN <--
        ) {
            // update the sync timestamp
            // KUN -->
            commitRemoteState(result)
            markFullSyncCompleted(full, result)
            // Nothing is restored here either, so full-snapshot deletions are safe.
            applySnapshotMangaDeletions(mangaIdsToDelete)
            applySnapshotHistoryPrune(historyIdsToPrune)
            // KUN <--
            finishWithSuccess(syncStart, "Sync completed successfully", forcePushCompleted = forcePushV2)
            return
        }
        // SY <--

        // SY -->
        // KUN -->
        // A full v2 snapshot lists every live category, so absence there means a peer deleted it; a
        // v2 delta only carries the categories that changed, so it can never prune. An empty list
        // fails closed either way: "the server has no categories" must not wipe the local ones.
        if (
            (full || !result.protocolV2) &&
            syncOptions.categories &&
            newSyncData.backupCategories.isNotEmpty()
        ) {
            val deletedCategories = pruneCategoriesMissingFromSnapshot(
                localCategories = getCategories.await(),
                snapshot = newSyncData.backupCategories,
                uploaded = backup.backupCategories,
                // Only a full v2 response is a complete list, and only a category this run uploaded
                // can have been deleted by the server: one created after the upload was built was
                // never sent, so its absence is a race between the upload and this read.
                requireUploaded = result.protocolV2 && full,
                categoryRepository = categoryRepository,
                pruneCategoryReferences = pruneCategoryReferences,
            )
            // remembered for the next v2 request, which tombstones them on the server
            deletedCategories.forEach { syncPreferences.rememberDeletedCategory(it.uid) }
        }
        // KUN <--
        // SY <--

        // SY -->
        if (newSyncData.hasNothingToRestore()) {
            // the category deletions above were the whole change; an empty backup cannot be decoded
            // KUN -->
            commitRemoteState(result)
            markFullSyncCompleted(full, result)
            // Nothing is restored here either, so full-snapshot deletions are safe.
            applySnapshotMangaDeletions(mangaIdsToDelete)
            applySnapshotHistoryPrune(historyIdsToPrune)
            // KUN <--
            finishWithSuccess(syncStart, "Sync completed successfully", forcePushCompleted = forcePushV2)
            return
        }
        // SY <--

        val backupUri = writeSyncDataToCache(context, newSyncData)
        logcat(LogPriority.DEBUG) { "Got Backup Uri: $backupUri" }
        if (backupUri != null) {
            // SY -->
            // Await the apply so a failed restore fails the sync instead of
            // silently reporting success with nothing written.
            val applied = BackupRestoreWorker.startAndAwaitSuccess(
                context,
                backupUri,
                sync = true,
                // KUN -->
                // Honour the user's "choose what to sync" flags instead of forcing everything on.
                // Pinned sources are only replaced when the server merged the upload for us
                // (SyncYomi v2); see [syncRestoreOptions].
                // KUN <--
                options = syncRestoreOptions(syncOptions, result.protocolV2),
                // KUN -->
                // Drop local chapters that the v2 server no longer returns (tombstoned there).
                // Chapters added after this snapshot survive; the restorer also skips everything
                // when library entries are disabled, before it ever reaches this call.
                removeMissingChaptersBefore = syncStart.takeIf { completeChapterList },
                // KUN <--
            )
            // SY <--
            if (applied) {
                // update the sync timestamp
                syncPreferences.lastSyncTimestamp.set(Date().time)
                // SY -->
                syncPreferences.lastPushedAt.set(syncStart)
                // SY <--
                // KUN -->
                commitRemoteState(result)
                markFullSyncCompleted(full, result)
                // The repair is complete: the server has the forced state and the local bump is
                // written. A failed restore or cache write below leaves the flag set so the next run
                // resumes the force push instead of doing an ordinary delta.
                if (forcePushV2) {
                    syncPreferences.forcePushPending.set(false)
                }
                // The snapshot is fully applied, so only now may its omissions delete rows. A
                // failed restore drops the ids and the next full sync recomputes them.
                applySnapshotMangaDeletions(mangaIdsToDelete)
                applySnapshotHistoryPrune(historyIdsToPrune)
                // KUN <--
            } else {
                // SY -->
                // The restore failed, so ask the server for everything on the next run to self-heal.
                syncPreferences.fullSyncRequested.set(true)
                notifier.showSyncError("Failed to apply synced data; will retry with a full sync")
                logcat(LogPriority.ERROR) { "Sync restore failed; requesting full sync on next run" }
                // SY <--
            }
        } else {
            // KUN -->
            syncPreferences.fullSyncRequested.set(true)
            notifier.showSyncError("Failed to write sync data to cache; will retry with a full sync")
            // KUN <--
            logcat(LogPriority.ERROR) { "Failed to write sync data to file" }
        }
    }

    // SY -->
    private fun finishWithSuccess(
        syncStart: Long,
        message: String,
        // KUN -->
        // Clear the force-push marker only after the cursor advanced, so a crash between the two
        // cannot leave the repair marked done while `lastPushedAt` still points at the old window.
        forcePushCompleted: Boolean = false,
        // KUN <--
    ) {
        syncPreferences.lastSyncTimestamp.set(Date().time)
        // everything modified before this instant reached the server; the next delta starts here
        syncPreferences.lastPushedAt.set(syncStart)
        // KUN -->
        if (forcePushCompleted) {
            syncPreferences.forcePushPending.set(false)
        }
        // KUN <--
        notifier.showSyncSuccess(message)
    }
    // SY <--

    // KUN -->
    /**
     * Commits what the server reported once the sync reached it. Every success exit uses this so the
     * cursor, the server's full-sync flag and the pending category tombstones stay in step with
     * [finishWithSuccess]'s `lastPushedAt`.
     */
    private fun commitRemoteState(result: SyncResult) {
        result.cursor?.let { syncPreferences.syncCursor.set(it) }
        syncPreferences.fullSyncRequested.set(result.fullSyncRequested)
        syncPreferences.pendingDeletedCategoryUids.set(
            syncPreferences.pendingDeletedCategoryUids.get() - result.pendingDeletedCategories,
        )
    }

    /**
     * Only a full v2 sync whose restore succeeded counts as done. `needsFullSync()` reads this
     * timestamp to decide when the next full upload is due, so an aborted or failed run must not
     * move it forward.
     */
    private fun markFullSyncCompleted(full: Boolean, result: SyncResult) {
        if (full && result.protocolV2) {
            syncPreferences.lastFullSync.set(Clock.System.now().toEpochMilliseconds())
        }
    }

    /**
     * Applies the manga the server omitted from a full v2 snapshot, deleting them locally. Called
     * only on success exits, after (or instead of) the restore, so a partial restore never deletes
     * rows the server did not actually drop.
     */
    private suspend fun applySnapshotMangaDeletions(ids: List<Long>) {
        if (ids.isEmpty()) return
        // The ids are local row ids; the download cleanup needs the rows themselves.
        val localMangaById = syncRepository.getAllManga().associateBy { it.id }
        ids.forEach { id ->
            localMangaById[id]?.let { deleteMangaRow(it) } ?: syncRepository.deleteManga(id)
        }
        logcat(LogPriority.DEBUG) { "Deleted ${ids.size} local manga absent from the full server snapshot" }
    }

    /**
     * Clears the local history entries a full v2 snapshot no longer lists, through the same reset
     * the user's own removal uses (the row keeps its id and loses its read date, which is what the
     * History tab shows as gone). Only the ids [historyIdsMissingFromFullSnapshot] produced reach
     * this, so a delta, a disabled history section or a row read during the sync is never pruned.
     *
     * Called only on the success exits, like the manga absence deletions, so a failed restore
     * leaves the rows for the next full sync to re-examine.
     */
    private suspend fun applySnapshotHistoryPrune(ids: List<Long>) {
        if (ids.isEmpty()) return
        historyRepository.resetHistory(ids)
        // The reset logs its own failures and returns nothing, so this can only report what was
        // asked for; a failure leaves the rows for the next full sync to find again.
        logcat(LogPriority.DEBUG) {
            "Requested clearing ${ids.size} local history entries absent from the full server snapshot"
        }
    }

    /**
     * Removes a manga row the server deleted elsewhere, after clearing what hangs off its id outside
     * the database: its download directory and its queued chapters would otherwise stay behind with
     * nothing left to name them. Cleanup is best-effort so a file-system failure cannot leave the
     * tombstone unapplied and retried forever; the row is deleted either way.
     */
    private suspend fun deleteMangaRow(manga: Manga) {
        try {
            downloadManager.deleteManga(manga, sourceManager.getOrStub(manga.source))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to clean up the downloads of ${manga.url}" }
        }
        syncRepository.deleteManga(manga.id)
    }
    // KUN <--

    // SY -->
    private fun Backup.hasNothingToRestore() = backupManga.isEmpty() &&
        backupCategories.isEmpty() &&
        backupSources.isEmpty() &&
        backupPreferences.isEmpty() &&
        backupSourcePreferences.isEmpty() &&
        backupExtensionStores.isEmpty() &&
        backupSavedSearches.isEmpty() &&
        // KMK -->
        backupFeeds.isEmpty()
    // KMK <--
    // SY <--

    private fun writeSyncDataToCache(context: Context, backup: Backup): Uri? {
        val cacheFile = File(context.cacheDir, "tachiyomi_sync_data.proto.gz")
        return try {
            // SY -->
            cacheFile.sink().buffer().use { BackupRequestBody(backup, ProtoBuf).writeTo(it) }
            // SY <--
            Uri.fromFile(cacheFile)
        } catch (e: IOException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to write sync data to cache" }
            null
        }
    }

    /**
     * Retrieves all manga from the local database.
     *
     * @return a list of all manga stored in the database
     */
    private suspend fun getAllMangaFromDB(): List<Manga> {
        return syncRepository.getAllManga()
    }

    private suspend fun getAllMangaThatNeedsSync(): List<Manga> {
        return syncRepository.getMangasToSync(syncPreferences.lastPushedAt.get())
    }

    // SY -->
    private suspend fun getMangasModifiedSince(since: Long): List<Manga> {
        return syncRepository.getMangasModifiedSince(since)
    }
    // SY <--

    private suspend fun isMangaDifferent(
        localManga: Manga,
        remoteManga: BackupManga,
        completeChapterList: Boolean,
    ): Boolean {
        return needsRestore(localStateOf(localManga), remoteManga, completeChapterList)
    }

    // SY -->
    // KUN -->
    /**
     * Everything the restore decision needs from the local row, gathered in one place so a manga
     * whose local state matches the payload can be skipped without touching its chapters or history.
     */
    private suspend fun localStateOf(manga: Manga): LocalMangaState {
        val chapters = chapterRepository
            .getChapterByMangaId(manga.id, applyFilter = false)
        val chapterUrlById = chapters.associate { it.id to it.url }
        return LocalMangaState(
            manga = manga,
            chapters = chapters,
            categoryOrders = getCategories.await(manga.id).mapTo(mutableSetOf()) { it.order },
            lastReadByChapterUrl = historyRepository.getHistoryByMangaId(manga.id)
                .mapNotNull { history ->
                    val url = chapterUrlById[history.chapterId] ?: return@mapNotNull null
                    history.readAt?.let { url to it.toEpochMilliseconds() }
                }
                .toMap(),
        )
    }
    // KUN <--
    // SY <--

    /**
     * Filters the favorite and non-favorite manga from the backup and checks
     * if the favorite manga is different from the local database.
     * @param backup the Backup object containing the backup data.
     * @return a Pair of lists, where the first list contains different favorite manga
     * and the second list contains non-favorite manga.
     */
    private suspend fun filterFavoritesAndNonFavorites(
        backup: Backup,
        // KUN -->
        completeChapterList: Boolean,
        // KUN <--
    ): Pair<List<BackupManga>, List<BackupManga>> {
        val favorites = mutableListOf<BackupManga>()
        val nonFavorites = mutableListOf<BackupManga>()
        val logTag = "filterFavoritesAndNonFavorites"

        val elapsedTimeMillis = measureTimeMillis {
            val databaseManga = getAllMangaFromDB()
            // KUN -->
            val localMangaMap = databaseManga.associateBy {
                it.source to it.url
            }

            logcat(LogPriority.DEBUG, logTag) { "Starting to filter favorites and non-favorites from backup data." }

            backup.backupManga.forEach { remoteManga ->
                val compositeKey = remoteManga.source to remoteManga.url
                val localManga = localMangaMap[compositeKey]
                // KUN <--
                when {
                    // Checks if the manga is in favorites and needs updating or adding
                    remoteManga.favorite -> {
                        // KUN -->
                        val localFavTime = localManga?.favoriteModifiedAt
                        val remoteFavTime = remoteManga.favoriteModifiedAt
                        val localUnfavoritedMoreRecently = localManga != null &&
                            !localManga.favorite &&
                            localFavTime != null &&
                            (remoteFavTime == null || localFavTime > remoteFavTime)

                        if (!localUnfavoritedMoreRecently &&
                            (localManga == null || isMangaDifferent(localManga, remoteManga, completeChapterList))
                        ) {
                            logcat(LogPriority.DEBUG, logTag) { "Adding to favorites: ${remoteManga.title}" }
                            favorites.add(remoteManga)
                        } else {
                            logcat(LogPriority.DEBUG, logTag) { "Already up-to-date favorite: ${remoteManga.title}" }
                        }
                        // KUN <--
                    }
                    // Handle non-favorites
                    !remoteManga.favorite -> {
                        logcat(LogPriority.DEBUG, logTag) { "Adding to non-favorites: ${remoteManga.title}" }
                        nonFavorites.add(remoteManga)
                    }
                }
            }
        }

        val minutes = elapsedTimeMillis / 60000
        val seconds = (elapsedTimeMillis % 60000) / 1000
        logcat(LogPriority.DEBUG, logTag) {
            "Filtering completed in ${minutes}m ${seconds}s. Favorites found: ${favorites.size}, " +
                "Non-favorites found: ${nonFavorites.size}"
        }

        return Pair(favorites, nonFavorites)
    }

    /**
     * Updates the non-favorite manga in the local database with their favorite status from the backup.
     * @param nonFavorites the list of non-favorite BackupManga objects from the backup.
     */
    private suspend fun updateNonFavorites(nonFavorites: List<BackupManga>) {
        val localMangaList = getAllMangaFromDB()

        // KUN -->
        val localMangaMap = localMangaList.associateBy { it.source to it.url }

        nonFavorites.forEach { nonFavorite ->
            val key = nonFavorite.source to nonFavorite.url
            localMangaMap[key]?.let { localManga ->
                if (localManga.favorite != nonFavorite.favorite) {
                    val remoteFavTime = nonFavorite.favoriteModifiedAt
                    val localFavTime = localManga.favoriteModifiedAt
                    val shouldUnfavorite = when {
                        remoteFavTime != null && localFavTime != null -> remoteFavTime > localFavTime
                        remoteFavTime != null && localFavTime == null -> {
                            val localAddedSeconds = localManga.dateAdded?.epochSeconds ?: 0L
                            remoteFavTime > localAddedSeconds
                        }
                        else -> nonFavorite.version >= localManga.version
                    }

                    if (shouldUnfavorite) {
                        val updatedManga = localManga.copy(
                            favorite = false,
                            favoriteModifiedAt = remoteFavTime,
                            version = maxOf(localManga.version, nonFavorite.version),
                        )
                        mangaRestorer.updateManga(updatedManga)
                    }
                }
            }
        }
        // KUN <--
    }

    // KUN -->
    companion object {
        private val NON_SYNCABLE_PREFERENCE_KEYS = setOf(
            "categorized_display",
            // Local unmapped category IDs
            "pref_filter_upcoming_included_categories",
            "pref_filter_upcoming_excluded_categories",
            "pref_filter_updates_included_categories",
            "pref_filter_updates_excluded_categories",
            "discord_rpc_incognito_categories",
            "eh_enabled_categories",
            // Biometrics & screen privacy
            "use_biometric_lock",
            "lock_app_after",
            "secure_screen_v2",
            // Hardware & display profiles (file URIs or device GPU constraints)
            "pref_high_quality_renderer_key",
            "pref_display_profile_key",
            "pref_always_decode_long_strip_with_ssiv",
            // WebGPU shaders are files in this device's filesDir; syncing their names would point
            // the other device at files it does not have (prune() then deletes the entries).
            "webgpu_image_filters",
            "webgpu_tile_filters",
            // Removed from the app; upgraded installs still carry the stale value.
            "split_tall_images",
            // Form factor & device layout
            "tablet_ui_mode",
            "pref_library_columns_portrait_key",
            "pref_library_columns_landscape_key",
            "pref_dual_page_view",
            "pref_dual_page_split",
            "pref_dual_page_rotate",
            "page_layout",
            "pref_default_orientation_type_key",
            "cutout_short",
            "webgpu_cutout_mode",
            "webgpu_dual_cutout_mode",
            // Network & battery constraints
            "pref_download_only_over_wifi_key",
            "pref_reader_load_ahead_only_over_wifi",
            "library_update_restriction",
            // Sync engine service selector & credentials
            "sync_service",
            "connection_sync_client_host",
            "connection_sync_client_api_key",
        )

        /**
         * Restore options for a sync run.
         *
         * Pinned sources are only replaced when the server merged the upload for us (SyncYomi
         * protocol v2): Google Drive, WebDAV and SyncYomi v1 merge preferences locally with the
         * remote value winning whenever both sides carry the key, so a local pin or un-pin is
         * discarded there and the server's set is the only one the restore sees. Replacing in that
         * case would delete local pins the server does not know about; the restorer therefore
         * unions instead, which never removes a local pin.
         */
        internal fun syncRestoreOptions(syncOptions: SyncSettings, protocolV2: Boolean): RestoreOptions {
            return RestoreOptions(
                appSettings = syncOptions.appSettings,
                sourceSettings = syncOptions.sourceSettings,
                libraryEntries = syncOptions.libraryEntries,
                categories = syncOptions.categories,
                extensionStores = syncOptions.extensionStores,
                savedSearchesFeeds = syncOptions.savedSearchesFeeds,
                tracking = syncOptions.tracking,
                history = syncOptions.history,
                customInfo = syncOptions.customInfo,
                skipPinnedSources = !syncOptions.syncPinnedSources,
                replacePinnedSources = syncOptions.syncPinnedSources && protocolV2,
            )
        }

        /**
         * App preferences that are safe to upload in a sync. Pinned sources are device-local unless
         * the user enabled [SyncSettings.syncPinnedSources]; manual/file backups do not use this
         * filter and still contain them.
         */
        internal fun filterSyncableAppPreferences(
            preferences: List<BackupPreference>,
            syncPinnedSources: Boolean,
        ): List<BackupPreference> {
            return preferences.filterNot {
                it.key in NON_SYNCABLE_PREFERENCE_KEYS ||
                    it.key.startsWith("connection_webdav_") ||
                    it.key.startsWith("pref_connections_password_") ||
                    it.key in LibraryPreferences.categoryPreferenceKeys ||
                    it.key in DownloadPreferences.categoryPreferenceKeys ||
                    (!syncPinnedSources && it.key == SourcePreferences.PINNED_SOURCES_PREF_KEY)
            }
        }

        /**
         * Drops the pinned-source preference from a server preference list when this device does
         * not sync it. Applying the same filter to the uploaded list and to the server payload
         * makes the change comparison converge after one sync instead of reporting the stale remote
         * key as a difference forever.
         */
        internal fun withoutDeviceLocalPinnedSources(
            preferences: List<BackupPreference>,
            syncPinnedSources: Boolean,
        ): List<BackupPreference> {
            if (syncPinnedSources) return preferences
            return preferences.filterNot { it.key == SourcePreferences.PINNED_SOURCES_PREF_KEY }
        }

        /** Whether the server's app preferences differ from what this device uploaded. */
        internal fun hasSyncPreferenceChanges(
            remotePreferences: List<BackupPreference>,
            uploadedPreferences: List<BackupPreference>,
            syncPinnedSources: Boolean,
        ): Boolean {
            val remote = withoutDeviceLocalPinnedSources(remotePreferences, syncPinnedSources)
            return remote.isNotEmpty() && remote != uploadedPreferences
        }
    }
    // KUN <--
}
