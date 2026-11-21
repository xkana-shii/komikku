package eu.kanade.domain.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.core.common.preference.Preference

/**
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY#1658).
 */
class SyncPreferencesTest {

    private fun syncPreferences(
        syncService: Int,
        // Tombstones are only recorded once the current host is a confirmed v2 server (see
        // SyncPreferences.isSyncYomiV2), so a v2 server is the default for these tests.
        serverSupportsV2: Boolean = true,
        probedHost: String = "https://sync.tachiyomi.org",
        libraryEntries: Boolean = true,
    ) = SyncPreferences(
        InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreference("sync_service", syncService, 0),
                InMemoryPreference("__APP_STATE_sync_server_supports_v2", serverSupportsV2, false),
                InMemoryPreference("__APP_STATE_sync_v2_probed_host", probedHost, ""),
                InMemoryPreference("sync_library_entries", libraryEntries, true),
            ),
        ),
    )

    @Test
    fun `deleted chapters stay pending until a re-add forgets them`() {
        val prefs = syncPreferences(syncService = 1)

        prefs.rememberDeletedChapters(listOf("a", "b"))
        prefs.forgetDeletedChapters(listOf("a", "c"))

        assertEquals(setOf("b"), prefs.pendingDeletedChapterKeys.get())
    }

    @Test
    fun `deleted chapters are not tracked while sync is off`() {
        val prefs = syncPreferences(syncService = 0)

        prefs.rememberDeletedChapters(listOf("a"))

        assertEquals(emptySet<String>(), prefs.pendingDeletedChapterKeys.get())
    }

    // KUN -->
    @Test
    fun `pinned source sync is off by default`() {
        val prefs = SyncPreferences(InMemoryPreferenceStore())

        assertFalse(prefs.getSyncSettings().syncPinnedSources)
    }

    @Test
    fun `pinned source sync round-trips through the sync settings`() {
        val prefs = SyncPreferences(InMemoryPreferenceStore())
        assertFalse(prefs.getSyncSettings().syncPinnedSources)

        prefs.setSyncSettings(prefs.getSyncSettings().copy(syncPinnedSources = true))
        assertTrue(prefs.getSyncSettings().syncPinnedSources)

        prefs.setSyncSettings(prefs.getSyncSettings().copy(syncPinnedSources = false))
        assertFalse(prefs.getSyncSettings().syncPinnedSources)
    }

    @Test
    fun `pinned source sync is device-local app state`() {
        val prefs = SyncPreferences(InMemoryPreferenceStore())

        assertEquals(
            Preference.appStateKey("sync_pinned_sources"),
            prefs.syncPinnedSources.key(),
        )
        assertTrue(Preference.isAppState(prefs.syncPinnedSources.key()))
    }

    @Test
    fun `cached protocol v2 needs the SyncYomi service and the probed host`() {
        assertTrue(syncPreferences(syncService = 1).isSyncYomiV2())
        assertFalse(syncPreferences(syncService = 2).isSyncYomiV2())
        assertFalse(syncPreferences(syncService = 0).isSyncYomiV2())
        assertFalse(syncPreferences(syncService = 1, serverSupportsV2 = false).isSyncYomiV2())
        assertFalse(syncPreferences(syncService = 1, probedHost = "https://other").isSyncYomiV2())
    }

    @Test
    fun `deleted manga stay pending until the server acknowledges them`() {
        val prefs = syncPreferences(syncService = 1)

        prefs.rememberDeletedManga(listOf("1|/a", "2|/b"))
        prefs.forgetDeletedManga(listOf("1|/a", "3|/c"))

        assertEquals(setOf("2|/b"), prefs.pendingDeletedMangaKeys.get())
    }

    @Test
    fun `deleted manga are not tracked while sync is off`() {
        assertEquals(
            emptySet<String>(),
            syncPreferences(syncService = 0).also { it.rememberDeletedManga(listOf("1|/a")) }
                .pendingDeletedMangaKeys.get(),
        )
        assertEquals(
            emptySet<String>(),
            syncPreferences(syncService = 2).also { it.rememberDeletedManga(listOf("1|/a")) }
                .pendingDeletedMangaKeys.get(),
        )
        assertEquals(
            emptySet<String>(),
            syncPreferences(syncService = 1, serverSupportsV2 = false)
                .also { it.rememberDeletedManga(listOf("1|/a")) }
                .pendingDeletedMangaKeys.get(),
        )
    }

    @Test
    fun `deleted manga are not tracked while library sync is disabled`() {
        val prefs = syncPreferences(syncService = 1, libraryEntries = false)

        prefs.rememberDeletedManga(listOf("1|/a"))

        assertEquals(emptySet<String>(), prefs.pendingDeletedMangaKeys.get())
    }

    @Test
    fun `forgetting the acknowledged manga keys keeps a key recorded meanwhile pending`() {
        val prefs = syncPreferences(syncService = 1)
        prefs.rememberDeletedManga(listOf("1|/a", "2|/b"))
        val sent = setOf("1|/a", "2|/b")

        // A delete that happens while the deletions request is in flight must survive the clear.
        prefs.rememberDeletedManga(listOf("3|/c"))
        prefs.forgetDeletedManga(sent)

        assertEquals(setOf("3|/c"), prefs.pendingDeletedMangaKeys.get())
    }
    // KUN <--
}
