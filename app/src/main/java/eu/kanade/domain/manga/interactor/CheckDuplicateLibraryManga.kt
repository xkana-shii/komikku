package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.withTimeoutOrNull
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.source.local.isLocal

// KNS -->
/** A manga about to be added to the library, with everything known about it, and its likely duplicates. */
data class DuplicateCheck(
    val manga: Manga,
    /** `null` when its chapters could not be loaded. */
    val chapterCount: Long?,
    val duplicates: List<MangaWithChapterCount>,
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
        var chapterCount: Long? = chapterRepository.getChapterByMangaId(manga.id).size.toLong()
            .takeIf { it > 0 }

        if (loadDetails && !manga.favorite && !manga.isLocal()) {
            val needsDetails = !manga.initialized
            val needsChapters = chapterCount == null
            if (needsDetails || needsChapters) {
                val result = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    updateMangaFromRemote(manga, fetchDetails = needsDetails, fetchChapters = needsChapters)
                }
                result?.getOrNull()?.let { update ->
                    current = update.manga
                }
                if (needsChapters) {
                    chapterCount = chapterRepository.getChapterByMangaId(manga.id).size.toLong()
                        .takeIf { it > 0 }
                }
                // The remote update may not hand the manga back; always prefer the stored, fresh row
                current = runCatching { mangaRepository.getMangaById(manga.id) }.getOrDefault(current)
            }
        }

        return DuplicateCheck(
            manga = current,
            chapterCount = chapterCount,
            duplicates = getDuplicateLibraryManga(current, chapterCount),
        )
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}
// KNS <--
