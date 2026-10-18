
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

    private val selected = Manga.create().copy(
        id = 1L,
        source = MERGED_SOURCE_ID,
        favorite = true,
    )

    private val unrelated = Manga.create().copy(
        id = 2L,
        source = 1L,
        favorite = true,
    )

    private val dependency = Manga.create().copy(
        id = 3L,
        source = 1L,
        favorite = false,
    )

    private val readOutsideLibrary = Manga.create().copy(
        id = 4L,
        source = 1L,
        favorite = false,
    )

    private val creator = BackupCreator(
        context = mockk(),
        isAutoBackup = false,
        parser = mockk(),
        getFavorites = favorites,
        backupPreferences = mockk(),
        mangaRepository = repository,
        categoriesBackupCreator = mockk(),
        mangaBackupCreator = mockk(),
        preferenceBackupCreator = mockk(),
        extensionStoresBackupCreator = mockk(),
        sourcesBackupCreator = mockk(),
        backupFileValidator = mockk(),
        feedBackupCreator = mockk(),
        savedSearchBackupCreator = mockk(),
        getMergedManga = merged,
        getCategories = categories,
        mangaMergeRepository = mergeRepository,
    )

    init {
        coEvery {
            favorites.await()
        } returns listOf(
            selected,
            unrelated,
        )

        coEvery {
            categories.await(1L)
        } returns listOf(
            Category(
                id = 10L,
                name = "Selected",
                order = 0L,
                flags = 0L,
                hidden = false,
            ),
        )

        coEvery {
            categories.await(2L)
        } returns listOf(
            Category(
                id = 20L,
                name = "Other",
                order = 1L,
                flags = 0L,
                hidden = false,
            ),
        )

        coEvery {
            mergeRepository.getMergedMangaById(1L)
        } returns listOf(
            dependency,
            dependency,
        )

        coEvery {
            merged.await()
        } returns listOf(dependency)

        coEvery {
            repository.getReadMangaNotInLibrary()
        } returns listOf(readOutsideLibrary)
    }

    @Test
    fun `selected merged entry retains dependencies without unrelated entries`() = runTest {
        val result = creator.selectManga(
            BackupOptions(
                includedCategoryIds = setOf(10L),
            ),
        )

        result.map { it.id } shouldBe listOf(1L, 3L)
        result.last().favorite shouldBe false

        coVerify(exactly = 0) {
            repository.getReadMangaNotInLibrary()
        }

        creator.selectManga(
            BackupOptions(
                includedCategoryIds = emptySet(),
            ),
        ) shouldBe emptyList()
    }

    @Test
    fun `unrestricted selection preserves full backup contents`() = runTest {
        creator.selectManga(
            BackupOptions(),
        ).map { it.id } shouldBe listOf(
            1L,
            2L,
            4L,
            3L,
        )
    }

    @Test
    fun `library entries disabled excludes library manga`() = runTest {
        creator.selectManga(
            BackupOptions(
                libraryEntries = false,
            ),
        ) shouldBe emptyList()
    }
}
