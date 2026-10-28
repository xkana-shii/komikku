// KUN -->
package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedEntry
import exh.source.MERGED_SOURCE_ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.MergeSettings

class MangaRestorerTest {

    private val restoreRepository: RestoreRepository = mockk(relaxed = true)
    private val getCategories: GetCategories = mockk()
    private val fetchInterval: FetchInterval = mockk(relaxed = true)

    private fun createRestorer(isSync: Boolean = true): MangaRestorer {
        val restorer = MangaRestorer(
            restoreRepository = restoreRepository,
            getCategories = getCategories,
            fetchInterval = fetchInterval,
        )
        restorer.isSync = isSync
        return restorer
    }

    @Test
    fun `restore resolves categories by backup category and db name when restoreCategories is true`() = runTest {
        val restorer = createRestorer(isSync = true)
        val dbCategories = listOf(
            Category(id = 10L, name = "Reading", order = 0, flags = 0, hidden = false, uid = 100L),
            Category(id = 20L, name = "Completed", order = 1, flags = 0, hidden = false, uid = 200L),
        )
        coEvery { getCategories.await() } returns dbCategories

        val backupCategories = listOf(
            BackupCategory(name = "Reading", order = 0, uid = 100L),
            BackupCategory(name = "Completed", order = 1, uid = 200L),
        )
        val backupManga = BackupManga(
            url = "/test/1",
            title = "Test Manga",
            categories = listOf(0L, 1L),
        )

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(backupManga),
            backupCategories = backupCategories,
            restoreCategories = true,
        )

        val captured = entriesSlot.captured.single()
        assertEquals(listOf(10L, 20L), captured.categoryIds)
    }

    @Test
    fun `restore forwards the chapter pruning snapshot to the repository`() = runTest {
        val restorer = createRestorer(isSync = true)
        restorer.removeMissingChaptersBefore = 1_000L
        coEvery { getCategories.await() } returns emptyList()

        coEvery {
            restoreRepository.restoreManga(
                any(),
                isSync = true,
                restoreCategories = true,
                removeMissingChaptersBefore = 1_000L,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(BackupManga(url = "/test/1", title = "Test Manga")),
            backupCategories = emptyList(),
        )

        coVerify(exactly = 1) {
            restoreRepository.restoreManga(
                any(),
                isSync = true,
                restoreCategories = true,
                removeMissingChaptersBefore = 1_000L,
                update = any(),
            )
        }
    }

    @Test
    fun `restore leaves categoryIds empty and passes restoreCategories false when disabled`() = runTest {
        val restorer = createRestorer(isSync = true)
        val dbCategories = listOf(
            Category(id = 10L, name = "Reading", order = 0, flags = 0, hidden = false, uid = 100L),
        )
        coEvery { getCategories.await() } returns dbCategories

        val backupManga = BackupManga(
            url = "/test/1",
            title = "Test Manga",
            categories = listOf(0L),
        )

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = false,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(backupManga),
            backupCategories = emptyList(),
            restoreCategories = false,
        )

        val captured = entriesSlot.captured.single()
        assertEquals(emptyList<Long>(), captured.categoryIds)
        coVerify(exactly = 1) {
            restoreRepository.restoreManga(
                any(),
                isSync = true,
                restoreCategories = false,
                update = any(),
            )
        }
    }

    @Test
    fun `restore falls back to local category order when backup categories map is missing entry`() = runTest {
        val restorer = createRestorer(isSync = true)
        val dbCategories = listOf(
            Category(id = 42L, name = "Local Only", order = 2, flags = 0, hidden = false, uid = 300L),
        )
        coEvery { getCategories.await() } returns dbCategories

        val backupManga = BackupManga(
            url = "/test/1",
            title = "Test Manga",
            categories = listOf(2L),
        )

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(backupManga),
            backupCategories = emptyList(), // e.g. empty or delta missing this category
            restoreCategories = true,
        )

        val captured = entriesSlot.captured.single()
        assertEquals(listOf(42L), captured.categoryIds)
    }

    @Test
    fun `restore preserves categories when incoming category orders cannot be matched in sync`() = runTest {
        val restorer = createRestorer(isSync = true)
        val dbCategories = listOf(
            Category(id = 42L, name = "Local Only", order = 2, flags = 0, hidden = false, uid = 300L),
        )
        coEvery { getCategories.await() } returns dbCategories

        // Incoming category order 99 does not exist in dbCategories or backupCategories
        val backupManga = BackupManga(
            url = "/test/1",
            title = "Test Manga",
            categories = listOf(99L),
        )

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(backupManga),
            backupCategories = emptyList(),
            restoreCategories = true,
        )

        val captured = entriesSlot.captured.single()
        assertEquals(null, captured.categoryIds)
    }

    @Test
    fun `restore resolves categories by normalized name when case differs or orders shifted`() = runTest {
        val restorer = createRestorer(isSync = true)
        val dbCategories = listOf(
            Category(id = 15L, name = "Action", order = 5, flags = 0, hidden = false, uid = 999L),
        )
        coEvery { getCategories.await() } returns dbCategories

        // Remote backup category has order 0 and different case, but same name
        val backupCategories = listOf(
            BackupCategory(name = "action", order = 0, uid = 888L),
        )
        val backupManga = BackupManga(
            url = "/test/1",
            title = "Test Manga",
            categories = listOf(0L),
        )

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(backupManga),
            backupCategories = backupCategories,
            restoreCategories = true,
        )

        val captured = entriesSlot.captured.single()
        assertEquals(listOf(15L), captured.categoryIds)
    }

    @Test
    fun `restore forwards a marked empty custom info section as an explicit clear`() = runTest {
        val restorer = createRestorer(isSync = true)
        coEvery { getCategories.await() } returns emptyList()

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(
                BackupManga(url = "/test/1", title = "Test Manga", customInfoIncluded = true),
            ),
            backupCategories = emptyList(),
        )

        val captured = entriesSlot.captured.single()
        assertEquals(true, captured.customInfoIncluded)
        // An empty section carries no overrides of its own; the marker is what tells the repository
        // this is an explicit clear rather than an unknown section.
        assertEquals(null, captured.manga.userTitle)
        assertEquals(null, captured.manga.userStatus)
        assertEquals("Test Manga", captured.manga.ogTitle)
    }

    @Test
    fun `restore keeps an unmarked custom info section as unknown`() = runTest {
        val restorer = createRestorer(isSync = true)
        coEvery { getCategories.await() } returns emptyList()

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(BackupManga(url = "/test/1", title = "Test Manga")),
            backupCategories = emptyList(),
        )

        val captured = entriesSlot.captured.single()
        assertEquals(null, captured.customInfoIncluded)
        assertEquals(null, captured.manga.userTitle)
    }

    @Test
    fun `restore carries the backup custom overrides onto the manga`() = runTest {
        val restorer = createRestorer(isSync = true)
        coEvery { getCategories.await() } returns emptyList()

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(
                BackupManga(
                    url = "/test/1",
                    title = "Test Manga",
                    customTitle = "Edited",
                    customAuthor = "Edited author",
                    customThumbnailUrl = "https://edited.example/cover.jpg",
                    customGenre = listOf("Action"),
                    customStatus = 2,
                    customInfoIncluded = true,
                ),
            ),
            backupCategories = emptyList(),
        )

        val manga = entriesSlot.captured.single().manga
        // The overrides ride on the manga itself; the remote fields keep the source's values.
        assertEquals("Edited", manga.userTitle)
        assertEquals("Edited author", manga.userAuthor)
        assertEquals("https://edited.example/cover.jpg", manga.userThumbnailUrl)
        assertEquals(listOf("Action"), manga.userGenre)
        assertEquals(2L, manga.userStatus)
        assertEquals("Test Manga", manga.ogTitle)
        assertEquals("Edited", manga.title)
    }

    @Test
    fun `restore forwards the merged reference marker`() = runTest {
        val restorer = createRestorer(isSync = true)
        coEvery { getCategories.await() } returns emptyList()

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(
                BackupManga(url = "/merge", title = "Merged", mergedMangaReferencesIncluded = true),
            ),
            backupCategories = emptyList(),
        )

        assertEquals(true, entriesSlot.captured.single().mergedMangaReferencesIncluded)
    }

    @Test
    fun `restore keeps an unmarked merged reference section as unknown`() = runTest {
        val restorer = createRestorer(isSync = true)
        coEvery { getCategories.await() } returns emptyList()

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = true,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(BackupManga(url = "/merge", title = "Merged")),
            backupCategories = emptyList(),
        )

        assertEquals(null, entriesSlot.captured.single().mergedMangaReferencesIncluded)
    }

    /**
     * A pre-split payload stores the merge-level settings on a `manga_source = 6969` self-reference
     * row, which the creator appends last. The children's own `chapterSortMode` is always 0, so
     * taking the first row silently reset every restored merge to "no dedupe".
     */
    @Test
    fun `a pre-split payload takes the sort mode from its self-reference, not the first child`() = runTest {
        val restorer = createRestorer(isSync = false)
        coEvery { getCategories.await() } returns emptyList()

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = false,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(preSplitMerge()),
            backupCategories = emptyList(),
        )

        val settings = entriesSlot.captured.single().mergeSettings!!
        assertEquals(MergeSettings.CHAPTER_SORT_PRIORITY, settings.chapterSortMode)
        // The info flag stays on the child row that carries it, as before the split.
        assertEquals("/child/b", settings.infoMangaUrl)
        assertEquals(SOURCE, settings.infoMangaSourceId)
    }

    /**
     * The same self-reference is not a child of the merge: storing it made the merge list itself,
     * let it pick itself as its info entry, and 61.sqm drops exactly that shape.
     */
    @Test
    fun `a pre-split self-reference is not restored as a child entry`() = runTest {
        val restorer = createRestorer(isSync = false)
        coEvery { getCategories.await() } returns emptyList()

        val entriesSlot = slot<List<RestoredManga>>()
        coEvery {
            restoreRepository.restoreManga(
                capture(entriesSlot),
                isSync = false,
                restoreCategories = true,
                update = any(),
            )
        } returns Unit

        restorer.restore(
            backupMangas = listOf(preSplitMerge()),
            backupCategories = emptyList(),
        )

        val restored = entriesSlot.captured.single()
        assertEquals(listOf("/child/a", "/child/b"), restored.mergedEntries.map { it.mangaUrl })
        assertEquals(listOf(SOURCE, SOURCE), restored.mergedEntries.map { it.mangaSourceId })
    }

    private fun preSplitMerge() = BackupManga(
        url = "/merge",
        title = "Merged",
        mergedEntries = listOf(
            BackupMergedEntry(mangaUrl = "/child/a", mangaSourceId = SOURCE, chapterPriority = 1),
            BackupMergedEntry(
                mangaUrl = "/child/b",
                mangaSourceId = SOURCE,
                chapterPriority = 2,
                isInfoManga = true,
            ),
            // The merge's own row: the only one carrying the merge-level sort mode.
            BackupMergedEntry(
                mangaUrl = "/merge",
                mangaSourceId = MERGED_SOURCE_ID,
                chapterSortMode = MergeSettings.CHAPTER_SORT_PRIORITY,
            ),
        ),
    )

    @Test
    fun `Category nameToUid generates deterministic positive 63-bit UIDs`() {
        val uid1 = Category.nameToUid("Action")
        val uid2 = Category.nameToUid("action ")
        val uid3 = Category.nameToUid("ACTION")

        assertEquals(uid1, uid2)
        assertEquals(uid1, uid3)
        org.junit.jupiter.api.Assertions.assertTrue(uid1 > 0L)
    }

    private companion object {
        const val SOURCE = 10L
    }
}
// KUN <--
