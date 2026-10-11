package eu.kanade.tachiyomi.util.chapter

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.chapter.service.scanlatorBlacklistKey

// KMK -->
class ChapterSmartMergeTest {

    @Test
    fun `keeps the first candidate when no scanlator priority is configured`() {
        val result = listOf(
            chapter(id = 1, number = 5.0, scanlator = "Group B"),
            chapter(id = 2, number = 5.0, scanlator = "Group A"),
        ).applySmartScanlatorMerge(emptyList(), emptyList(), emptyList())

        result.map(Chapter::id) shouldBe listOf(1L)
    }

    @Test
    fun `uses global scanlator priority for duplicate numbered chapters`() {
        val result = listOf(
            chapter(id = 1, number = 5.0, scanlator = "Group B"),
            chapter(id = 2, number = 5.0, scanlator = "Group A"),
            chapter(id = 3, number = 6.0, scanlator = "Group B"),
        ).applySmartScanlatorMerge(listOf("Group A", "Group B"), emptyList(), emptyList())

        result.map(Chapter::id) shouldBe listOf(2L, 3L)
    }

    @Test
    fun `a range rule takes precedence and falls back to global priority`() {
        val result = listOf(
            chapter(id = 1, number = 5.0, scanlator = "Group A"),
            chapter(id = 2, number = 5.0, scanlator = "Group B"),
            chapter(id = 3, number = 10.0, scanlator = "Group A"),
        ).applySmartScanlatorMerge(
            priority = listOf("Group A", "Group B"),
            blacklistedChapters = emptyList(),
            rangeRules = listOf("1:7:Group B", "8:12:Missing"),
        )

        result.map(Chapter::id) shouldBe listOf(2L, 3L)
    }

    @Test
    fun `last matching range rule wins`() {
        val result = listOf(
            chapter(id = 1, number = 5.0, scanlator = "Group A"),
            chapter(id = 2, number = 5.0, scanlator = "Group B"),
        ).applySmartScanlatorMerge(
            priority = emptyList(),
            blacklistedChapters = emptyList(),
            rangeRules = listOf("1:10:Group A", "4:6:Group B"),
        )

        result.map(Chapter::id) shouldBe listOf(2L)
    }

    @Test
    fun `removes URL-blacklisted special chapters without affecting a matching placeholder number`() {
        val blacklisted = chapter(id = 1, number = -1.0, scanlator = null, url = "/notice-a")
        val visible = chapter(id = 2, number = -1.0, scanlator = null, url = "/notice-b")

        val result = listOf(blacklisted, visible).applySmartScanlatorMerge(
            priority = emptyList(),
            blacklistedChapters = listOf(chapterUrlBlacklistKey(blacklisted.url)),
            rangeRules = emptyList(),
        )

        result.map(Chapter::id) shouldBe listOf(2L)
    }

    @Test
    fun `falls back after a preferred candidate is blacklisted and hides an all-blacklisted group`() {
        val first = chapter(id = 1, number = 5.0, scanlator = "Group A")
        val second = chapter(id = 2, number = 5.0, scanlator = "Group B")
        val allBlacklisted = chapter(id = 3, number = 6.0, scanlator = "Group A")

        val result = listOf(first, second, allBlacklisted).applySmartScanlatorMerge(
            priority = listOf("Group A", "Group B"),
            blacklistedChapters = listOf(
                scanlatorBlacklistKey(5.0, "Group A"),
                scanlatorBlacklistKey(6.0, "Group A"),
            ),
            rangeRules = emptyList(),
        )

        result.map(Chapter::id) shouldBe listOf(2L)
    }

    @Test
    fun `does not deduplicate special and invalid chapter numbers`() {
        val result = listOf(
            chapter(id = 1, number = -1.0, scanlator = "Group A"),
            chapter(id = 2, number = -1.0, scanlator = "Group B"),
            chapter(id = 3, number = 0.0, scanlator = "Group A"),
            chapter(id = 4, number = Double.NaN, scanlator = "Group B"),
        ).applySmartScanlatorMerge(listOf("Group B"), emptyList(), emptyList())

        result.map(Chapter::id) shouldBe listOf(1L, 2L, 3L, 4L)
    }

    @Test
    fun `range rules round trip and reject invalid bounds`() {
        val rule = ScanlatorRangeRule(1.5, 12.0, "Group:Name")

        parseScanlatorRangeRule(encodeScanlatorRangeRule(rule)) shouldBe rule
        parseScanlatorRangeRule("12:1:Group") shouldBe null
        parseScanlatorRangeRule("NaN:1:Group") shouldBe null
        parseScanlatorRangeRule("1:Infinity:Group") shouldBe null
    }

    private fun chapter(
        id: Long,
        number: Double,
        scanlator: String?,
        url: String = "/chapter/$id",
    ): Chapter = Chapter.create().copy(
        id = id,
        mangaId = 1,
        url = url,
        name = "Chapter $id",
        chapterNumber = number,
        scanlator = scanlator,
    )
}
// KMK <--
