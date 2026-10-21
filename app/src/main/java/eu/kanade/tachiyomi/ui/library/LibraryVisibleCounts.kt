package eu.kanade.tachiyomi.ui.library

import dev.zacsweers.metro.Inject
import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.VisibleChapterStats
import tachiyomi.domain.chapter.model.filterChapterVisibility
import tachiyomi.domain.chapter.model.smartScanlatorMergeEnabled
import tachiyomi.domain.chapter.model.visibleChapterStats
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.repository.MangaRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

// KNS -->
/**
 * Library chapter counts under the app-wide visibility rule. The library view counts every chapter
 * (minus excluded scanlators); for entries with a chapter blacklist or Smart Scanlator Merge the
 * totals, read, bookmark and unread counts are recomputed from the visible chapters, so the badges
 * agree with the chapter list, the reader and History.
 *
 * Only entries with a rule are touched, their chapters are read in one batched query, and the result
 * is cached per entry until its stored counts or its rules change, so one changed chapter or one
 * changed override does not recompute the rest of the library.
 */
@Inject
class LibraryVisibleCounts(
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
) {
    private data class Key(
        val totalChapters: Long,
        val readCount: Long,
        val bookmarkCount: Long,
        val bookmarkReadCount: Long,
        val latestUpload: Instant?,
        val chapterFetchedAt: Instant?,
        val blacklist: List<String>,
        val priority: List<String>,
        val rangeRules: List<String>,
        val smartMerge: Boolean,
    )

    private val cache = ConcurrentHashMap<Long, Pair<Key, VisibleChapterStats>>()

    suspend fun apply(library: List<LibraryManga>, globalSmartMerge: Boolean): List<LibraryManga> {
        val keys = library.mapNotNull { item ->
            val manga = item.manga
            val smartMerge = manga.smartScanlatorMergeEnabled(globalSmartMerge)
            if (manga.blacklistedChapters.isEmpty() && !smartMerge) return@mapNotNull null
            item.id to Key(
                item.totalChapters, item.readCount, item.bookmarkCount, item.bookmarkReadCount,
                item.latestUpload, item.chapterFetchedAt,
                manga.blacklistedChapters, manga.scanlatorPriority, manga.scanlatorRangeRules, smartMerge,
            )
        }.toMap()
        cache.keys.retainAll(keys.keys)
        if (keys.isEmpty()) return library

        val stale = library.filter { item -> keys[item.id]?.let { cache[item.id]?.first != it } == true }
        if (stale.isNotEmpty()) {
            val (merged, regular) = stale.partition { it.manga.source == MERGED_SOURCE_ID }
            val chaptersById: Map<Long, List<Chapter>> = buildMap {
                val ids = regular.map { it.id }
                if (ids.isNotEmpty()) {
                    val excluded = mangaRepository.getExcludedScanlatorsByMangaIds(ids)
                    val grouped = chapterRepository.getChaptersByMangaIds(ids).groupBy { it.mangaId }
                    ids.forEach { id ->
                        val excludedScanlators = excluded[id].orEmpty().toSet()
                        put(id, grouped[id].orEmpty().filterNot { it.scanlator in excludedScanlators })
                    }
                }
                merged.forEach { put(it.id, getMergedChaptersByMangaId.await(it.id, applyFilter = true)) }
            }
            stale.forEach { item ->
                val key = keys.getValue(item.id)
                val chapters = chaptersById[item.id].orEmpty()
                val visible = chapters.filterChapterVisibility(item.manga, key.smartMerge, chapters)
                cache[item.id] = key to visible.visibleChapterStats()
            }
        }

        return library.map { item ->
            val stats = cache[item.id]?.takeIf { keys[item.id] == it.first }?.second ?: return@map item
            item.copy(
                totalChapters = stats.total,
                readCount = stats.read,
                bookmarkCount = stats.bookmarked,
                bookmarkReadCount = stats.bookmarkedRead,
            )
        }
    }
}
// KNS <--
