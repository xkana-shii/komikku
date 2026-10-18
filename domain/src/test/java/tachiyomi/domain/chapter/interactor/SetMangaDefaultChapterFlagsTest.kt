package tachiyomi.domain.chapter.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaDefaultChapterFlagsTest {

    private fun preference(value: Long): Preference<Long> = mockk {
        every { get() } returns value
    }

    private val unread = preference(Manga.CHAPTER_SHOW_READ)
    private val downloaded = preference(Manga.CHAPTER_SHOW_NOT_DOWNLOADED)
    private val bookmarked = preference(Manga.CHAPTER_SHOW_BOOKMARKED)
    private val sorting = preference(Manga.CHAPTER_SORTING_UPLOAD_DATE)
    private val direction = preference(Manga.CHAPTER_SORT_ASC)
    private val display = preference(Manga.CHAPTER_DISPLAY_NUMBER)

    private val libraryPreferences = mockk<LibraryPreferences> {
        every { filterChapterByRead } returns unread
        every { filterChapterByDownloaded } returns downloaded
        every { filterChapterByBookmarked } returns bookmarked
        every { sortChapterBySourceOrNumber } returns sorting
        every { sortChapterByAscendingOrDescending } returns direction
        every { displayChapterByNameOrNumber } returns display
    }

    private val repository = mockk<MangaRepository>()
    private val getFavorites = mockk<GetFavorites>()
    private val flags = SetMangaChapterFlags(repository)

    private val subject = SetMangaDefaultChapterFlags(
        libraryPreferences,
        flags,
        getFavorites,
    )

    private val expectedFlags = Manga.CHAPTER_SHOW_READ or
        Manga.CHAPTER_SHOW_NOT_DOWNLOADED or
        Manga.CHAPTER_SHOW_BOOKMARKED or
        Manga.CHAPTER_SORTING_UPLOAD_DATE or
        Manga.CHAPTER_SORT_ASC or
        Manga.CHAPTER_DISPLAY_NUMBER

    @Test
    fun `await updates only the target manga`() = runTest {
        val captured = slot<List<MangaUpdate>>()

        coEvery {
            repository.updateAll(capture(captured))
        } returns true

        subject.await(Manga.create().copy(id = 77L))

        captured.captured.size shouldBe 1
        captured.captured.single().id shouldBe 77L
        captured.captured.single().chapterFlags shouldBe expectedFlags

        coVerify(exactly = 1) {
            repository.updateAll(any())
        }

        coVerify(exactly = 0) {
            getFavorites.await()
        }
    }

    @Test
    fun `awaitAll updates only library favorites in one bulk operation`() = runTest {
        val captured = slot<List<MangaUpdate>>()

        coEvery {
            getFavorites.await()
        } returns listOf(
            Manga.create().copy(id = 10L),
            Manga.create().copy(id = 20L),
        )

        coEvery {
            repository.updateAll(capture(captured))
        } returns true

        subject.awaitAll()

        captured.captured.map { it.id } shouldBe listOf(10L, 20L)
        captured.captured.map { it.chapterFlags } shouldBe
            listOf(expectedFlags, expectedFlags)

        coVerify(exactly = 1) {
            getFavorites.await()
        }

        coVerify(exactly = 1) {
            repository.updateAll(any())
        }
    }

    @Test
    fun `each stored chapter preference is read once`() = runTest {
        coEvery {
            getFavorites.await()
        } returns listOf(Manga.create().copy(id = 10L))

        coEvery {
            repository.updateAll(any())
        } returns true

        subject.awaitAll()

        verify(exactly = 1) { unread.get() }
        verify(exactly = 1) { downloaded.get() }
        verify(exactly = 1) { bookmarked.get() }
        verify(exactly = 1) { sorting.get() }
        verify(exactly = 1) { direction.get() }
        verify(exactly = 1) { display.get() }
    }

    @Test
    fun `empty library produces no manga updates`() = runTest {
        val captured = slot<List<MangaUpdate>>()

        coEvery {
            getFavorites.await()
        } returns emptyList()

        coEvery {
            repository.updateAll(capture(captured))
        } returns true

        subject.awaitAll()

        captured.captured shouldBe emptyList<MangaUpdate>()
    }
}
