package tachiyomi.domain.chapter.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ScanlatorRangeRule
import tachiyomi.domain.chapter.model.applySmartScanlatorMerge
import tachiyomi.domain.chapter.model.chapterListVisibility
import tachiyomi.domain.chapter.model.encodeScanlatorRangeRule
import tachiyomi.domain.chapter.model.filterChapterVisibility
import tachiyomi.domain.chapter.model.parseScanlatorRangeRule
import tachiyomi.domain.chapter.model.resolveVisibleChapter
import tachiyomi.domain.chapter.model.visibleChapterStats
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.chapter.service.hybridChapterBlacklistKey
import tachiyomi.domain.chapter.service.scanlatorBlacklistKey
import tachiyomi.domain.manga.model.Manga

// KNS -->
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

    @Test
    fun `uses the same visibility rule for merge toggles blacklist and library counts`() {
        val hidden = chapter(id = 1, number = 5.0, scanlator = "Group B").copy(read = false)
        val preferred = chapter(id = 2, number = 5.0, scanlator = "Group A").copy(read = true, bookmark = true)
        val blacklisted = chapter(id = 3, number = 6.0, scanlator = "Group A")
        val manga = Manga.create().copy(
            scanlatorPriority = listOf("Group A", "Group B"),
            blacklistedChapters = listOf(scanlatorBlacklistKey(6.0, "Group A")),
        )

        val merged = listOf(hidden, preferred, blacklisted).filterChapterVisibility(manga, true)

        merged.map(Chapter::id) shouldBe listOf(2L)
        merged.visibleChapterStats().total shouldBe 1L
        merged.visibleChapterStats().read shouldBe 1L
        listOf(hidden, preferred, blacklisted).filterChapterVisibility(manga, false).map(Chapter::id) shouldBe listOf(1L, 2L)
    }

    @Test
    fun `resolves a hidden history chapter to its visible duplicate`() {
        val hidden = chapter(id = 1, number = 5.0, scanlator = "Group B")
        val preferred = chapter(id = 2, number = 5.0, scanlator = "Group A")
        val manga = Manga.create().copy(scanlatorPriority = listOf("Group A", "Group B"))

        listOf(hidden, preferred).resolveVisibleChapter(manga, true, hidden.id) shouldBe preferred
    }

    @Test
    fun `reveals only explicit blacklist matches without unmerging other duplicates`() {
        val blacklisted = chapter(id = 1, number = 5.0, scanlator = "Group A")
        val preferred = chapter(id = 2, number = 5.0, scanlator = "Group B")
        val hiddenDuplicate = chapter(id = 3, number = 5.0, scanlator = "Group C")
        val manga = Manga.create().copy(
            scanlatorPriority = listOf("Group B", "Group C"),
            blacklistedChapters = listOf(chapterUrlBlacklistKey(blacklisted.url)),
        )

        val result = listOf(blacklisted, preferred, hiddenDuplicate).chapterListVisibility(
            manga = manga,
            smartScanlatorMergeEnabled = true,
            hideBlacklistedChapters = false,
        )

        result.chapters.map(Chapter::id) shouldBe listOf(1L, 2L)
        result.blacklistedChapterIds shouldBe setOf(1L)
    }

    @Test
    fun `filtered chapter subsets resolve refreshed Mangago extras against the complete snapshot`() {
        val oldExtra = chapter(1, 1.0, null, "/twitter-extra-old").copy(name = "twitter extra 1st anniversary")
        val extra = oldExtra.copy(url = "/twitter-extra-new")
        val chapterOne = chapter(2, 1.0, null, "/chapter-1").copy(name = "Chapter 1")
        val manga = Manga.create().copy(blacklistedChapters = listOf(hybridChapterBlacklistKey(oldExtra)))

        listOf(chapterOne).filterChapterVisibility(manga, smartScanlatorMergeEnabled = false, allChapters = listOf(extra, chapterOne))
            .map(Chapter::id) shouldBe listOf(chapterOne.id)
    }

    @Test
    fun `a dated notice carrying a chapter number does not hide that chapter`() {
        // The Edge of Ambiguity on Mangago: "March 6th" was recognized as chapter 6.
        val notice = chapter(1, 6.0, "Jujucat").copy(name = "March 6th")
        val chapterSix = chapter(2, 6.0, "Jujucat").copy(name = "Ch. 6")
        val other = chapter(3, 6.0, "Other").copy(name = "Ch. 6")
        val merged = listOf(notice, chapterSix, other).applySmartScanlatorMerge(
            priority = listOf("Jujucat", "Other"),
            blacklistedChapters = emptyList(),
            rangeRules = emptyList(),
        )
        merged.map(Chapter::id) shouldBe listOf(1L, 2L)
    }

    @Test
    fun `notices and date posts are never merged`() {
        val names = listOf("March 6th Notice", "Season Comeback 6/15", "Anniversary 1", "Hiatus announcement 3")
        names.forEachIndexed { index, name ->
            chapter(10L + index, 6.0, "Other").copy(name = name).isNotice() shouldBe true
        }
        chapter(20, 6.0, "Other").copy(name = "Chapter 6: March 6th").isNotice() shouldBe false
        chapter(21, 1.5, "Other").copy(name = "Side story 1.5").isNotice() shouldBe false
    }

    @Test
    fun `distinct uploads of the preferred scanlator stay visible`() {
        val official = chapter(1, 10.0, "Jujucat").copy(name = "Chapter 10 (Official)")
        val scanlated = chapter(2, 10.0, "Jujucat").copy(name = "Chapter 10")
        val other = chapter(3, 10.0, "Other").copy(name = "Chapter 10")
        listOf(official, scanlated, other).applySmartScanlatorMerge(
            priority = listOf("Jujucat", "Other"),
            blacklistedChapters = emptyList(),
            rangeRules = emptyList(),
        ).map(Chapter::id) shouldBe listOf(1L, 2L)
    }

    @Test
    fun `identical uploads from one scanlator are all retained`() {
        val first = chapter(1, 10.0, "Jujucat", "/a").copy(name = "Chapter 10")
        val relisted = chapter(2, 10.0, "Jujucat", "/b").copy(name = " chapter  10 ")
        listOf(first, relisted).applySmartScanlatorMerge(emptyList(), emptyList(), emptyList())
            .map(Chapter::id) shouldBe listOf(1L, 2L)
    }

    @Test
    fun `equivalent releases of different scanlators follow priority then range rules`() {
        val a = chapter(1, 10.0, "A").copy(name = "Chapter 10")
        val b = chapter(2, 10.0, "B").copy(name = "Chapter 10")
        val c = chapter(3, 10.0, "C").copy(name = "Chapter 10")
        listOf(a, b, c).applySmartScanlatorMerge(listOf("B", "A", "C"), emptyList(), emptyList())
            .map(Chapter::id) shouldBe listOf(2L)
        listOf(a, b, c).applySmartScanlatorMerge(
            listOf("B", "A", "C"),
            emptyList(),
            listOf(encodeScanlatorRangeRule(ScanlatorRangeRule(5.0, 15.0, "C"))),
        ).map(Chapter::id) shouldBe listOf(3L)
    }

    @Test
    fun `a blacklisted preferred release falls back to the next scanlator`() {
        val preferred = chapter(1, 10.0, "A").copy(name = "Chapter 10")
        val fallback = chapter(2, 10.0, "B").copy(name = "Chapter 10")
        listOf(preferred, fallback).applySmartScanlatorMerge(
            listOf("A", "B"),
            listOf(hybridChapterBlacklistKey(preferred)),
            emptyList(),
        ).map(Chapter::id) shouldBe listOf(2L)
    }

    @Test
    fun `visible unread count includes a chapter that shares its number with a notice`() {
        val chapters = (1..107).map { chapter(it.toLong(), it.toDouble(), "Jujucat").copy(name = "Ch. $it") } +
            chapter(500, 6.0, "Jujucat").copy(name = "March 6th", read = true) +
            chapter(501, 6.0, "Other").copy(name = "Ch. 6")
        val manga = Manga.create().copy(scanlatorPriority = listOf("Jujucat", "Other"))
        val visible = chapters.filterChapterVisibility(manga, smartScanlatorMergeEnabled = true)
        visible.count { !it.read } shouldBe 107
        visible.any { it.id == 6L } shouldBe true
    }

    @Test
    fun `each chapter comes from the highest priority scanlator that has it`() {
        // Delicious Snake on Mangago, in the user's priority order.
        val priority = listOf("Kaizen", "bloomingdale", "desire scans", "Myth Toons", "Paradise scans", "Unknown")
        var id = 0L
        fun releases(scanlator: String, numbers: List<Double>) =
            numbers.map { chapter(++id, it, scanlator).copy(name = "Ch. $it") }
        val chapters = releases("Paradise scans", (1..9).map(Int::toDouble)) +
            releases("Myth Toons", listOf(1.0, 2.0, 3.0, 4.0, 5.0, 9.0)) +
            releases("Unknown", listOf(1.0)) +
            releases("desire scans", (1..9).map(Int::toDouble)) +
            releases("bloomingdale", listOf(0.5)) +
            releases("Kaizen", (1..5).map(Int::toDouble))
        val merged = chapters.applySmartScanlatorMerge(priority, emptyList(), emptyList())
        merged.associate { it.chapterNumber to it.scanlator } shouldBe mapOf(
            0.5 to "bloomingdale",
            1.0 to "Kaizen", 2.0 to "Kaizen", 3.0 to "Kaizen", 4.0 to "Kaizen", 5.0 to "Kaizen",
            6.0 to "desire scans", 7.0 to "desire scans", 8.0 to "desire scans", 9.0 to "desire scans",
        )
        merged.size shouldBe 10
        // A range rule for 6-8 takes precedence over the order.
        chapters.applySmartScanlatorMerge(
            priority,
            emptyList(),
            listOf(encodeScanlatorRangeRule(ScanlatorRangeRule(6.0, 8.0, "Paradise scans"))),
        ).filter { it.chapterNumber in 6.0..8.0 }.map { it.scanlator }.distinct() shouldBe listOf("Paradise scans")
        // Without Smart Merge every release is visible.
        chapters.filterChapterVisibility(
            Manga.create().copy(scanlatorPriority = priority),
            smartScanlatorMergeEnabled = false,
        ).size shouldBe chapters.size
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
// KNS <--
