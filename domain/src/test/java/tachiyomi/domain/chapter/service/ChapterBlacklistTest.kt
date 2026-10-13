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

    @Test
    fun `hybrid key hides only the selected duplicate`() {
        val selected = chapter(url = "/chapter,100%|one", number = 12.0, scanlator = "Team|A")
        val duplicate = chapter(url = "/chapter/two", number = 12.0, scanlator = "Team|A").copy(id = 2)
        val otherScanlator = chapter(url = "/chapter/three", number = 12.0, scanlator = "Team B").copy(id = 3)
        val key = hybridChapterBlacklistKey(selected)

        filterBlacklistedChapters(listOf(selected, duplicate, otherScanlator), listOf(key))
            .map { it.url } shouldBe listOf(duplicate.url, otherScanlator.url)
    }

    @Test
    fun `hybrid key falls back to a unique metadata match after URL change`() {
        val original = chapter(url = "/old", number = 8.0, scanlator = "Team")
        val refreshed = chapter(url = "/new", number = 8.0, scanlator = " team ")
        val key = hybridChapterBlacklistKey(original)

        filterBlacklistedChapters(listOf(refreshed), listOf(key)) shouldBe emptyList()
    }

    @Test
    fun `hybrid key leaves ambiguous URL replacements visible`() {
        val original = chapter(url = "/old", number = 8.0, scanlator = "Team")
        val replacementOne = chapter(url = "/new-1", number = 8.0, scanlator = "Team").copy(id = 2)
        val replacementTwo = chapter(url = "/new-2", number = 8.0, scanlator = "Team").copy(id = 3)

        filterBlacklistedChapters(
            listOf(replacementOne, replacementTwo),
            listOf(hybridChapterBlacklistKey(original)),
        ).size shouldBe 2
    }

    @Test
    fun `hybrid key uses URL for special chapter numbers`() {
        val selected = chapter(url = "/special", number = 0.0, scanlator = "Team")
        val duplicate = chapter(url = "/other-special", number = 0.0, scanlator = "Team").copy(id = 2)

        filterBlacklistedChapters(listOf(selected, duplicate), listOf(hybridChapterBlacklistKey(selected)))
            .map { it.url } shouldBe listOf(duplicate.url)
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
