package tachiyomi.domain.history.interactor

import dev.zacsweers.metro.Inject
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.filterChapterVisibility
import tachiyomi.domain.chapter.model.resolveVisibleChapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import kotlin.math.max

// KMK -->
private const val DEFAULT_HISTORY_SCAN_LIMIT = 25L
// KMK <--

@Inject
class GetNextChapters(
    private val getChaptersByMangaId: GetChaptersByMangaId,
    // SY -->
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
    private val getManga: GetManga,
    private val historyRepository: HistoryRepository,
    // KMK -->
    private val libraryPreferences: LibraryPreferences,
) {

    suspend fun await(onlyUnread: Boolean = true): List<Chapter> {
        val history = historyRepository.getLastHistory() ?: return emptyList()
        return await(history.mangaId, history.chapterId, onlyUnread)
    }

    // KMK -->
    /** Finds the first unread chapter while walking a bounded set of recent History entries. */
    suspend fun awaitFirstReadable(scanLimit: Long = DEFAULT_HISTORY_SCAN_LIMIT): Chapter? {
        val checkedMangaIds = mutableSetOf<Long>()
        historyRepository.getRecentHistory(scanLimit).forEach { history ->
            if (!checkedMangaIds.add(history.mangaId)) return@forEach
            await(history.mangaId, history.chapterId, onlyUnread = false)
                .firstOrNull { !it.read }
                ?.let { return it }
        }
        return null
    }
    // KMK <--

    suspend fun await(mangaId: Long, onlyUnread: Boolean = true): List<Chapter> {
        val manga = getManga.await(mangaId) ?: return emptyList()
        // KMK --> History, next chapter, and automatic downloads use the same visibility rule.
        val chapters = chaptersFor(manga).filterChapterVisibility(
            manga,
            libraryPreferences.smartScanlatorMergeEnabled(manga.id),
        )
        return when {
            !onlyUnread -> chapters
            manga.isEhBasedManga() -> chapters.takeLast(1).takeUnless { it.firstOrNull()?.read == true }.orEmpty()
            else -> chapters.filterNot(Chapter::read)
        }
        // KMK <--
    }

    // KMK -->
    private suspend fun chaptersFor(manga: Manga): List<Chapter> {
        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            return getMergedChaptersByMangaId.await(manga.id, applyFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))
        }
        if (manga.isEhBasedManga()) {
            return getChaptersByMangaId.await(manga.id, applyFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))
        }
        // SY <--

        return getChaptersByMangaId.await(manga.id, applyFilter = true)
            .sortedWith(getChapterSort(manga, sortDescending = false))
    }
    // KMK <--

    suspend fun await(
        mangaId: Long,
        fromChapterId: Long,
        onlyUnread: Boolean = true,
    ): List<Chapter> {
        // KMK --> Resolve a hidden History chapter through the complete snapshot.
        val manga = getManga.await(mangaId) ?: return emptyList()
        val smartMergeEnabled = libraryPreferences.smartScanlatorMergeEnabled(manga.id)
        val allChapters = chaptersFor(manga)
        val visibleChapters = allChapters.filterChapterVisibility(manga, smartMergeEnabled)
        val chapters = when {
            !onlyUnread -> visibleChapters
            manga.isEhBasedManga() -> visibleChapters.takeLast(1).takeUnless { it.firstOrNull()?.read == true }.orEmpty()
            else -> visibleChapters.filterNot(Chapter::read)
        }
        val resolvedChapter = allChapters.resolveVisibleChapter(
            manga,
            smartMergeEnabled,
            fromChapterId,
            visibleChapters,
        )
        val currChapterIndex = chapters.indexOfFirst { it.id == resolvedChapter?.id }
        // KMK <--
        val nextChapters = chapters.subList(max(0, currChapterIndex), chapters.size)

        if (onlyUnread) {
            return nextChapters
        }

        // The "next chapter" is either:
        // - The current chapter if it isn't completely read
        // - The chapters after the current chapter if the current one is completely read
        val fromChapter = chapters.getOrNull(currChapterIndex)
        return if (fromChapter != null && !fromChapter.read) {
            nextChapters
        } else {
            nextChapters.drop(1)
        }
    }
}
