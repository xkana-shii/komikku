package tachiyomi.domain.chapter.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaDefaultChapterFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(manga: Manga) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setMangaChapterFlags.awaitSetAllFlags(
                    mangaIds = listOf(manga.id),
                    unreadFilter = filterChapterByRead().get(),
                    downloadedFilter = filterChapterByDownloaded().get(),
                    bookmarkedFilter = filterChapterByBookmarked().get(),
                    fillermarkedFilter = filterChapterByFillermarked().get(),
                    sortingMode = sortChapterBySourceOrNumber().get(),
                    sortingDirection = sortChapterByAscendingOrDescending().get(),
                    displayMode = displayChapterByNameOrNumber().get(),
                )
            }
        }
    }

    // KMK -->
    /** Applies one preference snapshot without loading library manga or opening a transaction per entry. */
    suspend fun awaitAll() {
        withNonCancellableContext {
            mangaRepository.updateLibraryChapterFlags(
                setMangaChapterFlags.buildAllFlags(
                    unreadFilter = libraryPreferences.filterChapterByRead().get(),
                    downloadedFilter = libraryPreferences.filterChapterByDownloaded().get(),
                    bookmarkedFilter = libraryPreferences.filterChapterByBookmarked().get(),
                    fillermarkedFilter = libraryPreferences.filterChapterByFillermarked().get(),
                    sortingMode = libraryPreferences.sortChapterBySourceOrNumber().get(),
                    sortingDirection = libraryPreferences.sortChapterByAscendingOrDescending().get(),
                    displayMode = libraryPreferences.displayChapterByNameOrNumber().get(),
                ),
            )
        }
    }

    suspend fun setAsDefault(manga: Manga, applyToExisting: Boolean) {
        withNonCancellableContext {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (applyToExisting) {
                awaitAll()
            }
        }
    }
    // KMK <--
}
