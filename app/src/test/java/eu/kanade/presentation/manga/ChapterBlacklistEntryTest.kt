
package eu.kanade.presentation.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.chapter.service.scanlatorBlacklistKey

// KMK -->
class ChapterBlacklistEntryTest {

    @Test
    fun `ambiguous numbered scanlator key does not resolve unrelated duplicates`() {
        val first = chapter(id = 1, name = "Chapter 12", number = 12.0, scanlator = "Team")
        val second = chapter(id = 2, name = "Alternative title", number = 12.0, scanlator = "Team")
        val key = scanlatorBlacklistKey(12.0, "Team")

        val entry = resolveChapterBlacklistEntries(listOf(key), listOf(first, second)).single()

        entry.key shouldBe key
        entry.chapter shouldBe null
        entry.chapterNumber shouldBe 12.0
        entry.scanlator shouldBe "Team"
        entry.matchingChapterCount shouldBe 0
        entry.isAvailable shouldBe false
    }

    @Test
    fun `numbered scanlator key resolves a unique matching chapter`() {
        val first = chapter(id = 1, name = "Chapter 12", number = 12.0, scanlator = "Team")
        val key = scanlatorBlacklistKey(12.0, "Team")

        val entry = resolveChapterBlacklistEntries(listOf(key), listOf(first)).single()

        entry.key shouldBe key
        entry.chapter shouldBe first
        entry.chapterNumber shouldBe 12.0
        entry.scanlator shouldBe "Team"
        entry.matchingChapterCount shouldBe 1
        entry.isAvailable shouldBe true
    }

    @Test
    fun `URL blacklist key resolves only the selected duplicate`() {
        val first = chapter(id = 1, name = "Chapter 12", number = 12.0, scanlator = "Team")
        val second = chapter(id = 2, name = "Alternative title", number = 12.0, scanlator = "Team")
        val key = chapterUrlBlacklistKey(second.url)

        val entry = resolveChapterBlacklistEntries(listOf(key), listOf(first, second)).single()

        entry.key shouldBe key
        entry.chapter shouldBe second
        entry.chapterNumber shouldBe 12.0
        entry.scanlator shouldBe "Team"
        entry.matchingChapterCount shouldBe 1
        entry.isAvailable shouldBe true
    }

    @Test
    fun `uses the actual name for special chapter numbers`() {
        val notice = chapter(id = 1, name = "Special Announcement", number = -1.0, scanlator = "Team")
        val key = chapterUrlBlacklistKey(notice.url)

        val entry = resolveChapterBlacklistEntries(listOf(key), listOf(notice)).single()

        entry.chapter?.name shouldBe "Special Announcement"
        entry.chapterNumber shouldBe null
        entry.scanlator shouldBe "Team"
    }

    @Test
    fun `resolves migrated URL keys without exposing the key as a label`() {
        val migrated = chapter(id = 1, name = "Fillermark notice", number = 0.0, scanlator = null)
        val key = chapterUrlBlacklistKey(migrated.url)

        val entry = resolveChapterBlacklistEntries(listOf(key), listOf(migrated)).single()

        entry.key shouldBe key
        entry.chapter?.name shouldBe "Fillermark notice"
        entry.chapterNumber shouldBe null
    }

    @Test
    fun `keeps an unavailable key removable while retaining readable key details`() {
        val key = scanlatorBlacklistKey(8.5, "Group")

        val entry = resolveChapterBlacklistEntries(listOf(key), emptyList()).single()

        entry.key shouldBe key
        entry.chapter shouldBe null
        entry.chapterNumber shouldBe 8.5
        entry.scanlator shouldBe "Group"
        entry.matchingChapterCount shouldBe 0
        entry.isAvailable shouldBe false
    }

    private fun chapter(
        id: Long,
        name: String,
        number: Double,
        scanlator: String?,
    ): Chapter = Chapter.create().copy(
        id = id,
        mangaId = 1,
        url = "/chapter/$id",
        name = name,
        chapterNumber = number,
        scanlator = scanlator,
    )
}
// KMK <--
