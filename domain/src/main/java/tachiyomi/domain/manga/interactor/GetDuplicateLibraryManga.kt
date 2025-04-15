package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title.lowercase())
    }
}
