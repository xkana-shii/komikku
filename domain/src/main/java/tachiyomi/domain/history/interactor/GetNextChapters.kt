
package tachiyomi.domain.history.interactor

import dev.zacsweers.metro.Inject
import exh.source.MERGED_SOURCE_ID
import exh.source.isEhBasedManga
import kotlinx.coroutines.flow.first
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
        val history = historyRepository.getLastHistory()
            ?: return emptyList()

        return await(
            mangaId = history.mangaId,
            fromChapterId = history.chapterId,
            onlyUnread = onlyUnread,
        )
    }

    // KMK -->
    /**
     * Finds the first unread continuation among recent History entries.
     *
     * Uses the existing HistoryRepository.getHistory() API rather than
     * referencing a nonexistent getRecentHistory() method.
     *
     * The history list is already ordered by most recent reading
     * activity, with one latest History entry per manga.
     *
     * scanLimit bounds the number of entries examined. The existing
     * repository query still loads its complete result; database-level
     * LIMIT optimization would require a separate repository query.
     */
    suspend fun awaitFirstReadable(
        scanLimit: Long = DEFAULT_HISTORY_SCAN_LIMIT,
    ): Chapter? {
        if (scanLimit <= 0L) return null

        val limit = scanLimit
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        val recentHistory = historyRepository.getHistory(
            query = "",
            unfinishedManga = null,
            unfinishedChapter = null,
            nonLibraryEntries = null,
        )
            .first()
            .take(limit)

        val checkedMangaIds = mutableSetOf<Long>()

        for (history in recentHistory) {
            if (!checkedMangaIds.add(history.mangaId)) {
                continue
            }

            val continuation = await(
                mangaId = history.mangaId,
                fromChapterId = history.chapterId,
                onlyUnread = false,
            )

            val firstUnread = continuation.firstOrNull { !it.read }

            if (firstUnread != null) {
                return firstUnread
            }
        }

        return null
    }
    // KMK <--

    suspend fun await(
        mangaId: Long,
        onlyUnread: Boolean = true,
    ): List<Chapter> {
        val manga = getManga.await(mangaId)
            ?: return emptyList()

        // KMK -->
        // History, next-chapter navigation, and automatic chapter
        // selection use the same visibility rules.
        val chapters = chaptersFor(manga).filterChapterVisibility(
            manga = manga,
            smartScanlatorMergeEnabled = libraryPreferences
                .smartScanlatorMergeEnabled(manga.id),
        )

        return when {
            !onlyUnread -> chapters

            manga.isEhBasedManga() -> {
                chapters
                    .takeLast(1)
                    .takeUnless { it.firstOrNull()?.read == true }
                    .orEmpty()
            }

            else -> chapters.filterNot(Chapter::read)
        }
        // KMK <--
    }

    // KMK -->
    /**
     * Loads and sorts chapters before applying Smart Scanlator Merge
     * and chapter blacklist visibility.
     *
     * Source-level chapter filters are preserved.
     */
    private suspend fun chaptersFor(manga: Manga): List<Chapter> {
        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            return getMergedChaptersByMangaId.await(
                manga.id,
                applyFilter = true,
            ).sortedWith(
                getChapterSort(manga, sortDescending = false),
            )
        }

        if (manga.isEhBasedManga()) {
            return getChaptersByMangaId.await(
                manga.id,
                applyFilter = true,
            ).sortedWith(
                getChapterSort(manga, sortDescending = false),
            )
        }
        // SY <--

        return getChaptersByMangaId.await(
            manga.id,
            applyFilter = true,
        ).sortedWith(
            getChapterSort(manga, sortDescending = false),
        )
    }
    // KMK <--

    suspend fun await(
        mangaId: Long,
        fromChapterId: Long,
        onlyUnread: Boolean = true,
    ): List<Chapter> {
        val manga = getManga.await(mangaId)
            ?: return emptyList()

        // KMK -->
        val smartMergeEnabled = libraryPreferences
            .smartScanlatorMergeEnabled(manga.id)

        val allChapters = chaptersFor(manga)

        if (allChapters.isEmpty()) {
            return emptyList()
        }

        val originalIndex = allChapters.indexOfFirst { chapter ->
            chapter.id == fromChapterId
        }

        if (originalIndex < 0) {
            return emptyList()
        }

        // Resolve chapters hidden by Smart Scanlator Merge to the
        // corresponding visible chapter before continuing.
        val visibleChapters = allChapters.filterChapterVisibility(
            manga = manga,
            smartScanlatorMergeEnabled = smartMergeEnabled,
        )

        val resolvedChapter = allChapters.resolveVisibleChapter(
            manga = manga,
            smartScanlatorMergeEnabled = smartMergeEnabled,
            chapterId = fromChapterId,
            visibleChapters = visibleChapters,
        )

        // If the chapter is hidden by a blacklist and no visible
        // replacement exists, continue after the original position.
        //
        // This prevents History navigation from starting over at
        // Chapter 1 when the recorded chapter was blacklisted.
        val anchorIndex = resolvedChapter
            ?.let { resolved ->
                allChapters.indexOfFirst { it.id == resolved.id }
            }
            ?.takeIf { it >= 0 }
            ?: originalIndex

        val anchorChapterId = resolvedChapter?.id

        val chapters = when {
            !onlyUnread -> visibleChapters

            manga.isEhBasedManga() -> {
                visibleChapters
                    .takeLast(1)
                    .takeUnless { it.firstOrNull()?.read == true }
                    .orEmpty()
            }

            else -> visibleChapters.filterNot(Chapter::read)
        }

        if (chapters.isEmpty()) {
            return emptyList()
        }

        // Preserve the positions of all chapters, including hidden
        // duplicates, so continuation never moves backward when
        // blacklist or Smart Merge visibility removes an entry.
        val chapterPositions = allChapters
            .withIndex()
            .associate { (index, chapter) ->
                chapter.id to index
            }

        return chapters.filter { chapter ->
            val position = chapterPositions[chapter.id]
                ?: return@filter false

            when {
                position > anchorIndex -> true

                position < anchorIndex -> false

                // A currently unread chapter may be resumed.
                // Read chapters are skipped for continuation.
                position == anchorIndex -> {
                    chapter.id == anchorChapterId &&
                        !chapter.read
                }

                else -> false
            }
        }
        // KMK <--
    }
}
