package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.VisibleChapterStats
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.SmartMergeSettings
import tachiyomi.domain.manga.model.Manga

// KMK -->
class LibraryVisibilityCacheTest {
    @Test
    fun `20129 manga query only effective preference changes and reuse unchanged entries`() = runTest {
        val mangas = (1L..20129L).map(::manga)
        val cache = LibraryVisibilityCache()
        val loads = mutableListOf<List<Long>>()
        suspend fun prepare(settings: SmartMergeSettings, data: List<LibraryManga> = mangas) =
            cache.prepare(data, settings) { missing, _ ->
                loads.add(missing.map(Manga::id))
                missing.associate { it.id to VisibleChapterStats(4, 2, 1, 1) }
            }
        val off = SmartMergeSettings(false, emptyMap())
        prepare(off)
        loads.size shouldBe 0
        val one = prepare(off.copy(overrides = mapOf(42L to true)))
        loads.single() shouldBe listOf(42L)
        val two = prepare(off.copy(overrides = mapOf(42L to true, 43L to true)))
        loads.last() shouldBe listOf(43L)
        (one[41] === two[41]) shouldBe true
        prepare(SmartMergeSettings(true, emptyMap()))
        loads.last().size shouldBe 20127
        prepare(SmartMergeSettings(true, mapOf(42L to false)))
        loads.size shouldBe 3
        prepare(SmartMergeSettings(true, mapOf(42L to false)))
        loads.size shouldBe 3
        println("20129 titles: OFF=0 loads, one override=1 title, second=1 title, global ON=20127 titles, repeat=0 loads")
    }

    @Test
    fun `database invalidation cannot reuse equal aggregate counts and removal drops entries`() = runTest {
        val cache = LibraryVisibilityCache()
        val data = listOf(manga(1))
        var read = 0L
        suspend fun prepare(snapshot: List<LibraryManga>) = cache.prepare(snapshot, SmartMergeSettings(true, emptyMap())) { _, _ ->
            mapOf(1L to VisibleChapterStats(2, read, 1, read))
        }
        prepare(data).single().readCount shouldBe 0
        read = 1
        prepare(listOf(data.single().copy())).single().readCount shouldBe 1
        prepare(emptyList()).size shouldBe 0
        prepare(data).single().bookmarkReadCount shouldBe 1
    }

    @Test
    fun `blacklist stays active when merge disabled and effective toggles invalidate it`() = runTest {
        val data = listOf(manga(1).let { it.copy(manga = it.manga.copy(blacklistedChapters = listOf("url:/hidden"))) })
        val cache = LibraryVisibilityCache()
        var calls = 0
        suspend fun prepare(enabled: Boolean) = cache.prepare(data, SmartMergeSettings(enabled, emptyMap())) { _, settings ->
            calls++
            mapOf(1L to VisibleChapterStats(if (settings.enabled) 1 else 2, 1, 1, 1))
        }
        prepare(false).single().totalChapters shouldBe 2
        prepare(true).single().totalChapters shouldBe 1
        prepare(false).single().totalChapters shouldBe 2
        calls shouldBe 3
    }

    private fun manga(id: Long) = LibraryManga(
        manga = Manga.create().copy(id = id), categories = emptyList(), totalChapters = 8,
        readCount = 3, bookmarkCount = 2, bookmarkReadCount = 1, chapterFlags = 0,
        latestUpload = 0, chapterFetchedAt = 0, lastRead = 0,
    )
}
// KMK <--
