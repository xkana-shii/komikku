package eu.kanade.tachiyomi.data.backup.models

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey

// KNS -->
@OptIn(ExperimentalSerializationApi::class)
class BackupForkStateTest {

    @Test
    fun `an old fork backup's next update comes from field 114, not its last update at 113`() {
        val oldFork = BackupManga(source = 1, url = "/m", nextUpdate = 1_000, legacyNextUpdate = 5_000)
        val decoded = ProtoBuf.decodeFromByteArray<BackupManga>(ProtoBuf.encodeToByteArray(oldFork))
        decoded.getMangaImpl().nextUpdate?.toEpochMilliseconds() shouldBe 5_000L

        val current = BackupManga(source = 1, url = "/m", nextUpdate = 1_000)
        val bytes = ProtoBuf.encodeToByteArray(current)
        ProtoBuf.decodeFromByteArray<BackupManga>(bytes).legacyNextUpdate shouldBe 0L
        ProtoBuf.decodeFromByteArray<BackupManga>(bytes).getMangaImpl().nextUpdate?.toEpochMilliseconds() shouldBe 1_000L
    }

    @Test
    fun `an entry without any fork state restores none`() {
        BackupManga(url = "/m", title = "M").getForkState() shouldBe null
    }

    @Test
    fun `old fork Fillermarks become exact blacklist entries`() {
        val manga = BackupManga(
            url = "/m",
            title = "M",
            chapters = listOf(
                BackupChapter(url = "/c1", name = "1", fillermark = true),
                BackupChapter(url = "/c2", name = "2"),
            ),
            blacklistedChapters = listOf("url:/old"),
        )

        val state = manga.getForkState()!!
        state.blacklistedChapters shouldContainExactlyInAnyOrder listOf("url:/old", chapterUrlBlacklistKey("/c1"))
        state.scanlatorSettingsPresent shouldBe true
        state.smartMergeOverridePresent shouldBe false
    }

    @Test
    fun `the override field distinguishes global from absent`() {
        BackupManga(url = "/m", title = "M", smartScanlatorMergeOverride = "global").getForkState()!!.let {
            it.smartMergeOverridePresent shouldBe true
            it.smartMergeOverride shouldBe null
        }
        BackupManga(url = "/m", title = "M", smartScanlatorMergeOverride = "disabled")
            .getForkState()!!.smartMergeOverride shouldBe false
    }

    @Test
    fun `an ordinary bookmark color survives encoding, a missing one decodes as unknown`() {
        val chapters = listOf(
            Chapter.create().copy(url = "/a", bookmark = true, bookmarkColor = 0),
            Chapter.create().copy(url = "/b", bookmark = true, bookmarkColor = 4),
            Chapter.create().copy(url = "/c", bookmark = false, bookmarkColor = 4),
        ).map { it.toBackupChapter() }

        val decoded = ProtoBuf.decodeFromByteArray<List<BackupChapter>>(ProtoBuf.encodeToByteArray(chapters))
            .map { it.toChapterImpl() }

        decoded.map { it.bookmarkColor } shouldBe listOf(0, 4, Chapter.BOOKMARK_COLOR_UNKNOWN)
    }

    @Test
    fun `an old backup without the field restores an unknown color`() {
        BackupChapter(url = "/a", name = "a", bookmark = true).toChapterImpl().bookmarkColor shouldBe
            Chapter.BOOKMARK_COLOR_UNKNOWN
    }
}
// KNS <--
