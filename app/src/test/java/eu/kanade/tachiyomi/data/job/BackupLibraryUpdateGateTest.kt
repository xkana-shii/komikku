package eu.kanade.tachiyomi.data.job

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

// KNS -->
class BackupLibraryUpdateGateTest {

    @Test
    fun `a second job starts only after the first finished`() = runBlocking<Unit> {
        val events = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val backupStarted = CompletableDeferred<Unit>()

        val backup = async {
            BackupLibraryUpdateGate.withExclusiveAccess {
                events += "backup start"
                backupStarted.complete(Unit)
                release.await()
                events += "backup end"
            }
        }
        backupStarted.await()
        val update = async {
            BackupLibraryUpdateGate.withExclusiveAccess { events += "update start" }
        }
        // the update had its chance to run while the backup was still going
        repeat(5) { kotlinx.coroutines.yield() }
        events shouldBe listOf("backup start")

        release.complete(Unit)
        backup.await()
        update.await()
        events shouldBe listOf("backup start", "backup end", "update start")
    }

    @Test
    fun `a failing job still frees the gate`() = runBlocking<Unit> {
        runCatching { BackupLibraryUpdateGate.withExclusiveAccess { error("boom") } }
        BackupLibraryUpdateGate.withExclusiveAccess { "ok" } shouldBe "ok"
    }
}
// KNS <--
