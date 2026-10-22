package eu.kanade.tachiyomi.ui.browse.feed

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.SavedSearch

// KNS -->
class LatestFeedResultsTest {

    @Test
    fun `a late old request cannot replace the active feed's new results`() {
        val results = LatestFeedResults()
        val oldConfiguration = configuration(query = "old")
        val newConfiguration = configuration(query = "new")
        val oldRequest = results.start(feedId = 1, oldConfiguration)
        val newRequest = results.start(feedId = 1, newConfiguration)
        var displayed = results.accept(newRequest, newConfiguration, listOf("new result"))

        results.accept(oldRequest, newConfiguration, listOf("stale result"))?.let { displayed = it }

        displayed shouldBe listOf("new result")
    }

    @Test
    fun `successive replacements keep only the newest result without affecting another feed`() {
        val results = LatestFeedResults()
        val firstConfig = configuration(query = "first")
        val secondConfig = configuration(query = "second", filters = "sort=newest;language=en")
        val thirdConfig = configuration(query = "third", filters = "sort=rating;language=ja")
        val otherConfig = configuration(id = 8, query = "other")
        val first = results.start(feedId = 1, firstConfig)
        val otherFeed = results.start(feedId = 2, otherConfig)
        val second = results.start(feedId = 1, secondConfig)
        val third = results.start(feedId = 1, thirdConfig)

        results.accept(first, thirdConfig, "first") shouldBe null
        results.accept(second, thirdConfig, "second") shouldBe null
        results.accept(third, thirdConfig, "third") shouldBe "third"
        results.accept(otherFeed, otherConfig, "other") shouldBe "other"
    }

    @Test
    fun `a result is rejected if the displayed feed configuration changed before another request started`() {
        val results = LatestFeedResults()
        val oldConfiguration = configuration(query = "cats", filters = "sort=popular;rating=all")
        val replacedConfiguration = configuration(query = "dogs", filters = "sort=latest;rating=safe")
        val request = results.start(feedId = 1, oldConfiguration)

        results.accept(request, replacedConfiguration, listOf("cats result")) shouldBe null
    }

    private fun configuration(
        id: Long = 7,
        query: String,
        filters: String? = null,
    ) = FeedRequestConfiguration(
        sourceId = 42,
        savedSearch = SavedSearch(id, 42, "Search", query, filters),
        kind = FeedRequestConfiguration.Kind.SAVED_SEARCH,
    )
}
// KNS <--
