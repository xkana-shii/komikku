package eu.kanade.tachiyomi.data.coil

import coil3.request.Options
import eu.kanade.tachiyomi.data.cache.CoverCache
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MangaCover
import kotlin.time.Instant

class RecoveredCoverKeysTest {

    private val url = "https://old.example/cover.jpg"
    private val newUrl = "https://new.example/cover.jpg"

    @Test
    fun `returns the stored key while the cover timestamp and URL are unchanged`() {
        val mangaId = 42L
        val timestamp = Instant.fromEpochMilliseconds(1_000)

        RecoveredCoverKeys.put(mangaId, newUrl, timestamp, "old;1000")

        RecoveredCoverKeys.get(mangaId, newUrl, timestamp) shouldBe "old;1000"
    }

    @Test
    fun `ignores the stored key after a manual cover change`() {
        val mangaId = 43L
        val timestamp = Instant.fromEpochMilliseconds(1_000)

        RecoveredCoverKeys.put(mangaId, newUrl, timestamp, "old;1000")

        RecoveredCoverKeys.get(mangaId, newUrl, Instant.fromEpochMilliseconds(2_000)) shouldBe null
        // The stale entry is dropped as well.
        RecoveredCoverKeys.get(mangaId, newUrl, timestamp) shouldBe null
    }

    @Test
    fun `ignores and drops the stored key when the URL changes`() {
        val mangaId = 45L
        val timestamp = Instant.fromEpochMilliseconds(1_000)

        RecoveredCoverKeys.put(mangaId, newUrl, timestamp, "old;1000")

        RecoveredCoverKeys.get(mangaId, url, timestamp) shouldBe null
        // The mismatching entry is dropped as well.
        RecoveredCoverKeys.get(mangaId, newUrl, timestamp) shouldBe null
    }

    @Test
    fun `normalized equivalent URLs still match`() {
        val mangaId = 46L
        val timestamp = Instant.fromEpochMilliseconds(1_000)

        RecoveredCoverKeys.put(mangaId, "https://Example.com:443/cover.jpg", timestamp, "old;1000")

        RecoveredCoverKeys.get(mangaId, "https://example.com/cover.jpg", timestamp) shouldBe "old;1000"
    }

    @Test
    fun `removing a manga drops its stored key`() {
        val mangaId = 47L
        val timestamp = Instant.fromEpochMilliseconds(1_000)

        RecoveredCoverKeys.put(mangaId, newUrl, timestamp, "old;1000")
        RecoveredCoverKeys.remove(mangaId)

        RecoveredCoverKeys.get(mangaId, newUrl, timestamp) shouldBe null
    }

    @Test
    fun `cover keyer uses the recovered key until the timestamp changes`() {
        val mangaId = 44L
        val timestamp = Instant.fromEpochMilliseconds(1_000)
        val newTimestamp = Instant.fromEpochMilliseconds(2_000)
        val coverCache = mockk<CoverCache>()
        every { coverCache.hasCustomCover(mangaId) } returns false
        val cover = MangaCover(
            mangaId = mangaId,
            sourceId = 1L,
            isMangaFavorite = true,
            ogUrl = newUrl,
            lastModified = timestamp,
        )

        RecoveredCoverKeys.put(mangaId, newUrl, timestamp, "old;1000")
        val keyer = MangaCoverKeyer(coverCache)
        val options = mockk<Options>()

        keyer.key(cover, options) shouldBe "old;1000"
        keyer.key(cover.copy(lastModified = newTimestamp), options) shouldBe "$newUrl;$newTimestamp"
    }
}
