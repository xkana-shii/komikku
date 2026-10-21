package eu.kanade.domain.manga.interactor

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import exh.md.service.MangaDexService
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import exh.source.mangaDexSourceIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.domain.manga.model.SequelPrequelRelation
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// KNS -->
/**
 * The titles related to a manga (sequels, prequels, side stories, ...), taken from its trackers:
 * the preferred tracker first, then the others until one has relations; MangaDex manga fall back to
 * MangaDex's own relations. Results are cached in memory for a day per manga and set of bindings, and
 * each entry is matched to a local manga when possible (same tracker binding, same source URL, or a
 * unique favorite with the same title).
 */
@SingleIn(AppScope::class)
@Inject
class GetSequelPrequel(
    private val trackerManager: TrackerManager,
    private val trackPreferences: TrackPreferences,
    private val mangaRepository: MangaRepository,
    private val getTracks: GetTracks,
    private val network: NetworkHelper,
) {
    private data class Key(val mangaId: Long, val preferred: Long?, val bindings: List<Pair<Long, Long>>)
    private data class Cached(val time: Instant, val entries: List<SequelPrequelEntry>)

    private val cache = LinkedHashMap<Key, Cached>()
    private val mutex = Mutex()

    suspend fun await(manga: Manga, tracks: List<Track>, refresh: Boolean = false): List<SequelPrequelEntry> {
        val applicable = tracks.filter { trackerManager.get(it.trackerId)?.isLoggedIn == true }
        val preferred = trackPreferences.resolvePreferredTracker(manga.id, applicable.mapTo(mutableSetOf()) { it.trackerId })
        val key = Key(manga.id, preferred, applicable.map { it.trackerId to it.remoteId }.sortedBy { it.first })
        val entries = mutex.withLock {
            val cached = cache[key]?.takeIf { !refresh && Clock.System.now() - it.time < CACHE_DURATION }
            cached?.entries ?: fetch(manga, applicable.sortedBy { it.trackerId != preferred }).also {
                if (it.isNotEmpty()) {
                    cache[key] = Cached(Clock.System.now(), it)
                    if (cache.size > MAX_CACHED) cache.remove(cache.keys.first())
                } else {
                    cache.remove(key)
                }
            }
        }
        if (entries.isEmpty()) return emptyList()
        return matchLocal(manga, entries.distinctBy { it.url })
    }

    private suspend fun matchLocal(manga: Manga, entries: List<SequelPrequelEntry>): List<SequelPrequelEntry> {
        val favorites = mangaRepository.getFavorites()
        val favoriteIds = favorites.mapTo(HashSet()) { it.id }
        val allTracks = getTracks.await()
        return entries.map { entry ->
            val bound = allTracks
                .filter { it.trackerId == entry.trackerId && it.remoteId == entry.remoteId }
                .maxByOrNull { it.mangaId in favoriteIds }
            val sourceMatch = entry.sourceUrl?.let { mangaRepository.getMangaByUrlAndSourceId(it, manga.source) }
            val sameTitle = favorites.filter { it.title.equals(entry.title, ignoreCase = true) }
            val local = bound?.mangaId
                ?: sourceMatch?.id
                ?: sameTitle.singleOrNull { it.source == manga.source }?.id
                ?: sameTitle.singleOrNull()?.id
            val related = local?.takeIf { it != manga.id }
            entry.copy(localMangaId = related, inLibrary = related != null && related in favoriteIds)
        }
    }

    private suspend fun fetch(manga: Manga, tracks: List<Track>): List<SequelPrequelEntry> {
        var failure: Exception? = null
        for (track in tracks) {
            try {
                val entries = trackerManager.get(track.trackerId)?.getRelatedEntries(track.remoteId).orEmpty()
                if (entries.isNotEmpty()) return entries
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        if (manga.source in mangaDexSourceIds) {
            try {
                val entries = fetchMangaDex(manga)
                if (entries.isNotEmpty()) return entries
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        failure?.let { throw it }
        return emptyList()
    }

    private suspend fun fetchMangaDex(manga: Manga): List<SequelPrequelEntry> {
        val service = MangaDexService(network.client, Headers.headersOf("Referer", "https://mangadex.org/"))
        val related = service.relatedManga(MdUtil.getMangaId(manga.url)).data
            .mapNotNull { dto ->
                val id = dto.relationships.firstOrNull()?.id ?: return@mapNotNull null
                id to SequelPrequelRelation.from(dto.attributes.relation)
            }
            .distinctBy { it.first }
            .take(MAX_MANGADEX)
        if (related.isEmpty()) return emptyList()
        val titles = service.viewMangas(related.map { it.first }).data.associateBy { it.id }
        return related.mapNotNull { (id, relation) ->
            val dto = titles[id] ?: return@mapNotNull null
            SequelPrequelEntry(
                title = MdUtil.getTitleFromManga(dto.attributes, "en", true),
                url = "https://mangadex.org/title/$id",
                relation = relation,
                sourceUrl = MdUtil.buildMangaUrl(id),
                coverUrl = dto.relationships.firstOrNull { it.type == MdConstants.Types.coverArt }
                    ?.attributes?.fileName?.let { MdUtil.cdnCoverUrl(id, it) },
            )
        }
    }

    private companion object {
        val CACHE_DURATION = 24.hours
        const val MAX_CACHED = 100
        const val MAX_MANGADEX = 100
    }
}
// KNS <--
