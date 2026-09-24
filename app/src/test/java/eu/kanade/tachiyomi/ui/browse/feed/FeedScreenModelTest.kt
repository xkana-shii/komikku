package eu.kanade.tachiyomi.ui.browse.feed

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchGlobalFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.service.SourceManager

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class FeedScreenModelTest {
    private val sources = (1L..2L).map { sourceId ->
        mockk<Source> {
            every { id } returns sourceId
            every { name } returns "Source $sourceId"
            every { lang } returns "en"
            every { supportsLatest } returns true
            coEvery { getLatestUpdates(1) } returns MangasPage(emptyList(), false)
        }
    }
    private val manager = mockk<SourceManager> {
        every { isInitialized } returns MutableStateFlow(true)
        this@FeedScreenModelTest.sources.forEachIndexed { index, source -> every { get((index + 1).toLong()) } returns source }
    }
    private val preferences = mockk<SourcePreferences>(relaxed = true) {
        every { hideInLibraryFeedItems().get() } returns false
    }
    private val configured = mockk<GetFeedSavedSearchGlobal> {
        every { subscribe() } returns MutableStateFlow(sources.map { FeedSavedSearch(it.id, it.id, null, true, it.id) })
    }
    private val searches = mockk<GetSavedSearchGlobalFeed> { coEvery { await() } returns emptyList() }
    private val network = mockk<NetworkToLocalManga>()

    init {
        coEvery { network.invoke(any<List<Manga>>(), any<Boolean>()) } returns emptyList()
    }

    @Test
    fun `refresh all updates every feed settles failures and individual retry still works`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val model = ScreenModelStore.getOrPut("feed-test", null) {
            FeedScreenModel(manager, preferences, mockk(), network, configured, searches, mockk(), mockk(), mockk(), mockk(), mockk(), dispatcher, { it.message.orEmpty() })
        }
        try {
            model.state.first { it.items?.all { row -> !row.loading } == true }
            coEvery { sources[0].getLatestUpdates(1) } throws IllegalStateException("failed source")
            model.refreshAll()
            testScheduler.runCurrent()
            val completed = model.state.first { it.items?.all { row -> !row.loading } == true }
            completed.items!![0].error shouldBe "failed source"
            completed.items[1].error shouldBe null
            sources.forEach { source -> coVerify(exactly = 2) { source.getLatestUpdates(1) } }
            coEvery { sources[0].getLatestUpdates(1) } returns MangasPage(emptyList(), false)
            model.retry(completed.items[0])
            testScheduler.runCurrent()
            model.state.first { !it.isLoadingItems }.items!![0].error shouldBe null
            coVerify(exactly = 3) { sources[0].getLatestUpdates(1) }
            coVerify(exactly = 2) { sources[1].getLatestUpdates(1) }
        } finally {
            ScreenModelStore.onDisposeNavigator("feed-test")
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `overlapping refresh all does not strand loading rows or apply an older failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val oldRequest = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { sources[0].getLatestUpdates(1) } coAnswers {
            calls++
            if (calls == 1) {
                oldRequest.await()
                error("stale failure")
            }
            MangasPage(emptyList(), false)
        }
        val model = ScreenModelStore.getOrPut("feed-overlap", null) {
            FeedScreenModel(manager, preferences, mockk(), network, configured, searches, mockk(), mockk(), mockk(), mockk(), mockk(), dispatcher, { it.message.orEmpty() })
        }
        try {
            testScheduler.runCurrent()
            model.refreshAll()
            testScheduler.runCurrent()
            model.state.first { !it.isLoadingItems }.items!![0].error shouldBe null
            oldRequest.complete(Unit)
            testScheduler.runCurrent()
            model.state.value.items!![0].error shouldBe null
            model.state.value.isLoadingItems shouldBe false
            sources.forEach { source -> coVerify(exactly = 2) { source.getLatestUpdates(1) } }
        } finally {
            oldRequest.complete(Unit)
            ScreenModelStore.onDisposeNavigator("feed-overlap")
            Dispatchers.resetMain()
        }
    }
}
