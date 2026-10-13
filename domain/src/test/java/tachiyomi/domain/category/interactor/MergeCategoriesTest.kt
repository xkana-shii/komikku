package tachiyomi.domain.category.interactor

import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.repository.MangaRepository

// KMK -->
class MergeCategoriesTest {

    @Test
    fun `merge reassigns manga membership before deleting sources`() = runTest {
        val repository = mockk<CategoryRepository>(relaxed = true)
        val mangaRepository = mockk<MangaRepository>(relaxed = true)
        val deleteCategory = mockk<DeleteCategory>()
        val libraryManga = mockk<LibraryManga> {
            every { id } returns 99
            every { categories } returns listOf(12)
        }
        coEvery { repository.getAll() } returns listOf(
            category(1, 0),
            category(11, 1),
            category(12, 1),
        )
        coEvery { mangaRepository.getLibraryManga() } returns listOf(libraryManga)
        coEvery { deleteCategory.await(12) } returns DeleteCategory.Result.Success

        val result = MergeCategories(repository, mangaRepository, deleteCategory).await(11, listOf(12))

        result.shouldBeInstanceOf<MergeCategories.Result.Success>()
        coVerify(exactly = 1) { mangaRepository.setMangaCategories(99, listOf(11)) }
        coVerify(exactly = 1) { deleteCategory.await(12) }
    }

    private fun category(id: Long, parentId: Long) = Category(
        id = id,
        name = "category$id",
        order = id,
        flags = 0,
        hidden = false,
        parentId = parentId,
    )
}
// KMK <--
