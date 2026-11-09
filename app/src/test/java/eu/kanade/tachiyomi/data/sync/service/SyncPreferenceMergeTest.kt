// KUN -->
package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * The generic app-preference merge: a key both sides carry keeps the remote value, a key present on
 * only one side is carried over, and a remote clear replaces a non-empty local value.
 */
class SyncPreferenceMergeTest {

    private val service = object : SyncService(
        context = mockk<Context>(relaxed = true),
        json = Json,
        syncPreferences = SyncPreferences(InMemoryPreferenceStore()),
    ) {
        override suspend fun doSync(syncData: SyncData, full: Boolean): SyncResult = error("not used")
    }

    private fun string(key: String, value: String) = BackupPreference(key, StringPreferenceValue(value))

    private fun merge(
        local: List<BackupPreference>,
        remote: List<BackupPreference>,
    ): Map<String, BackupPreference> {
        return service.mergePreferencesLists(local, remote).associateBy { it.key }
    }

    private fun Map<String, BackupPreference>.stringValue(key: String): String {
        return (getValue(key).value as StringPreferenceValue).value
    }

    @Test
    fun `a remote value wins over a different non-empty local value`() {
        val merged = merge(
            local = listOf(string("some_string_preference", "local")),
            remote = listOf(string("some_string_preference", "remote")),
        )

        assertEquals("remote", merged.stringValue("some_string_preference"))
    }

    @Test
    fun `keys present on only one side are carried over`() {
        val merged = merge(
            local = listOf(string("only_local", "local")),
            remote = listOf(string("only_remote", "remote")),
        )

        assertEquals(setOf("only_local", "only_remote"), merged.keys)
        assertEquals("local", merged.stringValue("only_local"))
        assertEquals("remote", merged.stringValue("only_remote"))
    }

    @Test
    fun `a remote clear replaces a non-empty local value`() {
        val merged = merge(
            local = listOf(string("some_string_preference", "local")),
            remote = listOf(string("some_string_preference", "")),
        )

        assertEquals("", merged.stringValue("some_string_preference"))
    }
}
// KUN <--
