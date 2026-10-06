package eu.kanade.tachiyomi.data.sync

import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.sync.service.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncService
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

// KMK -->
class SyncConvergenceTest {
    private val service = object : SyncService(
        mockk(), Json,
        mockk<SyncPreferences> { every { uniqueDeviceID() } returns "test-device" },
    ) {
        override suspend fun doSync(syncData: SyncData): Backup? = syncData.backup
        fun merge(local: SyncData, remote: SyncData) = mergeSyncData(local, remote)
    }

    @Test
    fun `mutable metadata does not split identities and repeated merges converge`() {
        val local = BackupManga(
            source = 1, url = "/same", title = "Local title", author = "Local author", version = 1,
            chapters = listOf(BackupChapter("/chapter", "Old name", chapterNumber = 1F, version = 1)),
        )
        val remote = BackupManga(
            source = 1, url = "/same", title = "Remote title", author = "Remote author", version = 2,
            chapterFlags = 123, viewer_flags = 456, customTitle = "Custom title",
            updateStrategy = UpdateStrategy.ONLY_FETCH_ONCE,
            chapters = listOf(BackupChapter("/chapter", "New name", chapterNumber = 2F, read = true, bookmark = true, fillermark = true, version = 3)),
        )
        val merged = service.merge(SyncData(backup = Backup(listOf(local))), SyncData(backup = Backup(listOf(remote))))
        val manga = merged.backup!!.backupManga.single()
        manga.title shouldBe "Remote title"
        manga.author shouldBe "Remote author"
        manga.version shouldBe 2
        manga.chapterFlags shouldBe 123
        manga.viewer_flags shouldBe 456
        manga.customTitle shouldBe "Custom title"
        manga.updateStrategy shouldBe UpdateStrategy.ONLY_FETCH_ONCE
        val chapter = manga.chapters.single()
        chapter.name shouldBe "New name"
        chapter.chapterNumber shouldBe 2F
        chapter.read shouldBe true
        chapter.bookmark shouldBe true
        chapter.fillermark shouldBe true
        chapter.version shouldBe 3
        val repeated = service.merge(merged, merged)
        Json.encodeToString(SyncData.serializer(), repeated) shouldBe Json.encodeToString(SyncData.serializer(), merged)
    }

    @Test
    fun `identical URLs from different sources remain separate`() {
        val local = SyncData(backup = Backup(listOf(BackupManga(1, "/same"))))
        val remote = SyncData(backup = Backup(listOf(BackupManga(2, "/same"))))
        service.merge(local, remote).backup!!.backupManga.map { it.source }.toSet() shouldBe setOf(1L, 2L)
    }
}
// KMK <--
