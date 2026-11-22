// KUN -->
package mihon.core.migration.migrations

import kotlinx.coroutines.runBlocking
import mihon.core.migration.MigrationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference

/**
 * The retired history tombstone set has to disappear from an upgraded store, and nowhere else: an
 * install that never wrote it must stay untouched (the migration is guarded on `isSet`, so it can
 * run twice), and a neighbouring preference must survive it.
 */
class DeleteRetiredHistoryQueueMigrationTest {

    private val retiredKey = Preference.appStateKey("sync_pending_deleted_history_keys")

    @Test
    fun `the retired history queue is deleted from an upgraded store`() = runBlocking {
        val store = InMemoryPreferenceStore()
        store.getStringSet(retiredKey, emptySet()).set(setOf("1|/m\u001f/m/ch1", "1|/m\u001f/m/ch2"))
        // a preference the migration must not touch
        store.getString("connection_sync_client_api_key", "").set("key")

        DeleteRetiredHistoryQueueMigration(store).invoke(MigrationContext(false, 0))

        // A leftover set would keep one dead key per removed entry for the life of the install.
        assertFalse(store.getStringSet(retiredKey, emptySet()).isSet())
        assertEquals("key", store.getString("connection_sync_client_api_key", "").get())
    }

    @Test
    fun `a store that never wrote the retired key is left alone`() = runBlocking {
        val store = InMemoryPreferenceStore()

        val completed = DeleteRetiredHistoryQueueMigration(store).invoke(MigrationContext(false, 0))

        assertTrue(completed)
        assertFalse(store.getStringSet(retiredKey, emptySet()).isSet())
    }
}
// KUN <--
