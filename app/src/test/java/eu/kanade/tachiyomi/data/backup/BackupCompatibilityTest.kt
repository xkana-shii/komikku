package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.toBackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga

@OptIn(ExperimentalSerializationApi::class)
class BackupCompatibilityTest {
    @Serializable
    private data class LegacyManga(@ProtoNumber(1) val source: Long, @ProtoNumber(2) val url: String, @ProtoNumber(100) val favorite: Boolean = false)

    @Serializable
    private data class SchedulingFields(@ProtoNumber(113) val last: Long = 0, @ProtoNumber(114) val next: Long = 0, @ProtoNumber(115) val interval: Int = 0)

    @Test
    fun `old backups decode absent scheduling fields as zero`() {
        val bytes = ProtoBuf.encodeToByteArray(LegacyManga.serializer(), LegacyManga(1, "/old"))
        val restored = ProtoBuf.decodeFromByteArray(BackupManga.serializer(), bytes)
        restored.lastUpdate shouldBe 0L
        restored.nextUpdate shouldBe 0L
        restored.fetchInterval shouldBe 0
    }

    @Test
    fun `scheduling fields and custom metadata round trip without replacing source data`() {
        val manga = Manga.create().copy(source = 1, url = "/manga", ogTitle = "Source title", ogGenre = listOf("Source tag"), ogStatus = 1, lastUpdate = 1234, nextUpdate = 5678, fetchInterval = 7)
        val backup = manga.toBackupManga(CustomMangaInfo(id = manga.id, title = "Custom", genre = emptyList(), status = 2))
        val bytes = ProtoBuf.encodeToByteArray(BackupManga.serializer(), backup)
        ProtoBuf.decodeFromByteArray(SchedulingFields.serializer(), bytes) shouldBe SchedulingFields(1234, 5678, 7)
        val decoded = ProtoBuf.decodeFromByteArray(BackupManga.serializer(), bytes)
        decoded.getCustomMangaInfo()!!.genre shouldBe emptyList()
        decoded.customStatus shouldBe 2
        decoded.title shouldBe "Source title"
        decoded.genre shouldBe listOf("Source tag")
        decoded.getMangaImpl().lastUpdate shouldBe 1234L
        decoded.getMangaImpl().nextUpdate shouldBe 5678L
        decoded.getMangaImpl().fetchInterval shouldBe 7
    }

    @Test
    fun `category restriction distinguishes all none default and overlapping membership`() {
        BackupOptions().includesManga(emptyList()) shouldBe true
        BackupOptions(includedCategoryIds = emptySet()).includesManga(listOf(1)) shouldBe false
        BackupOptions(includedCategoryIds = setOf(0)).includesManga(emptyList()) shouldBe true
        BackupOptions(includedCategoryIds = setOf(2)).includesManga(listOf(1, 2)) shouldBe true
        BackupOptions(includedCategoryIds = setOf(2)).includesManga(listOf(1)) shouldBe false
        BackupOptions(includedCategoryIds = setOf(2)).asBooleanArray().size shouldBe 12
        BackupOptions.fromBooleanArray(BackupOptions().asBooleanArray()).includedCategoryIds shouldBe null
    }
}
