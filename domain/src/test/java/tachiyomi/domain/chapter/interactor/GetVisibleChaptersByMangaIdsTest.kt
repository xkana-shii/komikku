package tachiyomi.domain.chapter.interactor

import exh.source.MERGED_SOURCE_ID
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.visibleChapterStats
import tachiyomi.domain.chapter.service.scanlatorBlacklistKey
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga

// KMK -->
class GetVisibleChaptersByMangaIdsTest {

    private val getChaptersByMangaIds: GetChaptersByMangaIds = mockk()
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId = mockk()
    private var smartMergeEnabled = true
    private val libraryPreferences: LibraryPreferences = mockk {
        every { smartScanlatorMergeEnabled(any()) } answers { smartMergeEnabled }
    }
    private val interactor = GetVisibleChaptersByMangaIds(
        getChaptersByMangaIds,
        getMergedChaptersByMangaId,
        libraryPreferences,
    )

    @Test
    fun `applies blacklist and smart merge once when calculating visible stats`() = runTest {
        val preferred = chapter(id = 1, number = 1.0, scanlator = "Group A", read = true)
        val hiddenDuplicate = chapter(id = 2, number = 1.0, scanlator = "Group B", read = false)
        val blacklisted = chapter(id = 3, number = 2.0, scanlator = "Group A", read = false)
        val manga = Manga.create().copy(
            id = 1,
            source = 1,
            scanlatorPriority = listOf("Group A", "Group B"),
            blacklistedChapters = listOf(scanlatorBlacklistKey(2.0, "Group A")),
        )
        coEvery { getChaptersByMangaIds.await(listOf(1), applyFilter = true) } returns
            listOf(preferred, hiddenDuplicate, blacklisted)

        val visible = interactor.await(listOf(manga)).getValue(manga.id)

        visible.map(Chapter::id) shouldBe listOf(preferred.id)
        visible.visibleChapterStats().total shouldBe 1L
        visible.visibleChapterStats().read shouldBe 1L
    }

    @Test
    fun `changing smart merge restores eligible duplicate without restoring blacklist entries`() = runTest {
        val first = chapter(id = 1, number = 1.0, scanlator = "Group A", read = true)
        val duplicate = chapter(id = 2, number = 1.0, scanlator = "Group B", read = false)
        val blacklisted = chapter(id = 3, number = 2.0, scanlator = "Group A", read = false)
        val manga = Manga.create().copy(
            id = 1,
            source = 1,
            blacklistedChapters = listOf(scanlatorBlacklistKey(2.0, "Group A")),
        )
        coEvery { getChaptersByMangaIds.await(listOf(1), applyFilter = true) } returns
            listOf(first, duplicate, blacklisted)

        interactor.await(listOf(manga)).getValue(manga.id).map(Chapter::id) shouldBe listOf(1L)

        smartMergeEnabled = false

        val visible = interactor.await(listOf(manga)).getValue(manga.id)
        visible.map(Chapter::id) shouldBe listOf(1L, 2L)
        visible.visibleChapterStats().total shouldBe 2L
        visible.visibleChapterStats().read shouldBe 1L
    }

    @Test
    fun `uses merged chapter loading while preserving shared visibility rules`() = runTest {
        val preferred = chapter(id = 1, number = 1.0, scanlator = "Group A", read = true)
        val hiddenDuplicate = chapter(id = 2, number = 1.0, scanlator = "Group B", read = false)
        val manga = Manga.create().copy(
            id = 1,
            source = MERGED_SOURCE_ID,
            scanlatorPriority = listOf("Group A", "Group B"),
        )
        coEvery { getMergedChaptersByMangaId.await(manga.id, applyFilter = true) } returns
            listOf(preferred, hiddenDuplicate)

        interactor.await(listOf(manga)).getValue(manga.id).map(Chapter::id) shouldBe listOf(preferred.id)
    }

    private fun chapter(
        id: Long,
        number: Double,
        scanlator: String,
        read: Boolean,
    ) = Chapter.create().copy(
        id = id,
        mangaId = 1,
        name = "Chapter $id",
        url = "/chapter/$id",
        chapterNumber = number,
        scanlator = scanlator,
        read = read,
    )
}
// KMK <--
