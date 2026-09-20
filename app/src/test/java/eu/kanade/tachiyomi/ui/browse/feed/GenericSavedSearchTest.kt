package eu.kanade.tachiyomi.ui.browse.feed

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.source.model.SavedSearch
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

class GenericSavedSearchTest {
    private class Genre(name: String) : Filter.TriState(name)
    private class Genres(children: List<Genre>) : Filter.Group<Genre>("Genres", children)
    private class Sort(options: Array<String> = arrayOf("Popular", "Latest")) : Filter.Select<String>("Sort", options)
    private val original = FilterList(Filter.Header("Current help"), Sort(), Genres(listOf(Genre("Action"), Genre("Yaoi"))))
    private val source = mockk<Source> { every { getFilterList() } returns original }
    private fun search(filters: FilterList) = SavedSearch(31, 99, "Saved", "query", Json.encodeToString(FilterSerializer().serialize(filters)))

    @Test
    fun `removed unused genre is compatible but a removed selected genre is not silently lost`() = runTest {
        val old = Genres(listOf(Genre("Removed"), Genre("Yaoi").apply { state = 1 }))
        every { source.getFilterList() } returns FilterList(Genres(listOf(Genre("Yaoi"))))
        coEvery { source.getSearchManga(1, "query", any()) } coAnswers {
            (thirdArg<FilterList>().single() as Genres).state.single().isIncluded() shouldBe true
            MangasPage(emptyList(), false)
        }
        FeedSearch.fetch(source, search(FilterList(old)))
        old.state.first().state = 1
        assertThrows<FeedSearch.InvalidSavedSearch> { FeedSearch.fetch(source, search(FilterList(old))) }
        coVerify(exactly = 1) { source.getSearchManga(any(), any(), any()) }
    }

    @Test
    fun `query only null empty and blank filter encodings execute without requesting a schema`() = runTest {
        coEvery { source.getSearchManga(1, "query", any()) } coAnswers {
            thirdArg<FilterList>().size shouldBe 0
            MangasPage(emptyList(), false)
        }
        for (encoded in listOf(null, "[]", "", "  ")) FeedSearch.fetch(source, search(FilterList()).copy(filtersJson = encoded))
        coVerify(exactly = 4) { source.getSearchManga(1, "query", any()) }
        io.mockk.verify(exactly = 0) { source.getFilterList() }
    }

    @Test
    fun `real save serializer restores static and default filters`() = runTest {
        coEvery { source.getSearchManga(1, "query", any()) } coAnswers { MangasPage(emptyList(), false) }
        val saved = search(original)
        FeedSearch.fetch(source, saved)
        (original[1] as Sort).state = 1
        val selected = search(original)
        (original[1] as Sort).state = 0
        coEvery { source.getSearchManga(1, "query", any()) } coAnswers {
            (thirdArg<FilterList>()[1] as Sort).state shouldBe 1
            MangasPage(emptyList(), false)
        }
        FeedSearch.fetch(source, selected)
        (original[1] as Sort).state shouldBe 0
    }

    @Test
    fun `decorative headers removed hints and new default filters do not invalidate saved selections`() = runTest {
        val previous = FilterList(Sort().apply { state = 1 }, Filter.Separator(), Genres(listOf(Genre("Yaoi").apply { state = 1 }, Genre("Action"))), Filter.Header("Tap Reset to load filters"))
        every { source.getFilterList() } returns FilterList(Filter.Header("New help"), Filter.Separator(), original[2], Sort(arrayOf("Title", "Latest", "Popular")), object : Filter.CheckBox("New optional filter") {})
        coEvery { source.getSearchManga(1, "query", any()) } coAnswers {
            val filters = thirdArg<FilterList>()
            (filters[2] as Genres).state.single { it.isIncluded() }.name shouldBe "Yaoi"
            (filters[3] as Sort).state shouldBe 1
            (filters[4] as Filter.CheckBox).state shouldBe false
            MangasPage(emptyList(), false)
        }
        FeedSearch.fetch(source, search(previous))
        coVerify(exactly = 1) { source.getSearchManga(any(), any(), any()) }
    }

    @Test
    fun `option labels restore a changed index instead of applying another option`() = runTest {
        every { source.getFilterList() } returns FilterList(Sort(arrayOf("Latest", "Popular")))
        coEvery { source.getSearchManga(1, "query", any()) } coAnswers {
            (thirdArg<FilterList>().single() as Sort).state shouldBe 0
            MangasPage(emptyList(), false)
        }
        FeedSearch.fetch(source, search(FilterList(Sort().apply { state = 1 })))
    }
}
