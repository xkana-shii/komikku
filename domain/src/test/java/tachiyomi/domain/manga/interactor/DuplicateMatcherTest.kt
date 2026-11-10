package tachiyomi.domain.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.DuplicateMatcher.MatchLevel
import tachiyomi.domain.manga.model.DuplicateKey
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository

// KNS -->
class DuplicateMatcherTest {

    private fun key(id: Long, title: String, author: String? = null, vararg extraTitles: String) =
        DuplicateKey(id, listOf(title) + extraTitles, listOfNotNull(author))

    private fun level(title: String, author: String?, candidate: DuplicateKey) =
        DuplicateMatcher.matchLevel(listOf(title), listOfNotNull(author), candidate)

    @Test
    fun `normalizing ignores case, punctuation, spacing and accents`() {
        DuplicateMatcher.normalize("Mr.A's  Farm") shouldBe DuplicateMatcher.normalize("MR A’S FARM")
        DuplicateMatcher.normalize("Café Ünder") shouldBe "cafeunder"
    }

    @Test
    fun `identical titles match whatever the punctuation, even by different authors`() {
        level("Mr.A's Farm", null, key(1, "Mr A's Farm")) shouldBe MatchLevel.EXACT_TITLE
        level("Mr.A's Farm", "Nachioboy", key(1, "Mr.A's Farm", "Someone Else")) shouldBe MatchLevel.EXACT_TITLE
        level("Mr.A's Farm", "Nachioboy", key(1, "Mr.A's Farm", "nachioboy")) shouldBe
            MatchLevel.EXACT_TITLE_AND_AUTHOR
    }

    @Test
    fun `a title that is only part of another needs agreeing or unknown authors`() {
        level("Wintertime", null, key(1, "Wintertime Rain")) shouldBe MatchLevel.SIMILAR_TITLE
        level("Wintertime", "A", key(1, "Wintertime Rain")) shouldBe MatchLevel.SIMILAR_TITLE
        level("Wintertime", "A", key(1, "Wintertime Rain", "A")) shouldBe MatchLevel.SIMILAR_TITLE_AND_AUTHOR
        level("Wintertime", "A", key(1, "Wintertime Rain", "B")) shouldBe MatchLevel.NONE
    }

    @Test
    fun `author fields with several names agree on any shared name`() {
        level("Love", "A, B & C", key(1, "Love Stage", "C")) shouldBe MatchLevel.SIMILAR_TITLE_AND_AUTHOR
    }

    @Test
    fun `any of the known titles of an entry can match`() {
        level("Original Name", null, key(1, "Custom Name", null, "Original Name")) shouldBe MatchLevel.EXACT_TITLE
    }

    @Test
    fun `blank and very short titles do not match everything`() {
        level("", null, key(1, "Anything")) shouldBe MatchLevel.NONE
        level("  ", null, key(1, "Anything")) shouldBe MatchLevel.NONE
        level("A!", null, key(1, "Alpha")) shouldBe MatchLevel.NONE
        level("Unrelated", null, key(1, "Alpha")) shouldBe MatchLevel.NONE
    }

    // ---- interactor

    private val repository = mockk<MangaRepository>()
    private val interactor = GetDuplicateLibraryManga(repository)

    private fun entry(id: Long, title: String, chapters: Long) =
        MangaWithChapterCount(Manga.create().copy(id = id, ogTitle = title, favorite = true), chapters)

    @Test
    fun `candidates are best match first, then closest chapter count, without repeats`() = runTest {
        val incoming = Manga.create().copy(id = 100, ogTitle = "Mr.A's Farm", ogAuthor = "Nachioboy")
        coEvery { repository.getTrackDuplicateMangaIds(100) } returns listOf(4L)
        coEvery { repository.getDuplicateCandidateKeys(100) } returns listOf(
            key(1, "Mr.A's Farm", "Nachioboy"),
            key(2, "Mr A's Farm", "Other"),
            key(3, "Mr.A's Farm", "Nachioboy"),
            key(4, "Completely different"),
            key(5, "Unrelated"),
        )
        coEvery { repository.getLibraryMangaWithChapterCount(any()) } answers {
            val ids = firstArg<Collection<Long>>()
            // the same entry twice must be shown once
            (ids + ids.first()).map { entry(it, "t$it", it * 10) }
        }

        val result = interactor(incoming, incomingChapterCount = 28)

        // tracker match, then exact title + author (3 has 30 chapters, closer to 28 than 1's 10), then exact title
        result.map { it.manga.id } shouldBe listOf(4L, 3L, 1L, 2L)
        coVerify { repository.getLibraryMangaWithChapterCount(setOf(1L, 2L, 3L, 4L)) }
    }

    @Test
    fun `nothing is loaded when no entry matches`() = runTest {
        val incoming = Manga.create().copy(id = 100, ogTitle = "Brand New")
        coEvery { repository.getTrackDuplicateMangaIds(100) } returns emptyList()
        coEvery { repository.getDuplicateCandidateKeys(100) } returns listOf(key(1, "Other series"))

        interactor(incoming) shouldBe emptyList()
        coVerify(exactly = 0) { repository.getLibraryMangaWithChapterCount(any()) }
    }

    @Test
    fun `fresh details change the outcome`() = runTest {
        coEvery { repository.getTrackDuplicateMangaIds(100) } returns emptyList()
        coEvery { repository.getDuplicateCandidateKeys(100) } returns listOf(key(1, "Wintertime Rain", "B"))
        coEvery { repository.getLibraryMangaWithChapterCount(any()) } returns listOf(entry(1, "Wintertime Rain", 5))
        val listed = Manga.create().copy(id = 100, ogTitle = "Wintertime")
        val detailed = listed.copy(ogAuthor = "A")

        interactor(listed).size shouldBe 1
        // the loaded author shows it is by someone else
        interactor(detailed).size shouldBe 0
    }

    @Test
    fun `the fast normalization matches the previous regex one`() {
        fun reference(text: String) = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFKD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), "")
        listOf(
            "Mr.A's Farm", "  Café  Ⅻ ", "ＦＵＬＬＷＩＤＴＨ ２", "Pokémon: Żółć!", "鬼の花嫁 1", "크렘노스 부흥기",
            "İstanbul", "ﬁnal ½", "Ça va? — oui", "",
        ).forEach { DuplicateMatcher.normalize(it) shouldBe reference(it) }
    }

    @Test
    fun `a bulk run loads the library once and never matches an entry with itself`() = runTest {
        coEvery { repository.getDuplicateCandidateKeys(-1) } returns
            listOf(key(1, "Alpha Story"), key(2, "Beta Story"), key(100, "Alpha Story"))
        coEvery { repository.getTrackDuplicateMangaIds(any()) } returns emptyList()
        coEvery { repository.getLibraryMangaWithChapterCount(any()) } answers {
            firstArg<Collection<Long>>().map { entry(it, "t$it", 1) }
        }
        val library = interactor.snapshot()

        interactor(Manga.create().copy(id = 100, ogTitle = "Alpha Story"), library = library)
            .map { it.manga.id } shouldBe listOf(1L)
        interactor(Manga.create().copy(id = 101, ogTitle = "Beta Story"), library = library)
            .map { it.manga.id } shouldBe listOf(2L)
        coVerify(exactly = 1) { repository.getDuplicateCandidateKeys(any()) }
    }
}
// KNS <--
