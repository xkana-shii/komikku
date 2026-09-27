package tachiyomi.domain.chapter.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaDefaultChapterFlagsTest {
    private val values = mutableMapOf<String, Long>()
    private val preferences = mutableMapOf<String, Preference<Long>>()
    private val store = mockk<PreferenceStore> {
        every { getLong(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Long>()
            preferences.getOrPut(key) {
                mockk {
                    every { get() } answers { values[key] ?: default }
                    every { set(any()) } answers { values[key] = firstArg() }
                }
            }
        }
    }
    private val libraryPreferences = LibraryPreferences(store)
    private val repository = mockk<MangaRepository> {
        coEvery { update(any()) } returns true
        coEvery { updateLibraryChapterFlags(any()) } returns true
    }
    private val flags = SetMangaChapterFlags(repository)
    private val subject = SetMangaDefaultChapterFlags(libraryPreferences, flags, repository)
    private val expected = Manga.CHAPTER_SHOW_READ or Manga.CHAPTER_SHOW_NOT_DOWNLOADED or
        Manga.CHAPTER_SHOW_BOOKMARKED or Manga.CHAPTER_SHOW_NOT_FILLERMARKED or
        Manga.CHAPTER_SORTING_UPLOAD_DATE or Manga.CHAPTER_SORT_ASC or Manga.CHAPTER_DISPLAY_NUMBER
    private val manga = Manga.create().copy(id = 42, chapterFlags = expected)

    @Test
    fun `await updates only the target manga`() = runTest {
        libraryPreferences.setChapterSettingsDefault(manga)
        subject.await(Manga.create().copy(id = 77, chapterFlags = -1))
        coVerify(exactly = 1) { repository.update(MangaUpdate(id = 77, chapterFlags = expected)) }
        confirmVerified(repository)
    }

    @Test
    fun `awaitAll reads each preference once and makes only one bulk call without loading manga`() = runTest {
        libraryPreferences.setChapterSettingsDefault(manga)
        subject.awaitAll()
        preferences.size shouldBe 7
        preferences.values.forEach { preference -> verify(exactly = 1) { preference.get() } }
        coVerify(exactly = 1) { repository.updateLibraryChapterFlags(expected) }
        // Also rejects getFavorites, getAll, update, and any other per-manga work.
        confirmVerified(repository)
    }

    @Test
    fun `set as default with applyToExisting false stores settings without updating existing manga`() = runTest {
        subject.setAsDefault(manga, applyToExisting = false)
        values.size shouldBe 7
        libraryPreferences.filterChapterByRead().get() shouldBe manga.unreadFilterRaw
        libraryPreferences.filterChapterByDownloaded().get() shouldBe manga.downloadedFilterRaw
        libraryPreferences.filterChapterByBookmarked().get() shouldBe manga.bookmarkedFilterRaw
        libraryPreferences.filterChapterByFillermarked().get() shouldBe manga.fillermarkedFilterRaw
        libraryPreferences.sortChapterBySourceOrNumber().get() shouldBe manga.sorting
        libraryPreferences.sortChapterByAscendingOrDescending().get() shouldBe Manga.CHAPTER_SORT_ASC
        libraryPreferences.displayChapterByNameOrNumber().get() shouldBe manga.displayMode
        confirmVerified(repository)
    }

    @Test
    fun `set as default with applyToExisting true applies the newly stored settings once`() = runTest {
        subject.setAsDefault(manga, applyToExisting = true)
        coVerify(exactly = 1) { repository.updateLibraryChapterFlags(expected) }
        confirmVerified(repository)
    }
}
