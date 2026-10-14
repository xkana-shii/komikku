package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

// KMK -->
class LibraryItemCacheTest {
    @Test
    fun `20129 items reconstruct only changed inputs and evict removed manga`() = runTest {
        val cache = LibraryItemCache<Long, Any>()
        var rebuilt = 0
        suspend fun prepare(id: Long, key: Long) = cache.get(id, key) {
            rebuilt++
            Any()
        }
        val first = (1L..20129L).map { prepare(it, 0) }
        rebuilt shouldBe 20129
        rebuilt = 0
        (1L..20129L).forEach { id ->
            val item = prepare(id, if (id == 42L) 1 else 0)
            if (id != 42L) (item === first[(id - 1).toInt()]) shouldBe true
        }
        rebuilt shouldBe 1
        (prepare(42, 1) === first[41]) shouldBe false
        cache.retain((2L..20129L).toSet())
        prepare(1, 0)
        rebuilt shouldBe 2
    }
}
// KMK <--
