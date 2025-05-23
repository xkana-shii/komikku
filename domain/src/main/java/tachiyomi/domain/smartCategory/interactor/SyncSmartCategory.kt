package tachiyomi.domain.smartCategory.interactor

import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.smartCategory.model.SmartCategory

class SyncSmartCategory(
    private val getSmartCategory: GetSmartCategory,
    private val getAllManga: GetAllManga,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
) {
    suspend fun await(smartCategory: SmartCategory) {
        val mangaList = getAllManga.await()

        mangaList.forEach { manga ->
            val mangaTags = manga.genre.orEmpty()
            val mangaCategories = getCategories.await(manga.id).map { it.id }.toSet()
            val newMangaCategories = mangaCategories.toMutableSet()

            if (mangaTags.isEmpty() || smartCategory.tags.any { !mangaTags.contains(it) }) {
                newMangaCategories.remove(smartCategory.categoryId)
            } else {
                newMangaCategories.add(smartCategory.categoryId)
            }

            if (newMangaCategories != mangaCategories) {
                setMangaCategories.await(manga.id, newMangaCategories.toList())
            }
        }
    }

    suspend fun await(manga: Manga) {
        val mangaTags = manga.genre.orEmpty()

        val mangaCategories = getCategories.await(manga.id).map { it.id }.toSet()
        val newMangaCategories = mangaCategories.toMutableSet()

        val smartCategories = getSmartCategory.await()

        for (category in smartCategories) {
            if (mangaTags.isEmpty() || category.tags.any { !mangaTags.contains(it) }) {
                newMangaCategories.remove(category.categoryId)
            } else {
                newMangaCategories.add(category.categoryId)
            }
        }

        if (newMangaCategories != mangaCategories) {
            setMangaCategories.await(manga.id, newMangaCategories.toList())
        }
    }
}
