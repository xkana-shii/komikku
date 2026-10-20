// KUN -->
package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.FeedWithSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository
import tachiyomi.domain.source.repository.SavedSearchRepository

class FeedRestorerTest {

    private val feedSavedSearchRepository: FeedSavedSearchRepository = mockk(relaxed = true)
    private val savedSearchRepository: SavedSearchRepository = mockk(relaxed = true)
    private val restorer = FeedRestorer(feedSavedSearchRepository, savedSearchRepository)

    private fun currentFeed(
        source: Long,
        global: Boolean,
        savedSearch: SavedSearch? = null,
    ) = FeedWithSavedSearch(
        feed = FeedSavedSearch(
            id = 1L,
            source = source,
            savedSearch = savedSearch?.id,
            global = global,
            feedOrder = 0,
        ),
        savedSearch = savedSearch,
    )

    private fun savedSearch(id: Long, source: Long, name: String) = SavedSearch(
        id = id,
        source = source,
        name = name,
        query = null,
        filtersJson = null,
    )

    @Test
    fun `global feed is restored when only a saved search feed exists for the source`() = runTest {
        coEvery { feedSavedSearchRepository.getAllWithSavedSearch() } returns listOf(
            currentFeed(source = 1L, global = false, savedSearch = savedSearch(10L, 1L, "Search")),
        )
        coEvery { savedSearchRepository.getAll() } returns emptyList()
        val inserted = slot<FeedSavedSearch>()
        coEvery { feedSavedSearchRepository.insert(capture(inserted)) } returns 1L

        restorer.restoreFeeds(listOf(BackupFeed(source = 1L, global = true)))

        assertEquals(1L, inserted.captured.source)
        assertEquals(true, inserted.captured.global)
        assertEquals(null, inserted.captured.savedSearch)
    }

    @Test
    fun `global feed is not restored twice`() = runTest {
        coEvery { feedSavedSearchRepository.getAllWithSavedSearch() } returns listOf(
            currentFeed(source = 1L, global = true),
        )

        restorer.restoreFeeds(listOf(BackupFeed(source = 1L, global = true)))

        coVerify(exactly = 0) { feedSavedSearchRepository.insert(any()) }
    }

    @Test
    fun `saved search feed is restored when a global feed already exists`() = runTest {
        coEvery { feedSavedSearchRepository.getAllWithSavedSearch() } returns listOf(
            currentFeed(source = 1L, global = true),
        )
        coEvery { savedSearchRepository.getAll() } returns emptyList()
        coEvery { savedSearchRepository.insert(any()) } returns 10L
        val inserted = slot<FeedSavedSearch>()
        coEvery { feedSavedSearchRepository.insert(capture(inserted)) } returns 1L

        restorer.restoreFeeds(
            listOf(
                BackupFeed(
                    source = 1L,
                    global = false,
                    savedSearch = BackupSavedSearch(
                        name = "Search",
                        query = "",
                        filterList = "[]",
                        source = 1L,
                    ),
                ),
            ),
        )

        assertEquals(1L, inserted.captured.source)
        assertEquals(false, inserted.captured.global)
        assertEquals(10L, inserted.captured.savedSearch)
    }

    @Test
    fun `existing saved search feed is not restored twice`() = runTest {
        coEvery { feedSavedSearchRepository.getAllWithSavedSearch() } returns listOf(
            currentFeed(source = 1L, global = true, savedSearch = savedSearch(10L, 1L, "Search")),
        )

        restorer.restoreFeeds(
            listOf(
                BackupFeed(
                    source = 1L,
                    global = true,
                    savedSearch = BackupSavedSearch(
                        name = "Search",
                        query = "",
                        filterList = "[]",
                        source = 1L,
                    ),
                ),
            ),
        )

        coVerify(exactly = 0) { feedSavedSearchRepository.insert(any()) }
    }
}
// KUN <--
