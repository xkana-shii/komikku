package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    /** `null` when its chapters could not be loaded. */
    val chapterCount: Long?,
    val duplicates: List<MangaWithChapterCount>,
    /** `false` when loading from the source failed or timed out: the result is inconclusive, not a confirmed none. */
    val complete: Boolean = true,
)

/**
 * Looks for library duplicates of a manga that is about to be added. Browse and search results only carry the
 * little a source lists, so first its details and chapters are loaded from the source (once: nothing is fetched
 * for an entry that already has them), and the duplicates are looked up using that fuller data.
 *
 * Loading is best effort: a source that is slow or fails never blocks adding; the check just runs with what is
 * known.
 */
@Inject
class CheckDuplicateLibraryManga(
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
) {

    /**
     * @param loadDetails `false` to look for duplicates with the data at hand only, e.g. for a manga screen that
     * has just loaded its details.
     */
    suspend operator fun invoke(manga: Manga, loadDetails: Boolean = true): DuplicateCheck {
        var current = manga
        val storedCount = chapterRepository.getChapterByMangaId(manga.id).size.toLong()
        var chapterCount: Long? = storedCount.takeIf { it > 0 }
        var complete = true

        if (loadDetails && !manga.favorite && !manga.isLocal()) {
            // Chapters fetched successfully but empty are remembered for this session; they are not "missing"
            val needsDetails = !manga.initialized
            val needsChapters = storedCount == 0L && manga.id !in fetchedEmpty
            if (needsDetails || needsChapters) {
                val result = load(manga, needsDetails, needsChapters)
                complete = result != null && result.isSuccess
                if (needsChapters && result?.isSuccess == true && storedCount == 0L) {
                    chapterCount = chapterRepository.getChapterByMangaId(manga.id).size.toLong().takeIf { it > 0 }
                    if (chapterCount == null) fetchedEmpty.add(manga.id)
                } else if (needsChapters) {
                    chapterCount = chapterRepository.getChapterByMangaId(manga.id).size.toLong().takeIf { it > 0 }
                }
                // Whatever was stored is kept even on failure; the stored row is the freshest data
                current = runCatching { mangaRepository.getMangaById(manga.id) }.getOrDefault(current)
            }
        }

        return DuplicateCheck(
            manga = current,
            chapterCount = chapterCount,
            duplicates = getDuplicateLibraryManga(current, chapterCount),
            complete = complete,
        )
    }

    /**
     * One request per entry and kind of data at a time: concurrent checks (bulk, repeated taps) share it. It runs
     * in its own scope, so a dismissed dialog neither cancels it nor loses its result — it is persisted by
     * [UpdateMangaFromRemote] and the next attempt finds it stored.
     */
    private suspend fun load(manga: Manga, details: Boolean, chapters: Boolean): Result<*>? {
        val key = Triple(manga.id, details, chapters)
        val job = synchronized(inFlight) {
            inFlight.getOrPut(key) {
                scope.async {
                    try {
                        updateMangaFromRemote(manga, fetchDetails = details, fetchChapters = chapters)
                    } finally {
                        synchronized(inFlight) { inFlight.remove(key) }
                    }
                }
            }
        }
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
