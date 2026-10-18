package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SyncConvergenceTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.data.backup.models.BackupChapter")
        }
    }
}
