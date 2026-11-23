// KUN -->
package mihon.core.migration.migrations

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import mihon.core.migration.MigrationContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * The retired history tombstone set has to disappear from an upgraded store, and nowhere else: an
 * install that never wrote it must stay untouched (the migration is guarded on `isSet`, so it can
 * run twice).
 */
// KNS --> this fork's InMemoryPreferenceStore does not keep writes (Komi-kun's does), so the
// preference is a mock whose `delete()` is observed instead of read back from a store
class DeleteRetiredHistoryQueueMigrationTest {

    private val retiredKey = Preference.appStateKey("sync_pending_deleted_history_keys")

    private fun storeWith(retired: Preference<Set<String>>): PreferenceStore = mockk {
        every { getStringSet(retiredKey, any()) } returns retired
    }

    @Test
    fun `the retired history queue is deleted from an upgraded store`() = runBlocking {
        val retired = mockk<Preference<Set<String>>>(relaxed = true) { every { isSet() } returns true }

        val completed = DeleteRetiredHistoryQueueMigration(storeWith(retired)).invoke(MigrationContext(false, 0))

        // A leftover set would keep one dead key per removed entry for the life of the install.
        assertTrue(completed)
        verify(exactly = 1) { retired.delete() }
    }

    @Test
    fun `a store that never wrote the retired key is left alone`() = runBlocking {
        val retired = mockk<Preference<Set<String>>>(relaxed = true) { every { isSet() } returns false }

        val completed = DeleteRetiredHistoryQueueMigration(storeWith(retired)).invoke(MigrationContext(false, 0))

        assertTrue(completed)
        verify(exactly = 0) { retired.delete() }
    }
}
// KNS <--
// KUN <--
