package eu.kanade.tachiyomi.ui.browse.feed

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class LatestFeedResultsTest {

    @Test
    fun `a late old request cannot replace the active feed's new results`() {
        val results = LatestFeedResults()
        val oldRequest = results.start(feedId = 1)
        val newRequest = results.start(feedId = 1)
        var displayed = results.accept(newRequest, listOf("new result"))

        results.accept(oldRequest, listOf("stale result"))?.let { displayed = it }

        displayed shouldBe listOf("new result")
    }

    @Test
    fun `successive replacements keep only the newest result without affecting another feed`() {
        val results = LatestFeedResults()
        val first = results.start(feedId = 1)
        val otherFeed = results.start(feedId = 2)
        val second = results.start(feedId = 1)
        val third = results.start(feedId = 1)

        results.accept(first, "first") shouldBe null
        results.accept(second, "second") shouldBe null
        results.accept(third, "third") shouldBe "third"
        results.accept(otherFeed, "other") shouldBe "other"
    }
}
// KNS <--
