package eu.kanade.tachiyomi.data.sync

import androidx.work.Data
import androidx.work.workDataOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The force-full request is a one-shot flag in [SyncDataJob]'s WorkManager input data, not a
 * preference, so it must survive the data round-trip and default to false for everything else
 * (periodic syncs, other callers).
 */
class SyncFullSyncTest {

    @Test
    fun `no input data means no force full request`() {
        assertFalse(Data.EMPTY.forceFullRequested())
    }

    @Test
    fun `a force full request round-trips through the input data`() {
        assertTrue(workDataOf(FORCE_FULL_SYNC_KEY to true).forceFullRequested())
    }

    @Test
    fun `an explicit false is not a force full request`() {
        assertFalse(workDataOf(FORCE_FULL_SYNC_KEY to false).forceFullRequested())
    }

    @Test
    fun `unrelated input data does not request a force full sync`() {
        assertFalse(workDataOf("manual" to true).forceFullRequested())
    }
}
