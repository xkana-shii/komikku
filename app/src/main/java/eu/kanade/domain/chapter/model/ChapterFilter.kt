package eu.kanade.domain.chapter.model

import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.filterBlacklistedChapters
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.source.local.isLocal

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return an observable of the list of chapters filtered and sorted.
 */
fun List<Chapter>.applyFilters(
    manga: Manga,
    downloadManager: DownloadManager,
    // SY -->
    mergedManga: Map<Long, Manga>,
    // SY <--
    // KMK -->
    bookmarkColorFilter: Set<BookmarkColor> = emptySet(),
    // KMK <--
): List<Chapter> {
    val isLocalManga = manga.isLocal()
    val unreadFilter = manga.unreadFilter
    val downloadedFilter = manga.downloadedFilter
    val bookmarkedFilter = manga.bookmarkedFilter

    return filterBlacklistedChapters(this, manga.blacklistedChapters)
        // KMK --> An uncoloured bookmark is a real selectable color, never an unbookmarked chapter.
        .filter { chapter ->
            bookmarkColorFilter.isEmpty() || (chapter.bookmark && chapter.bookmarkColor in bookmarkColorFilter)
        }
        // KMK <--
        .filter { chapter -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { chapter -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .filter { chapter ->
            // SY -->
            @Suppress("NAME_SHADOWING")
            val manga = mergedManga.getOrElse(chapter.mangaId) { manga }
            // SY <--
            applyFilter(downloadedFilter) {
                val downloaded = downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    chapter.url,
                    // SY -->
                    manga.ogTitle,
                    // SY <--
                    manga.source,
                )
                downloaded || isLocalManga
            }
        }
        .sortedWith(getChapterSort(manga))
}

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return an observable of the list of chapters filtered and sorted.
 */
fun List<ChapterList.Item>.applyFilters(
    manga: Manga,
    // KMK -->
    bookmarkColorFilter: Set<BookmarkColor> = emptySet(),
    // KMK <--
): Sequence<ChapterList.Item> {
    val isLocalManga = manga.isLocal()
    val unreadFilter = manga.unreadFilter
    val downloadedFilter = manga.downloadedFilter
    val bookmarkedFilter = manga.bookmarkedFilter
    val visibleUrls = filterBlacklistedChapters(map { it.chapter }, manga.blacklistedChapters)
        .mapTo(HashSet(), Chapter::url)
    return asSequence()
        .filter { (chapter) -> chapter.url in visibleUrls }
        // KMK -->
        .filter { (chapter) ->
            bookmarkColorFilter.isEmpty() || (chapter.bookmark && chapter.bookmarkColor in bookmarkColorFilter)
        }
        // KMK <--
        .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
        .sortedWith { (chapter1), (chapter2) -> getChapterSort(manga).invoke(chapter1, chapter2) }
}
