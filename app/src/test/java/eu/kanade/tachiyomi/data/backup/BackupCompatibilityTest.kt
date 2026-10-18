
package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.toBackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
class BackupCompatibilityTest {

    @Serializable
    private data class LegacyManga(
        @ProtoNumber(1)
        val source: Long,

        @ProtoNumber(2)
        val url: String,

        @ProtoNumber(100)
        val favorite: Boolean = false,
    )

    @Serializable
    private data class SchedulingFields(
        // Existing field 113 must not be renumbered.
        @ProtoNumber(113)
        val next: Long = 0,

        // Fields 114, 115, 116 are already used by
        // scanlator settings.
        @ProtoNumber(117)
        val last: Long = 0,

        @ProtoNumber(118)
        val interval: Int = 0,
    )

    @Test
    fun `old backups decode absent scheduling fields as zero`() {
        val bytes = ProtoBuf.encodeToByteArray(
            LegacyManga.serializer(),
            LegacyManga(
                source = 1L,
                url = "/old",
            ),
        )

        val restored = ProtoBuf.decodeFromByteArray(
            BackupManga.serializer(),
            bytes,
        )

        restored.lastUpdate shouldBe 0L
        restored.nextUpdate shouldBe 0L
        restored.fetchInterval shouldBe 0

        restored.scanlatorPriority shouldBe emptyList()
        restored.blacklistedChapters shouldBe emptyList()
        restored.scanlatorRangeRules shouldBe emptyList()
    }

    @Test
    fun `scheduling fields and custom metadata round trip`() {
        val manga = Manga.create().copy(
            source = 1L,
            url = "/manga",
            ogTitle = "Source title",
            ogGenre = listOf("Source tag"),
            ogStatus = 1L,
            lastUpdate = Instant.fromEpochMilliseconds(1234),
            nextUpdate = Instant.fromEpochMilliseconds(5678),
            fetchInterval = 7,
        )

        val backup = manga.toBackupManga(
            CustomMangaInfo(
                id = manga.id,
                title = "Custom",
                genre = emptyList(),
                status = 2L,
            ),
        )

        val bytes = ProtoBuf.encodeToByteArray(
            BackupManga.serializer(),
            backup,
        )

        ProtoBuf.decodeFromByteArray(
            SchedulingFields.serializer(),
            bytes,
        ) shouldBe SchedulingFields(
            next = 5678L,
            last = 1234L,
            interval = 7,
        )

        val decoded = ProtoBuf.decodeFromByteArray(
            BackupManga.serializer(),
            bytes,
        )

        decoded.getCustomMangaInfo()?.genre shouldBe emptyList()
        decoded.customStatus shouldBe 2
        decoded.title shouldBe "Source title"
        decoded.genre shouldBe listOf("Source tag")

        val restored = decoded.getMangaImpl()
        restored.lastUpdate shouldBe Instant.fromEpochMilliseconds(1234)
        restored.nextUpdate shouldBe Instant.fromEpochMilliseconds(5678)
        restored.fetchInterval shouldBe 7
        restored.ogTitle shouldBe "Source title"
        restored.ogGenre shouldBe listOf("Source tag")
        restored.ogStatus shouldBe 1L
    }

    @Test
    fun `category restriction distinguishes all none and overlapping membership`() {
        BackupOptions().includesManga(
            emptyList(),
        ) shouldBe true

        BackupOptions(
            includedCategoryIds = emptySet(),
        ).includesManga(
            listOf(1L),
        ) shouldBe false

        BackupOptions(
            includedCategoryIds = setOf(0L),
        ).includesManga(
            emptyList(),
        ) shouldBe true

        BackupOptions(
            includedCategoryIds = setOf(2L),
        ).includesManga(
            listOf(1L, 2L),
        ) shouldBe true

        BackupOptions(
            includedCategoryIds = setOf(2L),
        ).includesManga(
            listOf(1L),
        ) shouldBe false

        BackupOptions(
            includedCategoryIds = setOf(2L),
        ).asBooleanArray().size shouldBe 12

        val defaults = BackupOptions.fromBooleanArray(
            BackupOptions().asBooleanArray(),
        )

        defaults.includedCategoryIds shouldBe null
    }

    @Test
    fun `scanlator settings keep their assigned protobuf fields`() {
        val original = BackupManga(
            source = 1L,
            url = "/manga",
            scanlatorPriority = listOf("Group A"),
            blacklistedChapters = listOf("url:/extra"),
            scanlatorRangeRules = listOf("Group B:1-10"),
        )

        val restored = ProtoBuf.decodeFromByteArray(
            BackupManga.serializer(),
            ProtoBuf.encodeToByteArray(
                BackupManga.serializer(),
                original,
            ),
        )

        restored.scanlatorPriority shouldBe listOf("Group A")
        restored.blacklistedChapters shouldBe listOf("url:/extra")
        restored.scanlatorRangeRules shouldBe listOf("Group B:1-10")
    }
}
