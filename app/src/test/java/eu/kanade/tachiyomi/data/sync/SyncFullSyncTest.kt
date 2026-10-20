package eu.kanade.tachiyomi.data.sync

import androidx.work.Data
import androidx.work.workDataOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The force-full and force-push requests are one-shot flags in [SyncDataJob]'s WorkManager input
 * data, not preferences, so they must survive the data round-trip and default to false for
 * everything else (periodic syncs, other callers).
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

    @Test
    fun `no input data means no force push request`() {
        assertFalse(Data.EMPTY.forcePushRequested())
    }

    @Test
    fun `a force push request round-trips through the input data`() {
        assertTrue(workDataOf(FORCE_PUSH_SYNC_KEY to true).forcePushRequested())
    }

    @Test
    fun `an explicit false is not a force push request`() {
        assertFalse(workDataOf(FORCE_PUSH_SYNC_KEY to false).forcePushRequested())
    }

    @Test
    fun `the force flags are independent`() {
        assertTrue(workDataOf(FORCE_FULL_SYNC_KEY to true).forceFullRequested())
        assertFalse(workDataOf(FORCE_FULL_SYNC_KEY to true).forcePushRequested())
        assertFalse(workDataOf(FORCE_PUSH_SYNC_KEY to true).forceFullRequested())
    }
}
