package eu.kanade.tachiyomi.data.backup

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class RestoreDatabaseTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.data.backup.models.BackupChapter")
        }
    }
}
