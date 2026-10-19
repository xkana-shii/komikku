package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import eu.kanade.domain.manga.interactor.CoverPersistResult
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.network.HttpException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okio.Buffer
import okio.FileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MangaCover
import java.io.File
import java.io.IOException
import kotlin.time.Instant

class MangaCoverFetcherRecoveryTest {

    private val mangaId = 77L
    private val staleUrl = "https://old.example/cover.jpg"
    private val newUrl = "https://new.example/cover.jpg"
    private val diskKey = "test-key"

    private val cover = MangaCover(
        mangaId = mangaId,
        sourceId = 1L,
        isMangaFavorite = true,
        ogUrl = staleUrl,
        lastModified = Instant.fromEpochMilliseconds(1_000),
    )

    @AfterEach
    fun clearAlias() {
        RecoveredCoverKeys.remove(mangaId)
    }

    @Test
    fun `persisted replacement installs the session alias`() = runTest {
        val harness = harness(persistResult = CoverPersistResult.Persisted)

        harness.fetcher.httpLoaderWithRecovery() shouldBe harness.success

        harness.counters.find shouldBe 1
        harness.counters.load shouldBe 2
        harness.counters.persist shouldBe 1
        harness.counters.invalidate shouldBe 0
        RecoveredCoverKeys.get(mangaId, newUrl, cover.lastModified) shouldBe diskKey
    }

    @Test
    fun `failed persistence removes the alias without invalidating the replacement`() = runTest {
        val harness = harness(persistResult = CoverPersistResult.Failed)

        harness.fetcher.httpLoaderWithRecovery() shouldBe harness.success

        harness.counters.find shouldBe 1
        harness.counters.persist shouldBe 1
        harness.counters.invalidate shouldBe 0
        RecoveredCoverKeys.get(mangaId, newUrl, cover.lastModified) shouldBe null
    }

    @Test
    fun `persistence skipped during sync removes the alias without invalidating the replacement`() = runTest {
        val harness = harness(persistResult = CoverPersistResult.SkippedWhileSyncing)

        harness.fetcher.httpLoaderWithRecovery() shouldBe harness.success

        harness.counters.persist shouldBe 1
        harness.counters.invalidate shouldBe 0
        RecoveredCoverKeys.get(mangaId, newUrl, cover.lastModified) shouldBe null
    }

    @Test
    fun `obsolete persistence invalidates the replacement`() = runTest {
        val harness = harness(persistResult = CoverPersistResult.Obsolete)

        harness.fetcher.httpLoaderWithRecovery() shouldBe harness.success

        harness.counters.persist shouldBe 1
        harness.counters.invalidate shouldBe 1
        RecoveredCoverKeys.get(mangaId, newUrl, cover.lastModified) shouldBe null
    }

    @Test
    fun `a failed retry invalidates the replacement and never persists it`() = runTest {
        val harness = harness(firstFailure = HttpException(404), retryFailure = IOException("dead host"))

        shouldThrow<IOException> { harness.fetcher.httpLoaderWithRecovery() }

        harness.counters.find shouldBe 1
        harness.counters.load shouldBe 2
        harness.counters.persist shouldBe 0
        harness.counters.invalidate shouldBe 1
        RecoveredCoverKeys.get(mangaId, newUrl, cover.lastModified) shouldBe null
    }

    @Test
    fun `only retries once when the replacement also fails`() = runTest {
        val harness = harness(firstFailure = HttpException(404), retryFailure = IOException("still dead"))

        shouldThrow<IOException> { harness.fetcher.httpLoaderWithRecovery() }

        harness.counters.find shouldBe 1
        harness.counters.load shouldBe 2
        harness.counters.invalidate shouldBe 1
    }

    @Test
    fun `a non-recoverable failure does not ask for a replacement`() = runTest {
        val harness = harness(firstFailure = HttpException(500))

        shouldThrow<HttpException> { harness.fetcher.httpLoaderWithRecovery() }

        harness.counters.find shouldBe 0
        harness.counters.persist shouldBe 0
        harness.counters.invalidate shouldBe 0
    }

    @Test
    fun `an offline device does not ask for a replacement`() = runTest {
        val harness = harness(firstFailure = HttpException(404), online = false)

        shouldThrow<HttpException> { harness.fetcher.httpLoaderWithRecovery() }

        harness.counters.find shouldBe 0
        harness.counters.persist shouldBe 0
    }

    @Test
    fun `a custom cover is never replaced`() = runTest {
        val harness = harness(firstFailure = HttpException(404), hasCustomCover = true)

        shouldThrow<HttpException> { harness.fetcher.httpLoaderWithRecovery() }

        harness.counters.find shouldBe 0
        harness.counters.persist shouldBe 0
    }

    private fun harness(
        firstFailure: Exception? = HttpException(404),
        retryFailure: Exception? = null,
        persistResult: CoverPersistResult = CoverPersistResult.Persisted,
        online: Boolean = true,
        hasCustomCover: Boolean = false,
    ): Harness {
        val counters = Counters()
        val success = SourceFetchResult(
            source = ImageSource(source = Buffer(), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.NETWORK,
        )
        val coverCache = mockk<CoverCache>(relaxed = true)
        every { coverCache.hasCustomCover(mangaId) } returns hasCustomCover
        val uiPreferences = mockk<UiPreferences>()
        every { uiPreferences.themeCoverBased.get() } returns false
        every { uiPreferences.preloadLibraryColor.get() } returns false

        val fetcher = MangaCoverFetcher(
            mangaCover = cover,
            isLibraryManga = true,
            options = mockk<Options>(relaxed = true),
            customCoverFileLazy = lazy { File("missing-cover") },
            diskCacheKeyLazy = lazy { diskKey },
            getSource = { null },
            callFactoryLazy = lazy { mockk<Call.Factory>(relaxed = true) },
            imageLoader = mockk<ImageLoader>(relaxed = true),
            uiPreferences = uiPreferences,
            coverCache = coverCache,
            findReplacementCover = {
                counters.find++
                newUrl
            },
            persistReplacementCover = {
                counters.persist++
                persistResult
            },
            invalidateReplacementCover = { counters.invalidate++ },
            httpLoaderOverride = {
                counters.load++
                if (counters.load == 1 && firstFailure != null) throw firstFailure
                if (counters.load == 2 && retryFailure != null) throw retryFailure
                success
            },
            isOnlineOverride = { online },
        )
        return Harness(fetcher, success, counters)
    }

    private class Harness(
        val fetcher: MangaCoverFetcher,
        val success: FetchResult,
        val counters: Counters,
    )

    private class Counters {
        var find = 0
        var load = 0
        var persist = 0
        var invalidate = 0
    }
}
