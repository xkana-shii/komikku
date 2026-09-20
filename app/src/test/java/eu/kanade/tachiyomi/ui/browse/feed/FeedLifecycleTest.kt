package eu.kanade.tachiyomi.ui.browse.feed

import android.os.Trace
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.network.FreshNetworkRequests
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.testutil.LocalHttpServer
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreenModel
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Cache
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.interactor.GetSavedSearchGlobalFeed
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.nio.file.Files
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class FeedLifecycleTest {
    @BeforeEach
    fun stubPlatformTracing() {
        mockkStatic(Trace::class)
        every { Trace.beginSection(any()) } returns Unit
        every { Trace.endSection() } returns Unit
    }

    @AfterEach
    fun restorePlatformTracing() {
        unmockkStatic(Trace::class)
    }

    private data class Row(val id: Long, val urls: List<String>?, val error: String?, val loading: Boolean)
    private fun page(url: String) = MangasPage(
        listOf(
            SManga.create().apply {
                this.url = url
                title = url
            },
        ),
        false,
    )

    private inner class Fixture(val scope: TestScope, val local: Boolean, val initialized: Boolean = true, val saved: Boolean = false) {
        val ready = MutableStateFlow(initialized)
        val filters = FilterList(object : Filter.Text("genre") {})
        val source = mockk<Source> {
            every { id } returns 1
            every { name } returns "Source"
            every { lang } returns "en"
            every { supportsLatest } returns true
            every { getFilterList() } returns filters
            coEvery { getLatestUpdates(1) } returns page("initial")
            coEvery { getPopularManga(1) } returns MangasPage(emptyList(), false)
        }
        val manager = mockk<SourceManager> {
            every { isInitialized } returns ready
            every { get(1) } returns source
            every { getOrStub(1) } returns source
        }
        val searches = listOf("one", "two").mapIndexed { index, value ->
            val state = FilterList(object : Filter.Text("genre", value) {})
            SavedSearch(index + 10L, 1, value, value, FilterSerializer().serialize(state).toString())
        }
        val config = if (saved) searches.map { FeedSavedSearch(it.id, 1, it.id, !local, it.id) } else emptyList()
        val configured = MutableStateFlow(config + if (local) emptyList() else listOf(FeedSavedSearch(1, 1, null, true, 0), FeedSavedSearch(2, 2, null, true, 1)))
        val globalConfig = mockk<GetFeedSavedSearchGlobal> { every { subscribe() } returns configured }
        val localConfig = mockk<GetFeedSavedSearchBySourceId> { every { subscribe(1) } returns configured }
        val globalSearches = mockk<GetSavedSearchGlobalFeed> { coEvery { await() } returns searches }
        val localSearches = mockk<GetSavedSearchBySourceIdFeed> { coEvery { await(1) } returns searches }
        val prefs = mockk<SourcePreferences>(relaxed = true) { every { hideInLibraryFeedItems().get() } returns false }
        val ui = mockk<UiPreferences>(relaxed = true) {
            every { expandFilters().get() } returns false
            every { expandFilters().changes() } returns MutableStateFlow(false)
        }
        val incognito = mockk<GetIncognitoState> {
            every { await(1) } returns false
            every { subscribe(1) } returns MutableStateFlow(false)
        }
        val exh = mockk<GetExhSavedSearch> { coEvery { await(1, any()) } returns emptyList() }
        val manga = mockk<GetManga>()
        val network = mockk<NetworkToLocalManga>()
        init {
            coEvery { network.invoke(any<List<Manga>>(), any<Boolean>()) } answers { firstArg<List<Manga>>() }
        }
        val other = mockk<Source> {
            every { id } returns 2
            every { name } returns "Other"
            every { lang } returns "en"
            every { supportsLatest } returns false
            coEvery { getPopularManga(1) } returns MangasPage(emptyList(), false)
        }
        lateinit var global: FeedScreenModel
        lateinit var specific: SourceFeedScreenModel
        lateinit var rows: Flow<List<Row>>
        val key = "lifecycle-${UUID.randomUUID()}"
        val latestId get() = if (local) -1L else 1L
        fun start() {
            val dispatcher = StandardTestDispatcher(scope.testScheduler)
            Dispatchers.setMain(dispatcher)
            every { manager.get(2) } returns other
            if (local) {
                specific = ScreenModelStore.getOrPut(key, null) {
                    SourceFeedScreenModel(1, ui, manager, manga, network, localConfig, localSearches, mockk(), mockk(), mockk(), mockk(), exh, mockk(), incognito, mockk(), mockk(), prefs, dispatcher, { it.message.orEmpty() })
                }
                rows = specific.state.map { it.items.map { row -> Row(row.id, row.results?.map { it.url }, row.error, row.loading) } }
            } else {
                global = ScreenModelStore.getOrPut(key, null) {
                    FeedScreenModel(manager, prefs, manga, network, globalConfig, globalSearches, mockk(), mockk(), mockk(), mockk(), mockk(), dispatcher, { it.message.orEmpty() })
                }
                rows = global.state.map { it.items.orEmpty().map { row -> Row(row.feed.id, row.results?.map { it.url }, row.error, row.loading) } }
            }
        }
        fun refresh() {
            if (local) specific.refreshAll() else feedActions(global).onRefresh()
        }
        fun retry() {
            if (local) {
                specific.retry(specific.state.value.items.first { it.id == latestId })
            } else {
                feedActions(global).onRetry(global.state.value.items!!.first { it.feed.id == latestId })
            }
        }
        suspend fun settled() = rows.first { it.isNotEmpty() && it.none { row -> row.loading } }
        suspend fun latest() = rows.first().first { it.id == latestId }
        fun close() {
            ScreenModelStore.onDisposeNavigator(key)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `pull callback revalidates every successful cached HTTP response and individual retry makes one request`() = runTest {
        for (mode in listOf("success", "error", "empty")) {
            LocalHttpServer().use { server ->
                val directory = Files.createTempDirectory("feed-http-cache").toFile()
                Cache(directory, 1024 * 1024).use { cache ->
                    val client = server.clientBuilder().cache(cache).eventListenerFactory(FreshNetworkRequests).addInterceptor(FreshNetworkRequests).build()
                    val fixture = Fixture(this, false, saved = true)
                    fixture.configured.value = fixture.configured.value.filter { it.source == 1L }
                    var revision = 1
                    server.respond = { request ->
                        LocalHttpServer.Reply(
                            code = if (mode == "error" && revision == 1 && request.path == "/latest") 503 else 200,
                            body = if (mode == "empty" && request.path == "/one") "" else "${request.path}/$revision",
                            cacheControl = "public, max-age=600",
                        )
                    }
                    suspend fun fetch(path: String): MangasPage = client.newCall(GET("https://feed.test/$path")).awaitSuccess().use {
                        val body = it.body.string()
                        if (body.isEmpty()) MangasPage(emptyList(), false) else page(body)
                    }
                    coEvery { fixture.source.getLatestUpdates(1) } coAnswers { fetch("latest") }
                    coEvery { fixture.source.getSearchManga(1, any(), any()) } coAnswers { fetch(secondArg()) }
                    fixture.start()
                    try {
                        fixture.settled()
                        server.requests.size shouldBe 3
                        // Verify the fixture really exercises a warm HTTP cache, not only source mocks.
                        fetch("two")
                        server.requests.size shouldBe 3
                        revision = 2
                        feedActions(fixture.global).onRefresh()
                        fixture.settled().all { it.error == null } shouldBe true
                        server.requests.size shouldBe 6
                        server.requests.toList().takeLast(3).map { it.path }.toSet() shouldBe setOf("/latest", "/one", "/two")
                        server.requests.toList().takeLast(3).all { it.cacheControl?.contains("no-cache") == true } shouldBe true
                        fixture.global.state.value.refreshing shouldBe false
                        fixture.retry()
                        fixture.settled()
                        server.requests.size shouldBe 7
                        server.requests.last().path shouldBe "/latest"
                    } finally {
                        fixture.close()
                        client.connectionPool.evictAll()
                        client.dispatcher.executorService.shutdown()
                    }
                }
                directory.listFiles()?.forEach { it.delete() }
                directory.delete()
            }
        }
    }

    @Test
    fun `feed IDs differ from saved search IDs and editing preserves each reference in both models`() = runTest {
        for (local in listOf(false, true)) {
            val fixture = Fixture(this, local, saved = true)
            fixture.configured.value = fixture.configured.value.map { if (it.savedSearch != null) it.copy(id = it.id + 100) else it }
            coEvery { fixture.source.getSearchManga(1, any(), any()) } coAnswers { page(secondArg()) }
            fixture.start()
            try {
                fixture.settled().first { it.id == 110L }.urls shouldBe listOf("one")
                fixture.settled().first { it.id == 111L }.urls shouldBe listOf("two")
                val edited = fixture.searches.map { if (it.id == 10L) it.copy(query = "edited") else it }
                coEvery { fixture.globalSearches.await() } returns edited
                coEvery { fixture.localSearches.await(1) } returns edited
                fixture.refresh()
                fixture.settled().first { it.id == 110L }.urls shouldBe listOf("edited")
                fixture.settled().first { it.id == 111L }.urls shouldBe listOf("two")
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `saved searches on different sources resolve their own records and genuinely deleted records do not search`() = runTest {
        val fixture = Fixture(this, false, saved = true)
        val otherSearch = fixture.searches.first().copy(id = 77, source = 2, query = "other")
        every { fixture.other.getFilterList() } returns fixture.filters
        coEvery { fixture.source.getSearchManga(1, any(), any()) } coAnswers { page(secondArg()) }
        coEvery { fixture.other.getSearchManga(1, "other", any()) } returns page("other-source")
        coEvery { fixture.globalSearches.await() } returns fixture.searches + otherSearch
        fixture.configured.value += FeedSavedSearch(99, 2, 77, true, 8)
        fixture.start()
        try {
            fixture.settled().first { it.id == 99L }.urls shouldBe listOf("other-source")
            coEvery { fixture.globalSearches.await() } returns fixture.searches
            feedActions(fixture.global).onRetry(fixture.global.state.value.items!!.first { it.feed.id == 99L })
            fixture.settled().first { it.id == 99L }.error shouldBe "Invalid saved search"
            coVerify(exactly = 1) { fixture.other.getSearchManga(1, "other", any()) }
            coVerify(exactly = 0) { fixture.source.getSearchManga(1, "other", any()) }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `FeedTab gesture callback requests all three successful rows with zero errors`() = runTest {
        val fixture = Fixture(this, false, saved = true)
        fixture.configured.value = fixture.configured.value.filter { it.source == 1L }
        var revision = 1
        coEvery { fixture.source.getSearchManga(1, any(), any()) } coAnswers { page("${secondArg<String>()}/$revision") }
        coEvery { fixture.source.getLatestUpdates(1) } coAnswers { page("latest/$revision") }
        fixture.start()
        try {
            fixture.settled().size shouldBe 3
            val actions = feedActions(fixture.global)
            revision = 2
            actions.onRefresh()
            fixture.settled().all { it.urls!!.single().endsWith("/2") && it.error == null } shouldBe true
            fixture.global.state.value.refreshing shouldBe false
            coVerify(exactly = 2) {
                fixture.source.getLatestUpdates(1)
                fixture.source.getSearchManga(1, "one", any())
                fixture.source.getSearchManga(1, "two", any())
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `retry resolves a replaced source instead of retaining the row source`() = runTest {
        for (local in listOf(false, true)) {
            val fixture = Fixture(this, local)
            val replacement = mockk<Source> {
                every { id } returns 1L
                every { supportsLatest } returns true
                coEvery { getLatestUpdates(1) } returns page("replacement")
            }
            fixture.start()
            try {
                fixture.settled()
                every { fixture.manager.get(1) } returns replacement
                every { fixture.manager.getOrStub(1) } returns replacement
                fixture.retry()
                fixture.settled().first { it.id == fixture.latestId }.urls shouldBe listOf("replacement")
                coVerify(exactly = 1) { replacement.getLatestUpdates(1) }
                coVerify(exactly = 1) { fixture.source.getLatestUpdates(1) }
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `configuration invalidates old requests before replacement saved searches finish loading`() = runTest {
        for (local in listOf(false, true)) {
            val fixture = Fixture(this, local, saved = true)
            val old = CompletableDeferred<Unit>()
            val configuration = CompletableDeferred<Unit>()
            coEvery { fixture.source.getSearchManga(1, any(), any()) } coAnswers {
                if (secondArg<String>() == "one") old.await()
                page(secondArg())
            }
            fixture.start()
            try {
                testScheduler.runCurrent()
                val changed = fixture.searches.map { it.copy(query = "new-${it.query}") }
                coEvery { fixture.globalSearches.await() } coAnswers {
                    configuration.await()
                    changed
                }
                coEvery { fixture.localSearches.await(1) } coAnswers {
                    configuration.await()
                    changed
                }
                fixture.configured.value = fixture.configured.value.map { it.copy(feedOrder = it.feedOrder + 1) }
                testScheduler.runCurrent()
                old.complete(Unit)
                testScheduler.runCurrent()
                fixture.rows.first().first { it.id == 10L }.apply {
                    urls shouldBe null
                    loading shouldBe true
                }
                configuration.complete(Unit)
                fixture.settled().first { it.id == 10L }.urls shouldBe listOf("new-one")
            } finally {
                old.complete(Unit)
                configuration.complete(Unit)
                fixture.close()
            }
        }
    }

    @Test
    fun `both models discard late success and error after a newer success`() = runTest {
        for (local in listOf(false, true)) {
            for (failure in listOf(false, true)) {
                val fixture = Fixture(this, local)
                val old = CompletableDeferred<Unit>()
                var calls = 0
                coEvery { fixture.source.getLatestUpdates(1) } coAnswers {
                    if (++calls == 1) {
                        old.await()
                        if (failure) error("obsolete") else page("old")
                    } else {
                        page("new")
                    }
                }
                fixture.start()
                try {
                    testScheduler.runCurrent()
                    fixture.refresh()
                    fixture.settled().first { it.id == fixture.latestId }.urls shouldBe listOf("new")
                    old.complete(Unit)
                    testScheduler.runCurrent()
                    coVerify(exactly = 0) { fixture.network.invoke(match<List<Manga>> { mangas -> mangas.any { it.url == "old" } }, any<Boolean>()) }
                    fixture.latest().apply {
                        urls shouldBe listOf("new")
                        error shouldBe null
                        loading shouldBe false
                    }
                } finally {
                    old.complete(Unit)
                    fixture.close()
                }
            }
        }
    }

    @Test
    fun `obsolete completion cannot clear newer loading even for equal row states`() = runTest {
        for (local in listOf(false, true)) {
            val fixture = Fixture(this, local)
            val old = CompletableDeferred<Unit>()
            val newer = CompletableDeferred<Unit>()
            var calls = 0
            coEvery { fixture.source.getLatestUpdates(1) } coAnswers {
                if (++calls == 1) {
                    old.await()
                    error("old")
                } else {
                    newer.await()
                    page("new")
                }
            }
            fixture.start()
            try {
                testScheduler.runCurrent()
                fixture.refresh()
                testScheduler.runCurrent()
                old.complete(Unit)
                testScheduler.runCurrent()
                fixture.latest().loading shouldBe true
                fixture.latest().error shouldBe null
                if (!local) fixture.global.state.value.refreshing shouldBe true
                newer.complete(Unit)
                fixture.settled().first { it.id == fixture.latestId }.urls shouldBe listOf("new")
                if (!local) fixture.global.state.value.refreshing shouldBe false
            } finally {
                old.complete(Unit)
                newer.complete(Unit)
                fixture.close()
            }
        }
    }

    @Test
    fun `refresh snapshots successful empty and failed rows and retry isolates its row`() = runTest {
        for (local in listOf(false, true)) {
            val fixture = Fixture(this, local, saved = true)
            coEvery { fixture.source.getSearchManga(1, any(), any()) } coAnswers { page(secondArg<String>()) }
            fixture.start()
            try {
                fixture.settled()
                coEvery { fixture.source.getLatestUpdates(1) } throws IllegalStateException("Cloudflare")
                fixture.refresh()
                fixture.latest().apply {
                    loading shouldBe true
                    urls shouldBe listOf("initial")
                }
                fixture.settled().first { it.id == fixture.latestId }.apply {
                    error shouldBe "Cloudflare"
                    urls shouldBe listOf("initial")
                }
                coEvery { fixture.source.getLatestUpdates(1) } returns page("recovered")
                fixture.refresh()
                fixture.settled().first { it.id == fixture.latestId }.urls shouldBe listOf("recovered")
                fixture.retry()
                fixture.settled()
                coVerify(exactly = 4) { fixture.source.getLatestUpdates(1) }
                coVerify(exactly = 3) {
                    fixture.source.getSearchManga(1, "one", any())
                    fixture.source.getSearchManga(1, "two", any())
                }
                if (local) {
                    coVerify(exactly = 3) { fixture.source.getPopularManga(1) }
                } else {
                    coVerify(exactly = 3) { fixture.other.getPopularManga(1) }
                }
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `same source saved searches retain distinct cached filter state and wait for finalized startup data`() = runTest {
        for (local in listOf(false, true)) {
            val fixture = Fixture(this, local, initialized = false, saved = true)
            val savedReady = CompletableDeferred<Unit>()
            val firstSearch = CompletableDeferred<Unit>()
            coEvery { fixture.globalSearches.await() } coAnswers {
                savedReady.await()
                fixture.searches
            }
            coEvery { fixture.localSearches.await(1) } coAnswers {
                savedReady.await()
                fixture.searches
            }
            coEvery { fixture.source.getSearchManga(1, any(), any()) } coAnswers {
                val query = secondArg<String>()
                val filters = thirdArg<FilterList>()
                if (query == "one") firstSearch.await()
                (filters.single() as Filter.Text).state shouldBe query
                page(query)
            }
            fixture.start()
            try {
                testScheduler.runCurrent()
                coVerify(exactly = 0) {
                    fixture.source.getLatestUpdates(any())
                    fixture.source.getSearchManga(any(), any(), any())
                }
                fixture.ready.value = true
                testScheduler.runCurrent()
                coVerify(exactly = 0) {
                    fixture.source.getLatestUpdates(any())
                    fixture.source.getSearchManga(any(), any(), any())
                }
                savedReady.complete(Unit)
                testScheduler.runCurrent()
                // Row two can finish while row one is suspended: their filter trees are independent.
                coVerify(exactly = 1) { fixture.source.getSearchManga(1, "one", any()) }
                coVerify(exactly = 1) { fixture.source.getSearchManga(1, "two", any()) }
                firstSearch.complete(Unit)
                val result = fixture.settled()
                result.first { it.id == 10L }.urls shouldBe listOf("one")
                result.first { it.id == 11L }.urls shouldBe listOf("two")
                (fixture.filters.single() as Filter.Text).state shouldBe ""
                fixture.refresh()
                fixture.settled()
                coVerify(exactly = 2) {
                    fixture.source.getSearchManga(1, "one", any())
                    fixture.source.getSearchManga(1, "two", any())
                }
            } finally {
                firstSearch.complete(Unit)
                savedReady.complete(Unit)
                fixture.close()
            }
        }
    }

    @Test
    fun `initial Cloudflare style failure recovers with one isolated retry in both models`() = runTest {
        for (local in listOf(false, true)) {
            val fixture = Fixture(this, local)
            coEvery { fixture.source.getLatestUpdates(1) } throws IllegalStateException("Cloudflare")
            fixture.start()
            try {
                fixture.settled().first { it.id == fixture.latestId }.error shouldBe "Cloudflare"
                if (!local) fixture.global.state.value.refreshing shouldBe false
                coEvery { fixture.source.getLatestUpdates(1) } returns page("recovered")
                fixture.retry()
                fixture.settled().first { it.id == fixture.latestId }.apply {
                    urls shouldBe listOf("recovered")
                    error shouldBe null
                    loading shouldBe false
                }
                coVerify(exactly = 2) { fixture.source.getLatestUpdates(1) }
                if (local) {
                    coVerify(exactly = 1) { fixture.source.getPopularManga(1) }
                } else {
                    coVerify(exactly = 1) { fixture.other.getPopularManga(1) }
                }
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `reusing a card composition subscribes to its new manga in both models`() = runTest {
        for (local in listOf(false, true)) {
            val fixture = Fixture(this, local)
            val first = Manga.create().copy(source = 1, url = "first", ogTitle = "First")
            val second = first.copy(url = "second", ogTitle = "Second")
            val oldUpdates = MutableStateFlow<Manga?>(first)
            val newUpdates = MutableStateFlow<Manga?>(second)
            every { fixture.manga.subscribe("first", 1) } returns oldUpdates
            every { fixture.manga.subscribe("second", 1) } returns newUpdates
            fixture.start()
            val clock = BroadcastFrameClock()
            val recomposer = Recomposer(coroutineContext + clock)
            val composition = Composition(
                object : AbstractApplier<Unit>(Unit) {
                    override fun insertBottomUp(index: Int, instance: Unit) = Unit
                    override fun insertTopDown(index: Int, instance: Unit) = Unit
                    override fun move(from: Int, to: Int, count: Int) = Unit
                    override fun remove(index: Int, count: Int) = Unit
                    override fun onClear() = Unit
                },
                recomposer,
            )
            val recomposition = backgroundScope.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
            val selected = mutableStateOf(first)
            var rendered: Manga? = null
            fun frame() {
                testScheduler.runCurrent()
                Snapshot.sendApplyNotifications()
                testScheduler.runCurrent()
                clock.sendFrame(0)
                testScheduler.runCurrent()
            }
            try {
                composition.setContent {
                    val state = if (local) fixture.specific.getManga(selected.value) else fixture.global.getManga(selected.value)
                    val current = state.value
                    SideEffect { rendered = current }
                }
                frame()
                rendered?.url shouldBe "first"
                selected.value = second
                frame()
                frame()
                rendered?.url shouldBe "second"
                oldUpdates.value = first.copy(ogTitle = "Obsolete update")
                frame()
                rendered?.title shouldBe "Second"
                newUpdates.value = second.copy(ogTitle = "Current update")
                frame()
                rendered?.title shouldBe "Current update"
                verify(exactly = 1) { fixture.manga.subscribe("second", 1) }
            } finally {
                composition.dispose()
                recomposer.cancel()
                recomposition.cancel()
                fixture.close()
            }
        }
    }
}
