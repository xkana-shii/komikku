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
    suspend operator fun invoke(manga: Manga, incomingChapterCount: Long? = null): List<MangaWithChapterCount> {
        val incomingTitles = listOfNotNull(manga.title, manga.ogTitle, manga.userTitle)
        val incomingAuthors = listOfNotNull(manga.author, manga.ogAuthor)

        val trackMatches = mangaRepository.getTrackDuplicateMangaIds(manga.id).toSet()
        val levels = HashMap<Long, DuplicateMatcher.MatchLevel>()
        mangaRepository.getDuplicateCandidateKeys(manga.id).forEach { key ->
            val level = if (key.id in trackMatches) {
                DuplicateMatcher.MatchLevel.TRACKER
            } else {
                DuplicateMatcher.matchLevel(incomingTitles, incomingAuthors, key)
            }
            if (level != DuplicateMatcher.MatchLevel.NONE) levels[key.id] = level
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
    // KNS <--
}
