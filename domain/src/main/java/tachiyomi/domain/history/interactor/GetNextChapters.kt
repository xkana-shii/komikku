package tachiyomi.domain.history.interactor

import dev.zacsweers.metro.Inject
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.ChapterVisibility
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetManga
import kotlin.math.max

@Inject
class GetNextChapters(
    private val getChaptersByMangaId: GetChaptersByMangaId,
    // SY -->
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
    private val getManga: GetManga,
    private val historyRepository: HistoryRepository,
    // KNS -->
    private val chapterVisibility: ChapterVisibility,
    // KNS <--
) {

    suspend fun await(onlyUnread: Boolean = true): List<Chapter> {
        val history = historyRepository.getLastHistory() ?: return emptyList()
        return await(history.mangaId, history.chapterId, onlyUnread)
    }

    suspend fun await(mangaId: Long, onlyUnread: Boolean = true): List<Chapter> {
        val manga = getManga.await(mangaId) ?: return emptyList()

        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            val chapters = getMergedChaptersByMangaId.await(mangaId, applyFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))
                // KNS -->
                .let { chapterVisibility.visibleChapters(manga, it) }
            // KNS <--

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
            // KNS -->
            .let { filtered ->
                // Blacklist entries whose URL changed resolve against the complete chapter list.
                val all = if (manga.blacklistedChapters.isNotEmpty()) {
                    getChaptersByMangaId.await(mangaId, applyFilter = false)
                } else {
                    filtered
                }
                chapterVisibility.visibleChapters(manga, filtered, all)
            }
        // KNS <--

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
        // KNS -->
        // History can point at a chapter that is now hidden (blacklisted, or a Smart Merge duplicate).
        // It is not in the visible list, and indexOfFirst's -1 used to restart from the first chapter.
        // Resolve where it sits in the complete list instead; the history record itself is untouched.
        val manga = getManga.await(mangaId) ?: return emptyList()
        var startId = fromChapterId
        if (chapterVisibility.hasRules(manga)) {
            val visible = await(mangaId, onlyUnread = false)
            val ordered = if (manga.source == MERGED_SOURCE_ID) {
                getMergedChaptersByMangaId.await(mangaId, applyFilter = false)
            } else {
                getChaptersByMangaId.await(mangaId, applyFilter = false)
            }.sortedWith(getChapterSort(manga, sortDescending = false))
            when (val start = chapterVisibility.startFor(manga, fromChapterId, ordered, visible)) {
                is ChapterVisibility.Start.Representative -> startId = start.chapterId
                is ChapterVisibility.Start.After -> {
                    // The hidden chapter counts as passed: continue with the next visible one.
                    val next = visible.dropWhile { it.id != start.chapterId }
                    return if (onlyUnread) next.filterNot { it.read } else next
                }
                ChapterVisibility.Start.End -> return emptyList()
                else -> {}
            }
        }
        // KNS <--
        val chapters = await(mangaId, onlyUnread)
        val currChapterIndex = chapters.indexOfFirst {
            it.id == /* KNS --> */ startId /* KNS <-- */
        }
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
