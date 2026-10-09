package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class SetMangaChapterFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun awaitSetDownloadedFilter(manga: Manga, flag: Long): Boolean {
        return mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                chapterFlags = manga.chapterFlags.setFlag(flag, Manga.CHAPTER_DOWNLOADED_MASK),
            ),
        )
    }

    suspend fun awaitSetUnreadFilter(manga: Manga, flag: Long): Boolean {
        return mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                chapterFlags = manga.chapterFlags.setFlag(flag, Manga.CHAPTER_UNREAD_MASK),
            ),
        )
    }

    suspend fun awaitSetBookmarkFilter(manga: Manga, flag: Long): Boolean {
        return mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                chapterFlags = manga.chapterFlags.setFlag(flag, Manga.CHAPTER_BOOKMARKED_MASK),
            ),
        )
    }

    suspend fun awaitSetFillermarkFilter(manga: Manga, flag: Long): Boolean {
        return mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                chapterFlags = manga.chapterFlags.setFlag(flag, Manga.CHAPTER_FILLERMARKED_MASK),
            ),
        )
    }

    suspend fun awaitSetDisplayMode(manga: Manga, flag: Long): Boolean {
        return mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                chapterFlags = manga.chapterFlags.setFlag(flag, Manga.CHAPTER_DISPLAY_MASK),
            ),
        )
    }

    suspend fun awaitSetSortingModeOrFlipOrder(manga: Manga, flag: Long): Boolean {
        val newFlags = manga.chapterFlags.let {
            if (manga.sorting == flag) {
                // Just flip the order
                val orderFlag = if (manga.sortDescending()) {
                    Manga.CHAPTER_SORT_ASC
                } else {
                    Manga.CHAPTER_SORT_DESC
                }
                it.setFlag(orderFlag, Manga.CHAPTER_SORT_DIR_MASK)
            } else {
                // Set new flag with ascending order
                it
                    .setFlag(flag, Manga.CHAPTER_SORTING_MASK)
                    .setFlag(Manga.CHAPTER_SORT_ASC, Manga.CHAPTER_SORT_DIR_MASK)
            }
        }
        return mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                chapterFlags = newFlags,
            ),
        )
    }

    suspend fun awaitSetAllFlags(
        mangaIds: List<Long>,
        unreadFilter: Long,
        downloadedFilter: Long,
        bookmarkedFilter: Long,
        fillermarkedFilter: Long,
        sortingMode: Long,
        sortingDirection: Long,
        displayMode: Long,
    ): Boolean {
        val flags = buildAllFlags(
            unreadFilter = unreadFilter,
            downloadedFilter = downloadedFilter,
            bookmarkedFilter = bookmarkedFilter,
            fillermarkedFilter = fillermarkedFilter,
            sortingMode = sortingMode,
            sortingDirection = sortingDirection,
            displayMode = displayMode,
        )
        return mangaRepository.updateAll(mangaIds.map { MangaUpdate(id = it, chapterFlags = flags) })
    }

    // KMK -->
    fun buildAllFlags(
        unreadFilter: Long,
        downloadedFilter: Long,
        bookmarkedFilter: Long,
        fillermarkedFilter: Long,
        sortingMode: Long,
        sortingDirection: Long,
        displayMode: Long,
    ): Long {
        return 0L.setFlag(unreadFilter, Manga.CHAPTER_UNREAD_MASK)
            .setFlag(downloadedFilter, Manga.CHAPTER_DOWNLOADED_MASK)
            .setFlag(bookmarkedFilter, Manga.CHAPTER_BOOKMARKED_MASK)
            .setFlag(fillermarkedFilter, Manga.CHAPTER_FILLERMARKED_MASK)
            .setFlag(sortingMode, Manga.CHAPTER_SORTING_MASK)
            .setFlag(sortingDirection, Manga.CHAPTER_SORT_DIR_MASK)
            .setFlag(displayMode, Manga.CHAPTER_DISPLAY_MASK)
    }
    // KMK <--

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
