package eu.kanade.tachiyomi.ui.browse.feed

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.source.model.SavedSearch
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

class FeedSearchTest {
    private val filters = FilterList(object : Filter.Text("genre", "default") {})
    private val source = mockk<Source> { every { getFilterList() } returns filters }
    private val saved = SavedSearch(1, 1, "Saved", "", FilterSerializer().serialize(FilterList(object : Filter.Text("genre", "saved") {})).toString())

    @Test
    fun `failure and cancellation restore cached extension filter state`() = runTest {
        coEvery { source.getSearchManga(any(), any(), any()) } coAnswers {
            (thirdArg<FilterList>().single() as Filter.Text).state shouldBe "saved"
            throw CancellationException()
        }
        assertThrows<CancellationException> { FeedSearch.fetch(source, saved) }
        (filters.single() as Filter.Text).state shouldBe "default"
        coEvery { source.getSearchManga(any(), any(), any()) } throws IllegalStateException("HTTP error")
        assertThrows<IllegalStateException> { FeedSearch.fetch(source, saved) }
        (filters.single() as Filter.Text).state shouldBe "default"
    }

    @Test
    fun `invalid or changed saved filters cannot silently issue an unfiltered search`() = runTest {
        for (json in listOf("broken json", FilterSerializer().serialize(FilterList(object : Filter.Text("removed", "selected") {})).toString())) {
            assertThrows<FeedSearch.InvalidSavedSearch> { FeedSearch.fetch(source, saved.copy(filtersJson = json)) }
        }
        coVerify(exactly = 0) { source.getSearchManga(any(), any(), any()) }
        (filters.single() as Filter.Text).state shouldBe "default"
    }

    @Test
    fun `saved search drawer gets independent nested filter states`() {
        val child = object : Filter.CheckBox("genre", false) {}
        val nested = FilterList(object : Filter.Group<Filter<*>>("Group", listOf(child)) {})
        every { source.getFilterList() } returns nested
        val first = FeedSearch.uiFilters(source)
        val second = FeedSearch.uiFilters(source)
        ((first.single() as Filter.Group<*>).state.single() as Filter.CheckBox).state = true
        ((second.single() as Filter.Group<*>).state.single() as Filter.CheckBox).state shouldBe false
        child.state shouldBe false
    }
}
