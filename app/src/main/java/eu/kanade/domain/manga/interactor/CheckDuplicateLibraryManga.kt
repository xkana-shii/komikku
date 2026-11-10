package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.source.local.isLocal
import java.util.concurrent.ConcurrentHashMap

// KNS -->
/** A manga about to be added to the library, with everything known about it, and its likely duplicates. */
data class DuplicateCheck(
    val manga: Manga,
    /** `null` when no chapters are known. */
    val chapterCount: Long?,
    val duplicates: List<MangaWithChapterCount>,
    /** `false` when loading from the source failed or timed out: the result is inconclusive, not a confirmed none. */
    val complete: Boolean = true,
)

/**
 * Looks for library duplicates of a manga that is about to be added.
 *
 * [invoke] only uses what is stored locally (no network), so adding never waits on a source. Missing details
 * only make the match looser (an unknown author cannot disagree), so the local check never misses a duplicate
 * that fuller data would find. [enrich] then loads the incoming entry's details and chapters from its source in
 * the background, for the dialog to show; it never decides whether the dialog appears.
 */
@Inject
class CheckDuplicateLibraryManga(
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
) {

    /**
     * Local only: a stored chapter count and a match against the library, off the main thread.
     *
     * @param library a snapshot shared by the checks of one bulk add, so the library is loaded and normalized once.
     */
    suspend operator fun invoke(
        manga: Manga,
        library: GetDuplicateLibraryManga.LibrarySnapshot? = null,
    ): DuplicateCheck = withContext(Dispatchers.Default) {
        val chapterCount = chapterRepository.countChaptersByMangaId(manga.id).takeIf { it > 0 }
        DuplicateCheck(
            manga = manga,
            chapterCount = chapterCount,
            duplicates = getDuplicateLibraryManga(manga, chapterCount, library),
        )
    }

    /** The library, loaded and normalized once for the checks of one bulk add. */
    suspend fun librarySnapshot(): GetDuplicateLibraryManga.LibrarySnapshot =
        withContext(Dispatchers.Default) { getDuplicateLibraryManga.snapshot() }

    /** Whether [enrich] could add anything to [manga]. */
    fun canEnrich(manga: Manga, chapterCount: Long?): Boolean =
        !manga.favorite && !manga.isLocal() && (needsDetails(manga) || needsChapters(manga, chapterCount))

    /**
     * Loads the incoming entry's missing details and chapters from its source (once per entry: concurrent and
     * repeated requests share one load, and the result is persisted), then re-matches with the fuller data. The
     * enriched duplicates replace [check]'s only when there are some, so a looser earlier match is never hidden.
     */
    suspend fun enrich(check: DuplicateCheck): DuplicateCheck {
        val manga = check.manga
        if (!canEnrich(manga, check.chapterCount)) return check
        val needsChapters = needsChapters(manga, check.chapterCount)
        val result = load(manga, needsDetails(manga), needsChapters)
        val current = runCatching { mangaRepository.getMangaById(manga.id) }.getOrDefault(manga)
        val chapterCount = chapterRepository.countChaptersByMangaId(manga.id).takeIf { it > 0 }
        // A source that really lists no chapters is not asked again this session
        if (needsChapters && result?.isSuccess == true && chapterCount == null) fetchedEmpty.add(manga.id)
        val duplicates = withContext(Dispatchers.Default) { getDuplicateLibraryManga(current, chapterCount) }
        return DuplicateCheck(
            manga = current,
            chapterCount = chapterCount,
            duplicates = duplicates.ifEmpty { check.duplicates },
            complete = result?.isSuccess == true,
        )
    }

    private fun needsDetails(manga: Manga) = !manga.initialized

    private fun needsChapters(manga: Manga, chapterCount: Long?) = chapterCount == null && manga.id !in fetchedEmpty

    /**
     * One request per entry and kind of data at a time. It runs in its own scope, so a dismissed dialog neither
     * cancels it nor loses its result: [UpdateMangaFromRemote] persists it and the next attempt finds it stored.
     * The job is stored before it starts, so its cleanup can never run before it is in the map.
     */
    private suspend fun load(manga: Manga, details: Boolean, chapters: Boolean): Result<*>? {
        val key = Triple(manga.id, details, chapters)
        val job = synchronized(inFlight) {
            inFlight.getOrPut(key) {
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        updateMangaFromRemote(manga, fetchDetails = details, fetchChapters = chapters)
                    } finally {
                        synchronized(inFlight) { inFlight.remove(key) }
                    }
                }
            }
        }
        job.start()
        return withTimeoutOrNull(LOAD_TIMEOUT_MS) { job.await() }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val inFlight = HashMap<Triple<Long, Boolean, Boolean>, Deferred<Result<*>>>()
        val fetchedEmpty: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    }
}
// KNS <--
