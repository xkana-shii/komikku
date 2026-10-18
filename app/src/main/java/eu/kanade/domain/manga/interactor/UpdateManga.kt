package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.time.Clock
import kotlin.time.Instant

@Inject
class UpdateManga(
    private val mangaRepository: MangaRepository,
    private val fetchInterval: FetchInterval,
) {

    suspend fun await(mangaUpdate: MangaUpdate): Boolean {
        return mangaRepository.update(mangaUpdate)
    }

    suspend fun awaitAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return mangaRepository.updateAll(mangaUpdates)
    }

    // KUN -->
    // The fork edits a local entry's source-owned metadata directly, which the case-specific
    // remote update query covers (Mihon only writes it from UpdateMangaFromRemote).
    suspend fun awaitRemote(mangaRemoteUpdate: MangaRemoteUpdate): Boolean {
        return mangaRepository.updateRemote(mangaRemoteUpdate)
    }
    // KUN <--

    suspend fun awaitUpdateFetchInterval(
        manga: Manga,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        dateTime: LocalDateTime = Clock.System.now().toLocalDateTime(timeZone),
        window: ClosedRange<Instant>? = fetchInterval.getWindow(dateTime.date, timeZone),
        foundRelease: Boolean = false,
    ): Boolean {
        return mangaRepository.update(
            fetchInterval.withFetchInterval(manga, dateTime, timeZone, window, foundRelease),
        )
    }

    /**
     * Updates the fetch interval and marks the chapter list as changed in a single write.
     */
    suspend fun awaitUpdateFetchIntervalAndLastUpdate(
        manga: Manga,
        timeZone: TimeZone,
        dateTime: LocalDateTime,
        window: ClosedRange<Instant>?,
        foundRelease: Boolean = false,
    ): Boolean {
        return mangaRepository.update(
            fetchInterval.withFetchInterval(manga, dateTime, timeZone, window, foundRelease)
                .apply { lastUpdate = Clock.System.now() },
        )
    }

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean {
        return mangaRepository.update(
            MangaUpdate(mangaId) {
                coverLastModified = Clock.System.now()
            },
        )
    }

    suspend fun awaitUpdateFavorite(mangaId: Long, favorite: Boolean): Boolean {
        val dateAdded = when (favorite) {
            true -> Clock.System.now()
            false -> null
        }
        return mangaRepository.update(
            MangaUpdate(mangaId) {
                this.favorite = favorite
                this.dateAdded = dateAdded
            },
        )
    }
}
