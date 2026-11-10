package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.math.abs

@Inject
class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    // KNS -->
    /**
     * Library entries that are likely the same series as [manga], most likely first. The match uses the titles
     * and authors of both sides and entries tracked under the same remote id (see [DuplicateMatcher]); a better
     * informed [manga] (details loaded from its source) gives a more accurate result.
     *
     * @param incomingChapterCount only used to order candidates that matched equally well.
     */
    suspend operator fun invoke(
        manga: Manga,
        incomingChapterCount: Long? = null,
        library: LibrarySnapshot? = null,
    ): List<MangaWithChapterCount> {
        val incoming = DuplicateMatcher.Normalized(
            listOfNotNull(manga.title, manga.ogTitle, manga.userTitle),
            listOfNotNull(manga.author, manga.ogAuthor),
        )

        val trackMatches = mangaRepository.getTrackDuplicateMangaIds(manga.id).toSet()
        val candidates = library?.candidates
            ?: mangaRepository.getDuplicateCandidateKeys(manga.id).map { DuplicateMatcher.Candidate(it) }
        val levels = HashMap<Long, DuplicateMatcher.MatchLevel>()
        candidates.forEach { candidate ->
            if (candidate.id == manga.id) return@forEach
            val level = if (candidate.id in trackMatches) {
                DuplicateMatcher.MatchLevel.TRACKER
            } else {
                DuplicateMatcher.matchLevel(incoming, candidate.names)
            }
            if (level != DuplicateMatcher.MatchLevel.NONE) levels[candidate.id] = level
        }
        if (levels.isEmpty()) return emptyList()

        return mangaRepository.getLibraryMangaWithChapterCount(levels.keys)
            .distinctBy { it.manga.id }
            .sortedWith(
                compareByDescending<MangaWithChapterCount> { levels.getValue(it.manga.id).rank }
                    .thenBy { if (incomingChapterCount == null) 0L else abs(it.chapterCount - incomingChapterCount) }
                    .thenBy { it.manga.title.lowercase() },
            )
    }

    /**
     * The library's names, loaded and normalized once so that a bulk add can check every selected entry
     * against it. Entries added during the run are not in it; callers take a new one per run.
     */
    suspend fun snapshot(): LibrarySnapshot =
        LibrarySnapshot(mangaRepository.getDuplicateCandidateKeys(-1L).map(DuplicateMatcher::Candidate))

    class LibrarySnapshot(val candidates: List<DuplicateMatcher.Candidate>)
    // KNS <--
}
