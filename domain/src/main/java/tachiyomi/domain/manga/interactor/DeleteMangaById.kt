package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.sync.MangaTombstoneRecorder

@Inject
class DeleteMangaById(
    private val mangaRepository: MangaRepository,
    private val tombstones: MangaTombstoneRecorder,
) {

    suspend fun await(id: Long) {
        // Load first: the row is gone after the delete, and a failed delete must not leave a
        // stale tombstone. Recording after a successful delete can at worst miss one tombstone.
        // A row that is already gone has no key left to record, so there is nothing to tombstone.
        val manga = mangaRepository.getMangaByIdOrNull(id) ?: return
        mangaRepository.deleteManga(id)
        tombstones.rememberDeletedManga(manga.source, manga.url)
    }
}
