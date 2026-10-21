// KUN -->
package eu.kanade.tachiyomi.data.sync

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncManagerPreferencesTest {

    private val pinnedKey = SourcePreferences.PINNED_SOURCES_PREF_KEY

    private fun preferences(vararg keys: String): List<BackupPreference> {
        return keys.map { BackupPreference(it, BooleanPreferenceValue(true)) }
    }

    @Test
    fun `pinned sources are not uploaded when the setting is off`() {
        val filtered = SyncManager.filterSyncableAppPreferences(
            preferences(pinnedKey, "sync_library_entries"),
            syncPinnedSources = false,
        )

        assertEquals(listOf("sync_library_entries"), filtered.map { it.key })
    }

    @Test
    fun `pinned sources are uploaded when the setting is on`() {
        val filtered = SyncManager.filterSyncableAppPreferences(
            preferences(pinnedKey, "sync_library_entries"),
            syncPinnedSources = true,
        )

        assertTrue(pinnedKey in filtered.map { it.key })
        assertEquals(2, filtered.size)
    }

    @Test
    fun `other device-local preferences stay excluded from the sync upload`() {
        val filtered = SyncManager.filterSyncableAppPreferences(
            preferences("sync_service", "tablet_ui_mode", "connection_webdav_url", pinnedKey),
            syncPinnedSources = true,
        )

        // Only the (enabled) pinned-source key survives the device-local exclusions.
        assertEquals(listOf(pinnedKey), filtered.map { it.key })
    }

    @Test
    fun `non-v2 sync restores union the synced pins instead of replacing them`() {
        val options = SyncManager.syncRestoreOptions(
            SyncSettings(syncPinnedSources = true),
            protocolV2 = false,
        )

        assertFalse(options.skipPinnedSources)
        assertFalse(options.replacePinnedSources)
    }

    @Test
    fun `v2 sync restores replace the local pins`() {
        val options = SyncManager.syncRestoreOptions(
            SyncSettings(syncPinnedSources = true),
            protocolV2 = true,
        )

        assertFalse(options.skipPinnedSources)
        assertTrue(options.replacePinnedSources)
    }

    @Test
    fun `sync with pinned sources off skips the restore`() {
        val options = SyncManager.syncRestoreOptions(
            SyncSettings(syncPinnedSources = false),
            protocolV2 = true,
        )

        assertTrue(options.skipPinnedSources)
        assertFalse(options.replacePinnedSources)
    }

    @Test
    fun `stale remote pinned key does not count as a preference change when pins are device-local`() {
        val remote = preferences(pinnedKey, "sync_library_entries")
        val upload = preferences("sync_library_entries")

        assertFalse(
            SyncManager.hasSyncPreferenceChanges(
                remotePreferences = remote,
                uploadedPreferences = upload,
                syncPinnedSources = false,
            ),
        )
    }

    @Test
    fun `a remote payload with only the stale pinned key counts as no change`() {
        assertFalse(
            SyncManager.hasSyncPreferenceChanges(
                remotePreferences = preferences(pinnedKey),
                uploadedPreferences = emptyList(),
                syncPinnedSources = false,
            ),
        )
    }

    @Test
    fun `real preference changes are still detected when pins are device-local`() {
        val remote = preferences(pinnedKey, "sync_library_entries", "sync_new_flag")
        val upload = preferences("sync_library_entries")

        assertTrue(
            SyncManager.hasSyncPreferenceChanges(
                remotePreferences = remote,
                uploadedPreferences = upload,
                syncPinnedSources = false,
            ),
        )
    }

    @Test
    fun `pinned source changes still count when pins are synced`() {
        assertTrue(
            SyncManager.hasSyncPreferenceChanges(
                remotePreferences = preferences(pinnedKey),
                uploadedPreferences = emptyList(),
                syncPinnedSources = true,
            ),
        )
    }

    @Test
    fun `restored preference list drops the device-local pinned key`() {
        val stripped = SyncManager.withoutDeviceLocalPinnedSources(
            preferences(pinnedKey, "sync_library_entries"),
            syncPinnedSources = false,
        )

        assertEquals(listOf("sync_library_entries"), stripped.map { it.key })
    }
}
// KUN <--
