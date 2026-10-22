package tachiyomi.domain.source.interactor

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch

// KNS -->
class ObserveSavedSearchesTest {

    private val feed = FeedSavedSearch(id = 10, source = 100, savedSearch = 1, global = true, feedOrder = 0)
    private val original = SavedSearch(1, 100, "Saved", "old", "old filters and sort")

    @Test
    fun `an active feed receives every replacement while unrelated searches do not refresh it`() = runTest {
        val feeds = MutableStateFlow(listOf(feed))
        val searches = MutableStateFlow(listOf(original))
        val emissions = mutableListOf<List<ResolvedFeedSavedSearch>>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveSavedSearches.resolve(feeds, searches).collect(emissions::add)
        }

        runCurrent()
        emissions shouldHaveSize 1

        searches.value = searches.value + SavedSearch(2, 200, "Unrelated", "other", null)
        runCurrent()
        emissions shouldHaveSize 1

        searches.value = listOf(original.copy(query = "new term", filtersJson = "new filters and sort"))
        runCurrent()
        emissions.last().single().savedSearch shouldBe
            original.copy(query = "new term", filtersJson = "new filters and sort")

        searches.value = listOf(original.copy(query = "newest term", filtersJson = "source-specific options"))
        runCurrent()
        emissions.last().single().savedSearch shouldBe
            original.copy(query = "newest term", filtersJson = "source-specific options")
        emissions shouldHaveSize 3
        collector.cancel()
    }

    @Test
    fun `one replaced search updates every feed that references it and a later collector sees it`() = runTest {
        val secondFeed = feed.copy(id = 11, global = false, feedOrder = 1)
        val feeds = MutableStateFlow(listOf(feed, secondFeed))
        val searches = MutableStateFlow(listOf(original))
        val emissions = mutableListOf<List<ResolvedFeedSavedSearch>>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveSavedSearches.resolve(feeds, searches).collect(emissions::add)
        }

        searches.value = listOf(original.copy(query = "replacement"))
        runCurrent()
        emissions.last().map { it.savedSearch?.query } shouldBe listOf("replacement", "replacement")
        collector.cancel()

        val reopened = mutableListOf<List<ResolvedFeedSavedSearch>>()
        val reopenedCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveSavedSearches.resolve(feeds, searches).collect(reopened::add)
        }
        runCurrent()
        reopened.single().map { it.savedSearch?.query } shouldBe listOf("replacement", "replacement")
        reopenedCollector.cancel()
    }
}
// KNS <--
