package eu.kanade.domain.manga.interactor

import eu.kanade.domain.connections.service.WebhookEvent
import eu.kanade.tachiyomi.data.webhook.WebhookNotifier
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime

class UpdateManga(
    private val mangaRepository: MangaRepository,
    private val fetchInterval: FetchInterval,
) {

    suspend fun await(mangaUpdate: MangaUpdate): Boolean {
        val previous = if (mangaUpdate.favorite != null) mangaRepository.getMangaById(mangaUpdate.id) else null
        return mangaRepository.update(mangaUpdate).also { success ->
            if (success && previous != null && previous.favorite != mangaUpdate.favorite) {
                Injekt.get<WebhookNotifier>().notify(if (mangaUpdate.favorite == true) WebhookEvent.MANGA_ADDED else WebhookEvent.MANGA_REMOVED, previous)
            }
        }
    }

    suspend fun awaitAll(mangaUpdates: List<MangaUpdate>): Boolean {
        val previous = mangaUpdates.filter { it.favorite != null }.associate { it.id to mangaRepository.getMangaById(it.id) }
        return mangaRepository.updateAll(mangaUpdates).also { success ->
            if (success) {
                mangaUpdates.forEach { update ->
                    previous[update.id]?.takeIf { it.favorite != update.favorite }?.let {
                        Injekt.get<WebhookNotifier>().notify(if (update.favorite == true) WebhookEvent.MANGA_ADDED else WebhookEvent.MANGA_REMOVED, it)
                    }
                }
            }
        }
    }

    suspend fun awaitUpdateFetchInterval(
        manga: Manga,
        dateTime: ZonedDateTime = ZonedDateTime.now(),
        window: Pair<Long, Long> = fetchInterval.getWindow(dateTime),
    ): Boolean {
        return mangaRepository.update(
            fetchInterval.toMangaUpdate(manga, dateTime, window),
        )
    }

    suspend fun awaitUpdateLastUpdate(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, lastUpdate = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, coverLastModified = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateFavorite(mangaId: Long, favorite: Boolean): Boolean {
        val dateAdded = when (favorite) {
            true -> Instant.now().toEpochMilli()
            false -> 0
        }
        return await(
            MangaUpdate(id = mangaId, favorite = favorite, dateAdded = dateAdded),
        )
    }
}
