package eu.kanade.domain.sync

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.models.SyncTriggerOptions
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {
    val clientHost = preferenceStore.getString("connection_sync_client_host", "https://sync.tachiyomi.org")
    val clientAPIKey = preferenceStore.getString("connection_sync_client_api_key", "")
    val lastSyncTimestamp = preferenceStore.getLong(Preference.appStateKey("last_sync_timestamp"), 0L)

    val lastSyncEtag = preferenceStore.getString(Preference.appStateKey("sync_etag"), "")

    // SY -->
    // SyncYomi protocol v2 state
    val syncCursor = preferenceStore.getLong(Preference.appStateKey("sync_cursor"), 0L)

    /** Epoch seconds of the start of the last successful sync; the next delta starts here. */
    val lastPushedAt = preferenceStore.getLong(Preference.appStateKey("sync_last_pushed_at"), 0L)

    /** Epoch millis of the last sync that carried the complete library. */
    val lastFullSync = preferenceStore.getLong(Preference.appStateKey("sync_last_full_sync"), 0L)
    val fullSyncRequested = preferenceStore.getBoolean(
        Preference.appStateKey("sync_full_requested"),
        false,
    )
    val serverSupportsV2 = preferenceStore.getBoolean(
        Preference.appStateKey("sync_server_supports_v2"),
        false,
    )
    val v2ProbedHost = preferenceStore.getString(Preference.appStateKey("sync_v2_probed_host"), "")
    val pendingDeletedCategoryUids = preferenceStore.getStringSet(
        Preference.appStateKey("sync_pending_deleted_category_uids"),
        emptySet(),
    )
    // KUN -->
    /** Chapter keys a source dropped, kept until the server acknowledges the tombstone. */
    // KUN <--
    val pendingDeletedChapterKeys = preferenceStore.getStringSet(
        Preference.appStateKey("sync_pending_deleted_chapter_keys"),
        emptySet(),
    )
    // SY <--

    // KUN -->
    /**
     * A force push whose server upload was accepted but whose local half (database bump and restore)
     * has not necessarily finished. A later run that still uses SyncYomi resumes the repair instead
     * of uploading the raised versions as an ordinary delta with empty disabled sections.
     */
    val forcePushPending = preferenceStore.getBoolean(
        Preference.appStateKey("sync_force_push_pending"),
        false,
    )
    // KUN <--

    val syncInterval = preferenceStore.getInt("sync_interval", 0)
    val syncService = preferenceStore.getInt("sync_service", 0)

    // KMK -->
    val webDavUrl = preferenceStore.getString("connection_webdav_url", "")
    val webDavUsername = preferenceStore.getString("connection_webdav_username", "")
    val webDavPassword = preferenceStore.getString("connection_webdav_password", "")
    val webDavFolder = preferenceStore.getString("connection_webdav_folder", "komikku")
    // KMK <--

    val googleDriveAccessToken = preferenceStore.getString(
        Preference.appStateKey("connection_google_drive_access_token"),
        "",
    )

    val googleDriveRefreshToken = preferenceStore.getString(
        Preference.appStateKey("connection_google_drive_refresh_token"),
        "",
    )

    fun uniqueDeviceID(): String {
        val uniqueIDPreference = preferenceStore.getString(Preference.appStateKey("unique_device_id"), "")

        // Retrieve the current value of the preference
        var uniqueID = uniqueIDPreference.get()
        if (uniqueID.isBlank()) {
            uniqueID = UUID.randomUUID().toString()
            uniqueIDPreference.set(uniqueID)
        }

        return uniqueID
    }

    fun isSyncEnabled(): Boolean {
        return syncService.get() != SyncManager.SyncService.NONE.value
    }

    // KUN -->
    /**
     * Only the SyncYomi service consumes these tombstones, and only once the server is known to
     * speak protocol v2. Recording them for Google Drive, WebDAV or an old v1 SyncYomi server would
     * let the sets grow without bound and then flush a stale backlog once the user switches to a v2
     * server, potentially deleting live categories or chapters.
     *
     * [serverSupportsV2] is the persisted flag that SyncYomiSyncService sets when the capability
     * probe succeeds; it is only meaningful for the host it was probed against, so require a match.
     */
    private fun isSyncYomiV2(): Boolean {
        return syncService.get() == SyncManager.SyncService.SYNCYOMI.value &&
            v2ProbedHost.get() == clientHost.get() &&
            serverSupportsV2.get()
    }

    /** Serialises the read-modify-write of the tombstone sets so concurrent edits are not lost. */
    private val tombstoneLock = Any()
    // KUN <--

    // SY -->
    /**
     * Remembers a deleted category so the next SyncYomi v2 request can send it as a tombstone.
     */
    fun rememberDeletedCategory(uid: Long) {
        // KUN -->
        if (uid == 0L || !isSyncYomiV2()) return
        synchronized(tombstoneLock) {
            pendingDeletedCategoryUids.set(pendingDeletedCategoryUids.get() + uid.toString())
        }
        // KUN <--
    }

    // KUN -->
    /**
     * Forgets category tombstones for categories that are seen live again, so a live category is
     * never deleted on the server.
     */
    fun forgetDeletedCategory(uids: Collection<Long>) {
        if (uids.isEmpty()) return
        val keys = uids.mapTo(mutableSetOf()) { it.toString() }
        synchronized(tombstoneLock) {
            val pending = pendingDeletedCategoryUids.get()
            val remaining = pending - keys
            if (remaining.size != pending.size) pendingDeletedCategoryUids.set(remaining)
        }
    }
    // KUN <--
    // KUN -->
    /**
     * Remembers the chapters a source dropped, so the next SyncYomi v2 request can tombstone them.
     */
    // KUN <--
    fun rememberDeletedChapters(keys: Collection<String>) {
        // KUN -->
        if (keys.isEmpty() || !isSyncYomiV2()) return
        synchronized(tombstoneLock) {
            pendingDeletedChapterKeys.set(pendingDeletedChapterKeys.get() + keys)
        }
        // KUN <--
    }

    // KUN -->
    /**
     * Forgets chapters a source re-added before they were sent, so a live chapter is never tombstoned.
     */
    // KUN <--
    fun forgetDeletedChapters(keys: Collection<String>) {
        // KUN -->
        synchronized(tombstoneLock) {
            val pending = pendingDeletedChapterKeys.get()
            val remaining = pending - keys.toSet()
            if (remaining.size != pending.size) pendingDeletedChapterKeys.set(remaining)
        }
        // KUN <--
    }
    // SY <--

    fun getSyncSettings(): SyncSettings {
        return SyncSettings(
            libraryEntries = preferenceStore.getBoolean("sync_library_entries", true).get(),
            categories = preferenceStore.getBoolean("sync_categories", true).get(),
            chapters = preferenceStore.getBoolean("sync_chapters", true).get(),
            tracking = preferenceStore.getBoolean("sync_tracking", true).get(),
            history = preferenceStore.getBoolean("sync_history", true).get(),
            appSettings = preferenceStore.getBoolean("sync_appSettings", true).get(),
            extensionStores = preferenceStore.getBoolean("sync_extensionStores", true).get(),
            sourceSettings = preferenceStore.getBoolean("sync_sourceSettings", true).get(),
            privateSettings = preferenceStore.getBoolean("sync_privateSettings", true).get(),

            // SY -->
            customInfo = preferenceStore.getBoolean("sync_customInfo", true).get(),
            readEntries = preferenceStore.getBoolean("sync_readEntries", true).get(),
            savedSearchesFeeds = preferenceStore.getBoolean("sync_savedSearchesFeeds", true).get(),
            // SY <--
        )
    }

    fun setSyncSettings(syncSettings: SyncSettings) {
        preferenceStore.getBoolean("sync_library_entries", true).set(syncSettings.libraryEntries)
        preferenceStore.getBoolean("sync_categories", true).set(syncSettings.categories)
        preferenceStore.getBoolean("sync_chapters", true).set(syncSettings.chapters)
        preferenceStore.getBoolean("sync_tracking", true).set(syncSettings.tracking)
        preferenceStore.getBoolean("sync_history", true).set(syncSettings.history)
        preferenceStore.getBoolean("sync_appSettings", true).set(syncSettings.appSettings)
        preferenceStore.getBoolean("sync_extensionStores", true).set(syncSettings.extensionStores)
        preferenceStore.getBoolean("sync_sourceSettings", true).set(syncSettings.sourceSettings)
        preferenceStore.getBoolean("sync_privateSettings", true).set(syncSettings.privateSettings)

        // SY -->
        preferenceStore.getBoolean("sync_customInfo", true).set(syncSettings.customInfo)
        preferenceStore.getBoolean("sync_readEntries", true).set(syncSettings.readEntries)
        preferenceStore.getBoolean("sync_savedSearchesFeeds", true).set(syncSettings.savedSearchesFeeds)
        // SY <--
    }

    fun getSyncTriggerOptions(): SyncTriggerOptions {
        return SyncTriggerOptions(
            syncOnChapterRead = preferenceStore.getBoolean("sync_on_chapter_read", false).get(),
            syncOnChapterOpen = preferenceStore.getBoolean("sync_on_chapter_open", false).get(),
            syncOnAppStart = preferenceStore.getBoolean("sync_on_app_start", false).get(),
            syncOnAppResume = preferenceStore.getBoolean("sync_on_app_resume", false).get(),
        )
    }

    fun setSyncTriggerOptions(syncTriggerOptions: SyncTriggerOptions) {
        preferenceStore.getBoolean("sync_on_chapter_read", false)
            .set(syncTriggerOptions.syncOnChapterRead)
        preferenceStore.getBoolean("sync_on_chapter_open", false)
            .set(syncTriggerOptions.syncOnChapterOpen)
        preferenceStore.getBoolean("sync_on_app_start", false)
            .set(syncTriggerOptions.syncOnAppStart)
        preferenceStore.getBoolean("sync_on_app_resume", false)
            .set(syncTriggerOptions.syncOnAppResume)
    }

    // KMK -->
    val showSyncingProgressBanner = preferenceStore.getBoolean(
        Preference.appStateKey("pref_show_syncing_progress_banner_key"),
        true,
    )
    // KMK <--
}
