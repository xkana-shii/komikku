package eu.kanade.tachiyomi.ui.browse.feed

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import keiyoushi.source.KeiSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.source.model.SavedSearch
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

class SourceFeedFiltersTest {
    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class Genres(children: List<Genre>) : Filter.Group<Genre>("Genres", children)
    private class Sort : Filter.Sort("Sort", arrayOf("Popular", "Latest"), Selection(0, false))
    private fun filters() = FilterList(Sort(), Genres(listOf(Genre("Action", "action-id"), Genre("Yaoi", "yaoi-id"))))
    private fun saved(): SavedSearch {
        val filters = filters()
        (filters[0] as Sort).state = Filter.Sort.Selection(1, false)
        (filters[1] as Genres).state[1].state = Filter.TriState.STATE_INCLUDE
        return SavedSearch(1, 1, "Yaoi Latest", "", FilterSerializer().serialize(filters).toString())
    }

    @Test
    fun `cold metadata must finish before first search and custom subclasses keep IDs and sorting`() = runTest {
        val delegate = mockk<Source>()
        val ready = CompletableDeferred<Unit>()
        val cached = filters()
        val source = object : KeiSource(delegate) {
            override val supportsFilterFetching = true
            override suspend fun fetchFilterData(): JsonElement {
                ready.await()
                return JsonNull
            }
            override fun getFilterList(data: JsonElement?) = if (data == null) FilterList(Sort()) else cached
        }
        coEvery { delegate.getSearchManga(1, "", any()) } coAnswers {
            val supplied = thirdArg<FilterList>()
            (supplied[0] as Sort).state shouldBe Filter.Sort.Selection(1, false)
            (supplied[1] as Genres).state.filter { it.isIncluded() }.map { it.id } shouldBe listOf("yaoi-id")
            (cached[1] as Genres).state.none { it.isIncluded() } shouldBe true
            MangasPage(emptyList(), false)
        }
        val request = async { FeedSearch.fetch(source, saved()) }
        testScheduler.runCurrent()
        coVerify(exactly = 0) { delegate.getSearchManga(any(), any(), any()) }
        ready.complete(Unit)
        request.await()
        coVerify(exactly = 1) { delegate.getSearchManga(any(), any(), any()) }
    }

    @Test
    fun `metadata Cloudflare failure surfaces and exactly one retry issues the intended search`() = runTest {
        val delegate = mockk<Source>()
        var calls = 0
        val source = object : KeiSource(delegate) {
            override val supportsFilterFetching = true
            override suspend fun fetchFilterData(): JsonElement {
                if (++calls == 1) error("Cloudflare")
                return JsonNull
            }
            override fun getFilterList(data: JsonElement?) = if (data == null) FilterList(Sort()) else filters()
        }
        coEvery { delegate.getSearchManga(1, "", any()) } coAnswers {
            val supplied = thirdArg<FilterList>()
            (supplied[0] as Sort).state?.index shouldBe 1
            (supplied[1] as Genres).state.single { it.isIncluded() }.id shouldBe "yaoi-id"
            MangasPage(emptyList(), false)
        }
        assertThrows<IllegalStateException> { FeedSearch.fetch(source, saved()) }.message shouldBe "Cloudflare"
        coVerify(exactly = 0) { delegate.getSearchManga(any(), any(), any()) }
        FeedSearch.fetch(source, saved())
        calls shouldBe 2
        coVerify(exactly = 1) { delegate.getSearchManga(any(), any(), any()) }
    }

    @Test
    fun `metadata genre order cannot move Yaoi state onto Action`() = runTest {
        val delegate = mockk<Source>()
        val source = object : KeiSource(delegate) {
            override val supportsFilterFetching = true
            override suspend fun fetchFilterData(): JsonElement = JsonNull
            override fun getFilterList(data: JsonElement?) = FilterList(Sort(), Genres(listOf(Genre("Yaoi", "yaoi-id"), Genre("Action", "action-id"))))
        }
        coEvery { delegate.getSearchManga(1, "", any()) } coAnswers {
            (thirdArg<FilterList>()[1] as Genres).state.single { it.isIncluded() }.id shouldBe "yaoi-id"
            MangasPage(emptyList(), false)
        }
        FeedSearch.fetch(source, saved())
    }

    @Test
    fun `cancellation while loading metadata never starts a search`() = runTest {
        val delegate = mockk<Source>()
        val ready = CompletableDeferred<Unit>()
        val source = object : KeiSource(delegate) {
            override val supportsFilterFetching = true
            override suspend fun fetchFilterData(): JsonElement {
                ready.await()
                return JsonNull
            }
        }
        val request = async { FeedSearch.fetch(source, saved()) }
        testScheduler.runCurrent()
        request.cancel()
        request.join()
        coVerify(exactly = 0) { delegate.getSearchManga(any(), any(), any()) }
    }
}
