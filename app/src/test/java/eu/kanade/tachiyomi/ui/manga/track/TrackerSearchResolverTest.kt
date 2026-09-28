package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaUrlResolution
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaUrlResolver
import eu.kanade.tachiyomi.data.track.mangabaka.parseMangaBakaSeriesId
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TrackerSearchResolverTest {
    private val url = "https://mangabaka.org/manhwa/583843/The-Milky-Way"

    @Test
    fun `MangaBaka URL resolves the exact MangaUpdates binding`() = runTest {
        val tracker = tracker(7)
        val expected = mockk<TrackSearch>()
        val resolver = TrackerSearchResolver { _, trackerId ->
            trackerId shouldBe 7L
            MangaBakaUrlResolution.ExternalSourceId("6ol8fx2")
        }
        coEvery { tracker.searchById("6ol8fx2") } returns expected

        resolver.search(tracker, url) shouldBe listOf(expected)
        coVerify { tracker.searchById("6ol8fx2") }
        coVerify(exactly = 0) { tracker.search(any()) }
    }

    @Test
    fun `MangaBaka URL resolves the exact MyAnimeList binding`() = runTest {
        val tracker = tracker(1)
        val expected = mockk<TrackSearch>()
        val resolver = TrackerSearchResolver { _, trackerId ->
            trackerId shouldBe 1L
            MangaBakaUrlResolution.ExternalSourceId("195632")
        }
        coEvery { tracker.searchById("195632") } returns expected

        resolver.search(tracker, url) shouldBe listOf(expected)
        coVerify { tracker.searchById("195632") }
        coVerify(exactly = 0) { tracker.search(any()) }
    }

    @Test
    fun `missing MangaBaka binding is empty while normal text uses regular search`() = runTest {
        val tracker = tracker(7)
        val regular = mockk<TrackSearch>()
        val resolver = TrackerSearchResolver { query, _ ->
            if (query == url) MangaBakaUrlResolution.MissingExternalSource else MangaBakaUrlResolution.NotMangaBakaUrl
        }
        coEvery { tracker.search("Milky Way") } returns listOf(regular)

        resolver.search(tracker, url) shouldBe emptyList()
        resolver.search(tracker, "Milky Way") shouldBe listOf(regular)
        coVerify(exactly = 0) { tracker.searchById(any()) }
    }

    @Test
    fun `MangaBaka URL parsing accepts canonical forms and supplies merged IDs to the shared resolver`() = runTest {
        parseMangaBakaSeriesId(url) shouldBe 583843L
        parseMangaBakaSeriesId("https://www.mangabaka.org/583843") shouldBe 583843L
        parseMangaBakaSeriesId("https://example.org/manhwa/583843/The-Milky-Way") shouldBe null
        var requestedId: Long? = null
        MangaBakaUrlResolver { seriesId, _ ->
            requestedId = seriesId
            "current-binding"
        }.resolve(url, 7) shouldBe MangaBakaUrlResolution.ExternalSourceId("current-binding")
        requestedId shouldBe 583843L
    }

    private fun tracker(id: Long) = mockk<Tracker> {
        every { this@mockk.id } returns id
    }
}
