package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class SearchScreenModelTest {
    @ParameterizedTest
    @EnumSource(value = SourceFilter::class, names = ["PinnedOnly", "Category"])
    fun `search immediately observes new source filter before preference flow catches up`(next: SourceFilter) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val flow = MutableStateFlow(SourceFilter.All)
        val preferences = mockk<SourcePreferences>(relaxed = true) {
            every { enabledLanguages().get() } returns setOf("en")
            every { disabledSources().get() } returns emptySet()
            every { pinnedSources().get() } returns emptySet()
            every { globalSearchPinnedState().changes() } returns flow
            every { globalSearchFilterState().changes() } returns MutableStateFlow(false)
            every { sourcesTabCategories().changes() } returns MutableStateFlow(emptySet())
            every { sourcesTabSourcesInCategories().changes() } returns MutableStateFlow(emptySet())
            every { globalSearchCategoryFilter().changes() } returns MutableStateFlow("")
        }
        val pinnedPreference = preferences.globalSearchPinnedState()
        val observed = mutableListOf<SourceFilter>()
        val key = "search-filter-${UUID.randomUUID()}"
        try {
            val subject = ScreenModelStore.getOrPut(key, null) {
                object : SearchScreenModel(
                    initialState = State(searchQuery = "unchanged query", sourceFilter = SourceFilter.All),
                    sourcePreferences = preferences,
                    sourceManager = mockk(),
                    extensionManager = mockk(),
                    networkToLocalManga = mockk(),
                    getManga = mockk(),
                    preferences = preferences,
                ) {
                    override fun getEnabledSources(): List<Source> {
                        observed += state.value.sourceFilter
                        return emptyList()
                    }
                }
            }
            testScheduler.runCurrent()
            subject.search()
            observed shouldBe listOf(SourceFilter.All)
            observed.clear()

            // Persisting does not emit yet. The repeated query must still search with the new filter.
            subject.setSourceFilter(next)
            flow.value shouldBe SourceFilter.All
            observed shouldBe listOf(next)
            subject.state.value.sourceFilter shouldBe next
            verify(exactly = 1) { pinnedPreference.set(next) }

            flow.value = next
            testScheduler.runCurrent()
            subject.state.value.sourceFilter shouldBe next
            observed shouldBe listOf(next)
        } finally {
            ScreenModelStore.onDisposeNavigator(key)
            Dispatchers.resetMain()
        }
    }
}
