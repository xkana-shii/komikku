package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import exh.source.MERGED_SOURCE_ID
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository

class BackupSelectionTest {
    private val favorites = mockk<GetFavorites>()
    private val categories = mockk<GetCategories>()
    private val merged = mockk<GetMergedManga>()
    private val mergeRepository = mockk<MangaMergeRepository>()
    private val repository = mockk<MangaRepository>()
    private val selected = Manga.create().copy(id = 1, source = MERGED_SOURCE_ID)
    private val unrelated = Manga.create().copy(id = 2, source = 1)
    private val dependency = Manga.create().copy(id = 3, source = 1)
    private val readOutsideLibrary = Manga.create().copy(id = 4, source = 1)
    private val creator = BackupCreator(
        context = mockk(), isAutoBackup = false, parser = mockk(), getFavorites = favorites,
        backupPreferences = mockk(), mangaRepository = repository, categoriesBackupCreator = mockk(),
        mangaBackupCreator = mockk(), preferenceBackupCreator = mockk(), extensionStoresBackupCreator = mockk(),
        sourcesBackupCreator = mockk(), sourceManager = mockk(), feedBackupCreator = mockk(),
        savedSearchBackupCreator = mockk(), getMergedManga = merged, getCategories = categories, mangaMergeRepository = mergeRepository,
    )

    init {
        coEvery { favorites.await() } returns listOf(selected, unrelated)
        coEvery { categories.await(1) } returns listOf(Category(10, "Selected", 0, 0, false))
        coEvery { categories.await(2) } returns listOf(Category(20, "Other", 1, 0, false))
        coEvery { mergeRepository.getMergedMangaById(1) } returns listOf(dependency, dependency)
        coEvery { merged.await() } returns listOf(dependency)
        coEvery { repository.getReadMangaNotInLibrary() } returns listOf(readOutsideLibrary)
    }

    @Test
    fun `selected merged entry retains dependencies without unrelated library or read entries`() = runTest {
        val result = creator.selectManga(BackupOptions(includedCategoryIds = setOf(10)))
        result.map { it.id } shouldBe listOf(1L, 3L)
        result.last().favorite shouldBe false
        coVerify(exactly = 0) { repository.getReadMangaNotInLibrary() }
        creator.selectManga(BackupOptions(includedCategoryIds = emptySet())) shouldBe emptyList()
    }

    @Test
    fun `unrestricted selection preserves existing full backup contents`() = runTest {
        creator.selectManga(BackupOptions()).map { it.id } shouldBe listOf(1L, 2L, 4L, 3L)
    }
}
