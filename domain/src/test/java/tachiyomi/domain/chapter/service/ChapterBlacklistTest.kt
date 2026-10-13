package tachiyomi.domain.chapter.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

// KMK -->
class ChapterBlacklistTest {
    @Test
    fun `scanlator key matches its normalized chapter identity`() {
        val chapter = chapter(number = 12.345, scanlator = "  Team  ")

        isChapterBlacklisted(chapter, listOf(scanlatorBlacklistKey(12.345, "Team"))) shouldBe true
    }

    @Test
    fun `URL key only excludes the Fillermarked chapter when number and scanlator collide`() {
        val fillermarked = chapter(url = "/chapter/one", number = 0.0, scanlator = null)
        val unmarkedDuplicate = chapter(url = "/chapter/two", number = 0.0, scanlator = null)
        val migratedKeys = legacyFillermarkBlacklistKeys(listOf(fillermarked.url))

        isChapterBlacklisted(fillermarked, migratedKeys) shouldBe true
        isChapterBlacklisted(unmarkedDuplicate, migratedKeys) shouldBe false
    }

    @Test
    fun `URL key only excludes the selected numbered chapter when scanlator metadata collides`() {
        val selected = chapter(url = "/chapter/one", number = 12.0, scanlator = "Team")
        val duplicate = chapter(url = "/chapter/two", number = 12.0, scanlator = "Team")
        val keys = listOf(chapterUrlBlacklistKey(selected.url))

        isChapterBlacklisted(selected, keys) shouldBe true
        isChapterBlacklisted(duplicate, keys) shouldBe false
    }

    @Test
    fun `URL keys escape separators and remain exact`() {
        val chapter = chapter(url = "/chapter,100%")
        val keys = listOf(chapterUrlBlacklistKey(chapter.url))

        keys shouldBe listOf("url:/chapter%2C100%25")
        isChapterBlacklisted(chapter, keys) shouldBe true
    }

    private fun chapter(
        url: String = "/chapter",
        number: Double = 1.0,
        scanlator: String? = "Group",
    ) = Chapter.create().copy(
        id = 1,
        mangaId = 1,
        url = url,
        name = url,
        chapterNumber = number,
        scanlator = scanlator,
    )
}
// KMK <--
