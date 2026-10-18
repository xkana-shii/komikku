package tachiyomi.domain.chapter.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

// KMK -->
class ChapterBlacklistTest {
    @Test
    fun `scanlator key matches its normalized chapter identity`() {
        val chapter = chapter(number = 12.345, scanlator = "  Team  ")

        filterBlacklistedChapters(listOf(chapter), listOf(scanlatorBlacklistKey(12.345, "Team"))) shouldBe emptyList()
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

    @Test
    fun `Mangago extra blacklist does not hide its matching numbered chapter`() {
        val twitterExtra = chapter(url = "/the_melting_season/twitter-extra", number = 1.0, scanlator = null)
        val chapterOne = chapter(url = "/the_melting_season/chapter-1", number = 1.0, scanlator = null).copy(id = 2)
        val seasonComeback = chapter(url = "/the_melting_season/season-comeback-6-15", number = 6.0, scanlator = null).copy(id = 3)
        val chapterSix = chapter(url = "/the_melting_season/chapter-6", number = 6.0, scanlator = null).copy(id = 4)
        val chapters = listOf(twitterExtra, chapterOne, seasonComeback, chapterSix)

        filterBlacklistedChapters(
            chapters,
            listOf(hybridChapterBlacklistKey(twitterExtra), hybridChapterBlacklistKey(seasonComeback)),
        ).map(Chapter::url) shouldBe listOf(chapterOne.url, chapterSix.url)
    }

    @Test
    fun `ambiguous legacy key does not hide multiple Mangago chapters`() {
        val extra = chapter(url = "/the_melting_season/twitter-extra", number = 1.0, scanlator = null)
        val chapterOne = chapter(url = "/the_melting_season/chapter-1", number = 1.0, scanlator = null).copy(id = 2)
        val legacyKey = scanlatorBlacklistKey(extra.chapterNumber, extra.scanlator)

        filterBlacklistedChapters(listOf(extra, chapterOne), listOf(legacyKey)) shouldBe listOf(extra, chapterOne)
        isChapterBlacklisted(extra, listOf(legacyKey)) shouldBe false
    }

    @Test
    fun `refreshed Mangago extras use their names without hiding numbered chapters`() {
        val oldTwitterExtra = chapter(
            url = "/the_melting_season/twitter-extra-old",
            name = "twitter extra 1st anniversary",
            number = 1.0,
            scanlator = null,
        )
        val oldSeasonComeback = chapter(
            url = "/the_melting_season/season-comeback-old",
            name = "season comeback 6/15",
            number = 6.0,
            scanlator = null,
        ).copy(id = 2)
        val twitterExtra = oldTwitterExtra.copy(url = "/the_melting_season/twitter-extra-new")
        val chapterOne = chapter(
            url = "/the_melting_season/chapter-1",
            name = "Chapter 1",
            number = 1.0,
            scanlator = null,
        ).copy(id = 3)
        val seasonComeback = oldSeasonComeback.copy(url = "/the_melting_season/season-comeback-new")
        val chapterSix = chapter(
            url = "/the_melting_season/chapter-6",
            name = "Chapter 6",
            number = 6.0,
            scanlator = null,
        ).copy(id = 4)
        val refreshedChapters = listOf(twitterExtra, chapterOne, seasonComeback, chapterSix)

        filterBlacklistedChapters(
            refreshedChapters,
            listOf(hybridChapterBlacklistKey(oldTwitterExtra), hybridChapterBlacklistKey(oldSeasonComeback)),
            refreshedChapters,
        ).map(Chapter::url) shouldBe listOf(chapterOne.url, chapterSix.url)
    }

    @Test
    fun `legacy fallback uses the complete snapshot rather than a filtered subset`() {
        val extra = chapter(url = "/extra", name = "Twitter extra", number = 1.0, scanlator = null)
        val chapterOne = chapter(url = "/chapter-1", name = "Chapter 1", number = 1.0, scanlator = null).copy(id = 2)
        val legacyKey = scanlatorBlacklistKey(1.0, null)

        filterBlacklistedChapters(
            chapters = listOf(extra),
            blacklist = listOf(legacyKey),
            allChapters = listOf(extra, chapterOne),
        ) shouldBe listOf(extra)
    }

    @Test
    fun `v1 hybrid keys remain compatible after the name discriminator is added`() {
        val refreshed = chapter(url = "/new", name = "Chapter 8", number = 8.0, scanlator = "Team")

        filterBlacklistedChapters(
            listOf(refreshed),
            listOf("hybrid:8.0|team|%2Fold"),
            listOf(refreshed),
        ) shouldBe emptyList()
    }

    private fun chapter(
        url: String = "/chapter",
        name: String = url,
        number: Double = 1.0,
        scanlator: String? = "Group",
    ) = Chapter.create().copy(
        id = 1,
        mangaId = 1,
        url = url,
        name = name,
        chapterNumber = number,
        scanlator = scanlator,
    )
}
// KMK <--
