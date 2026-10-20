// KUN -->
package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
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
}
// KUN <--
