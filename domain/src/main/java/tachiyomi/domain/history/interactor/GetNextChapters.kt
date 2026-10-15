package tachiyomi.domain.history.interactor

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
    /**
     * Finds a readable chapter by walking back through recent History entries. The walk is
     * capped so reselecting History cannot scan the full database.
     */
    suspend fun awaitFirstReadable(scanLimit: Long = DEFAULT_HISTORY_SCAN_LIMIT): Chapter? {
        val checkedMangaIds = mutableSetOf<Long>()

        historyRepository.getRecentHistory(scanLimit).forEach { history ->
            if (!checkedMangaIds.add(history.mangaId)) return@forEach

            val next = await(history.mangaId, history.chapterId, onlyUnread = false)
                .firstOrNull { !it.read }
            if (next != null) return next
        }

        return null
    }
    // KMK <--

    suspend fun await(mangaId: Long, onlyUnread: Boolean = true): List<Chapter> {
        val manga = getManga.await(mangaId) ?: return emptyList()
        // KMK --> Keep History, reader next chapter, and automatic downloads on the same rule.
        val chapters = chaptersFor(manga, onlyUnread = false)
            .filterChapterVisibility(manga, libraryPreferences.smartScanlatorMergeEnabled(manga.id))

        return when {
            !onlyUnread -> chapters
            manga.isEhBasedManga() -> chapters.takeLast(1).takeUnless { it.firstOrNull()?.read == true }.orEmpty()
            else -> chapters.filterNot { it.read }
        }
        // KMK <--
    }

    private suspend fun chaptersFor(manga: Manga, onlyUnread: Boolean): List<Chapter> {
        val mangaId = manga.id

        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            val chapters = getMergedChaptersByMangaId.await(mangaId, applyFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))

            return if (onlyUnread) {
                chapters.filterNot { it.read }
            } else {
                chapters
            }
        }
        if (manga.isEhBasedManga()) {
            val chapters = getChaptersByMangaId.await(mangaId, applyFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))

            return if (onlyUnread) {
                chapters.takeLast(1).takeUnless { it.firstOrNull()?.read == true }.orEmpty()
            } else {
                chapters
            }
        }
        // SY <--

        val chapters = getChaptersByMangaId.await(mangaId, applyFilter = true)
            .sortedWith(getChapterSort(manga, sortDescending = false))

        return if (onlyUnread) {
            chapters.filterNot { it.read }
        } else {
            chapters
        }
    }

    suspend fun await(
        mangaId: Long,
        fromChapterId: Long,
        onlyUnread: Boolean = true,
    ): List<Chapter> {
        // KMK --> Resolve an old History entry to the surviving duplicate without touching History.
        val manga = getManga.await(mangaId) ?: return emptyList()
        val smartMergeEnabled = libraryPreferences.smartScanlatorMergeEnabled(manga.id)
        val allChapters = chaptersFor(manga, onlyUnread = false)
        val visibleChapters = allChapters.filterChapterVisibility(manga, smartMergeEnabled)
        val chapters = when {
            !onlyUnread -> visibleChapters
            manga.isEhBasedManga() -> visibleChapters.takeLast(1).takeUnless { it.firstOrNull()?.read == true }.orEmpty()
            else -> visibleChapters.filterNot { it.read }
        }
        val resolvedChapter = allChapters.resolveVisibleChapter(
            manga,
            smartMergeEnabled,
            fromChapterId,
            visibleChapters,
        )
        val currChapterIndex = resolvedChapter
            ?.let { resolved -> chapters.indexOfFirst { it.id == resolved.id } }
            ?: allChapters.indexOfFirst { it.id == fromChapterId }
                .takeIf { it >= 0 }
                ?.let { historyIndex ->
                    allChapters.drop(historyIndex + 1)
                        .firstNotNullOfOrNull { chapter ->
                            chapters.indexOfFirst { it.id == chapter.id }.takeIf { it >= 0 }
                        }
                }
            ?: return emptyList()
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
