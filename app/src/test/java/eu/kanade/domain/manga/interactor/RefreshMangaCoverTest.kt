package eu.kanade.domain.manga.interactor

import android.util.Log
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import java.io.IOException

class RefreshMangaCoverTest {

    private val mangaId = 1L
    private val sourceId = 2L
    private val staleUrl = "https://old.example/cover.jpg"
    private val newUrl = "https://new.example/cover.jpg"

    private val mangaCooldown = 6 * 60 * 60 * 1000L
    private val transientCooldown = 10 * 60 * 1000L
    private val deadReplacementCooldown = 15 * 60 * 1000L
    private val sourceMinInterval = 300L

    private val manga = Manga.create().copy(
        id = mangaId,
        source = sourceId,
        url = "/manga/1",
        favorite = true,
        ogThumbnailUrl = staleUrl,
    )

    private val repository = mockk<MangaRepository>()
    private val sourceManager = mockk<SourceManager>()
    private val coverCache = mockk<CoverCache>(relaxed = true)
    private val source = mockk<HttpSource>()

    private var now = 0L

    private val refreshMangaCover = RefreshMangaCover(
        repository,
        sourceManager,
        coverCache,
        now = { now },
    )

    @BeforeEach
    fun setUpLogging() {
        // XLog is not initialized in JVM tests; its fallback uses android.util.Log.
        mockkStatic(Log::class)
        every { Log.println(any(), any(), any()) } returns 0
        every { Log.getStackTraceString(any()) } returns ""
    }

    @AfterEach
    fun tearDownLogging() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `finds a changed source cover without persisting it`() = runTest {
        stubManga()
        stubSource(newUrl)

        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl

        coVerify(exactly = 0) { repository.updateCover(any(), any(), any()) }
        verify(exactly = 0) { coverCache.deleteFromCache(any(), any()) }
    }

    @Test
    fun `ignores the cover URL that already failed`() = runTest {
        stubManga()
        stubSource(staleUrl)

        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe null

        coVerify(exactly = 0) { repository.updateCover(any(), any(), any()) }
    }

    @Test
    fun `ignores a non-http replacement`() = runTest {
        stubManga()
        stubSource("not a url")

        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe null

        coVerify(exactly = 0) { repository.updateCover(any(), any(), any()) }
    }

    @Test
    fun `persists only the cover fields`() = runTest {
        stubManga()
        coEvery { repository.updateCover(mangaId, staleUrl, newUrl) } returns true

        refreshMangaCover.persistReplacement(mangaId, staleUrl, newUrl) shouldBe CoverPersistResult.Persisted

        coVerify(exactly = 1) { repository.updateCover(mangaId, staleUrl, newUrl) }
        verify(exactly = 1) { coverCache.deleteFromCache(manga, false) }
    }

    @Test
    fun `does not evict the cover cache when the write fails`() = runTest {
        stubManga()
        coEvery { repository.updateCover(mangaId, staleUrl, newUrl) } returns false

        refreshMangaCover.persistReplacement(mangaId, staleUrl, newUrl) shouldBe CoverPersistResult.Failed

        coVerify(exactly = 1) { repository.updateCover(mangaId, staleUrl, newUrl) }
        verify(exactly = 0) { coverCache.deleteFromCache(any(), any()) }
    }

    @Test
    fun `treats an already persisted replacement as success`() = runTest {
        // A concurrent recovery won the race and already wrote the same URL.
        stubManga(ogThumbnailUrl = newUrl)

        refreshMangaCover.persistReplacement(mangaId, staleUrl, newUrl) shouldBe CoverPersistResult.Persisted

        coVerify(exactly = 0) { repository.updateCover(any(), any(), any()) }
        // The winner already handled the cover cache.
        verify(exactly = 0) { coverCache.deleteFromCache(any(), any()) }
    }

    @Test
    fun `skips the write while a sync is applying`() = runTest {
        stubManga()
        coEvery { repository.isSyncing(mangaId) } returns true

        refreshMangaCover.persistReplacement(mangaId, staleUrl, newUrl) shouldBe
            CoverPersistResult.SkippedWhileSyncing

        coVerify(exactly = 0) { repository.updateCover(any(), any(), any()) }
    }

    @Test
    fun `does not persist when the entry changed meanwhile`() = runTest {
        stubManga(ogThumbnailUrl = "https://manual.example/cover.jpg")

        refreshMangaCover.persistReplacement(mangaId, staleUrl, newUrl) shouldBe CoverPersistResult.Obsolete

        coVerify(exactly = 0) { repository.updateCover(any(), any(), any()) }
    }

    @Test
    fun `does not persist over a custom cover`() = runTest {
        stubManga()
        every { coverCache.hasCustomCover(mangaId) } returns true

        refreshMangaCover.persistReplacement(mangaId, staleUrl, newUrl) shouldBe CoverPersistResult.Obsolete

        coVerify(exactly = 0) { repository.updateCover(any(), any(), any()) }
    }

    @Test
    fun `does not touch the source for a custom cover`() = runTest {
        stubManga()
        every { coverCache.hasCustomCover(mangaId) } returns true

        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe null

        coVerify(exactly = 0) { sourceManager.get(any()) }
        coVerify(exactly = 0) { source.getMangaUpdate(any(), any(), true, false) }
    }

    @Test
    fun `reuses the discovered replacement without hitting the source again`() = runTest {
        stubManga()
        stubSource(newUrl)

        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl

        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), true, false) }
    }

    @Test
    fun `cooldown prevents another source call and expiry allows a retry`() = runTest {
        stubManga()
        stubSource(newUrl)

        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), true, false) }

        now += mangaCooldown
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), true, false) }
    }

    @Test
    fun `a different stale URL is not blocked by the old cooldown`() = runTest {
        stubManga()
        stubSource(newUrl)
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl

        val otherStaleUrl = "https://other-old.example/cover.jpg"
        stubManga(ogThumbnailUrl = otherStaleUrl)
        now += sourceMinInterval
        refreshMangaCover.findReplacement(mangaId, otherStaleUrl) shouldBe newUrl

        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), true, false) }
    }

    @Test
    fun `a transient failure uses the short backoff before retrying`() = runTest {
        stubManga()
        every { source.id } returns sourceId
        coEvery { sourceManager.get(sourceId) } returns source
        coEvery { source.getMangaUpdate(any(), any(), true, false) } throws IOException("network down")

        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe null
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe null
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), true, false) }

        now += transientCooldown
        stubSource(newUrl)
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), true, false) }
    }

    @Test
    fun `invalidation removes the dead replacement and backs off briefly`() = runTest {
        stubManga()
        stubSource(newUrl)
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl

        refreshMangaCover.invalidateReplacement(mangaId, newUrl)
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe null
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), true, false) }

        now += deadReplacementCooldown
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), true, false) }
    }

    @Test
    fun `concurrent requests share one source call`() = runTest {
        stubManga()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        every { source.id } returns sourceId
        coEvery { sourceManager.get(sourceId) } returns source
        coEvery { source.getMangaUpdate(any(), any(), true, false) } coAnswers {
            started.complete(Unit)
            release.await()
            SMangaUpdate(
                manga = SManga.create().apply { thumbnail_url = newUrl },
                chapters = emptyList(),
            )
        }

        val first = async { refreshMangaCover.findReplacement(mangaId, staleUrl) }
        started.await()
        val second = async { refreshMangaCover.findReplacement(mangaId, staleUrl) }
        // Let the follower join the in-flight leader before releasing it.
        yield()
        release.complete(Unit)

        first.await() shouldBe newUrl
        second.await() shouldBe newUrl
        coVerify(exactly = 1) { source.getMangaUpdate(any(), any(), true, false) }
    }

    @Test
    fun `a cancelled refresh does not set a cooldown`() = runTest {
        stubManga()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        every { source.id } returns sourceId
        coEvery { sourceManager.get(sourceId) } returns source
        coEvery { source.getMangaUpdate(any(), any(), true, false) } coAnswers {
            started.complete(Unit)
            release.await()
            SMangaUpdate(
                manga = SManga.create().apply { thumbnail_url = newUrl },
                chapters = emptyList(),
            )
        }

        val leader = async { refreshMangaCover.findReplacement(mangaId, staleUrl) }
        started.await()
        leader.cancelAndJoin()

        now += sourceMinInterval
        stubSource(newUrl)
        refreshMangaCover.findReplacement(mangaId, staleUrl) shouldBe newUrl
        coVerify(exactly = 2) { source.getMangaUpdate(any(), any(), true, false) }
    }

    @Test
    fun `five source failures open the breaker for that source`() = runTest {
        every { source.id } returns sourceId
        coEvery { sourceManager.get(sourceId) } returns source
        coEvery { source.getMangaUpdate(any(), any(), true, false) } throws IOException("source down")

        repeat(5) { index ->
            val id = 100L + index
            val stale = "https://old.example/$id.jpg"
            coEvery { repository.getMangaById(id) } returns manga.copy(id = id, ogThumbnailUrl = stale)
            refreshMangaCover.findReplacement(id, stale) shouldBe null
            now += sourceMinInterval
        }
        coVerify(exactly = 5) { source.getMangaUpdate(any(), any(), true, false) }

        val sixthId = 200L
        val sixthStale = "https://old.example/6.jpg"
        coEvery { repository.getMangaById(sixthId) } returns manga.copy(id = sixthId, ogThumbnailUrl = sixthStale)
        refreshMangaCover.findReplacement(sixthId, sixthStale) shouldBe null

        coVerify(exactly = 5) { source.getMangaUpdate(any(), any(), true, false) }
    }

    private fun stubManga(ogThumbnailUrl: String = staleUrl) {
        coEvery { repository.getMangaById(mangaId) } returns manga.copy(ogThumbnailUrl = ogThumbnailUrl)
        coEvery { repository.isSyncing(mangaId) } returns false
    }

    private fun stubSource(thumbnailUrl: String?) {
        every { source.id } returns sourceId
        coEvery { sourceManager.get(sourceId) } returns source
        coEvery { source.getMangaUpdate(any(), any(), true, false) } returns SMangaUpdate(
            manga = SManga.create().apply { this.thumbnail_url = thumbnailUrl },
            chapters = emptyList(),
        )
    }
}
