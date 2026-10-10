package tachiyomi.domain.history.interactor

import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.chapter.service.isChapterBlacklisted
import tachiyomi.domain.history.repository.HistoryRepository
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

        return chaptersFor(manga, onlyUnread)
            // KMK --> Keep this after every source-specific branch so History, reader next chapter,
            // and automatic downloads all agree on the same exclusion rule.
            .filterNot { isChapterBlacklisted(it, manga.blacklistedChapters) }
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
        val chapters = await(mangaId, onlyUnread)
        val currChapterIndex = chapters.indexOfFirst { it.id == fromChapterId }
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
