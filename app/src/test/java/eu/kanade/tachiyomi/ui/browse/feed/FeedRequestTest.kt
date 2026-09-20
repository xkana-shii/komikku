package eu.kanade.tachiyomi.ui.browse.feed

import eu.kanade.presentation.browse.SourceFeedUI
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.manga.model.Manga

class FeedRequestTest {
    @Test
    fun `source feed keeps stale cards with an error and clears errors on success`() {
        val cards = listOf(Manga.create())
        listOf(SourceFeedUI.Latest(cards), SourceFeedUI.Browse(cards)).forEach { row ->
            val failed = row.withResults(row.results, "HTTP error 403")
            failed.results shouldBe cards
            failed.error shouldBe "HTTP error 403"
            failed.loading shouldBe false
            val retried = failed.withResults(emptyList())
            retried.error shouldBe null
            retried.results shouldBe emptyList()
        }
    }

    @Test
    fun `empty success is distinct from failure and retry can recover`() = runTest {
        feedRequest(onError = { listOf("stale") }) { emptyList<String>() } shouldBe emptyList()
        feedRequest(onError = { listOf("stale") }) { error("source failed") } shouldBe listOf("stale")
        feedRequest(onError = { listOf("stale") }) { listOf("fresh") } shouldBe listOf("fresh")
    }

    @Test
    fun `cancellation never becomes a feed error`() = runTest {
        assertThrows<CancellationException> {
            feedRequest(onError = { error("must not handle cancellation") }) { throw CancellationException() }
        }
    }
}
