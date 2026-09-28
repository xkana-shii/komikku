package eu.kanade.domain.manga.interactor

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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> Bounded, in-memory cache keyed by bindings and effective preference.
class GetSequelPrequel(
    private val manager: TrackerManager = Injekt.get(),
    private val preferences: TrackPreferences = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
) {
    private data class Key(val mangaId: Long, val preferred: Long?, val bindings: List<Pair<Long, Long>>)
    private data class Cached(val time: Long, val entries: List<SequelPrequelEntry>)
    private val cache = linkedMapOf<Key, Cached>()
    private val mutex = Mutex()

    suspend fun await(manga: Manga, tracks: List<Track>, refresh: Boolean = false): List<SequelPrequelEntry> {
        val applicable = tracks.filter { manager.get(it.trackerId)?.isLoggedIn == true }
        val preferred = preferences.resolvePreferredTracker(manga.id, applicable.map { it.trackerId }.toSet())
        val key = Key(manga.id, preferred, applicable.map { it.trackerId to it.remoteId }.sortedBy { it.first })
        val entries = mutex.withLock {
            val cached = cache[key]?.takeIf { System.currentTimeMillis() - it.time < 24 * 60 * 60 * 1000L && !refresh }
            cached?.entries ?: fetch(manga, applicable.sortedBy { it.trackerId != preferred }).also {
                if (it.isNotEmpty()) {
                    cache[key] = Cached(System.currentTimeMillis(), it)
                    if (cache.size > 100) cache.remove(cache.keys.first())
                } else {
                    cache.remove(key)
                }
            }
        }
        if (entries.isEmpty()) return emptyList()
        val favorites = mangaRepository.getFavorites()
        val allTracks = getTracks.await()
        return entries.distinctBy { it.url }.map { entry ->
            val bound = allTracks.filter { it.trackerId == entry.trackerId && it.remoteId == entry.remoteId }
                .sortedByDescending { track -> favorites.any { it.id == track.mangaId } }.firstOrNull()
            val sourceMatch = entry.sourceUrl?.let { mangaRepository.getMangaByUrlAndSourceId(it, manga.source) }
            val titled = favorites.filter { it.title.equals(entry.title, true) }
            val local = bound?.mangaId ?: sourceMatch?.id ?: titled.filter { it.source == manga.source }.singleOrNull()?.id
                ?: titled.singleOrNull()?.id
            entry.copy(localMangaId = local?.takeIf { it != manga.id })
        }
    }

    private suspend fun fetch(manga: Manga, tracks: List<Track>): List<SequelPrequelEntry> {
        var failure: Exception? = null
        for (track in tracks) {
            try {
                val entries = manager.get(track.trackerId)?.getRelatedEntries(track.remoteId).orEmpty()
                if (entries.isNotEmpty()) return entries
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        if (manga.source in mangaDexSourceIds) {
            try {
                val service = MangaDexService(network.client, Headers.headersOf("Referer", "https://mangadex.org/"))
                val pairs = service.relatedManga(MdUtil.getMangaId(manga.url)).data.mapNotNull {
                    val related = it.relationships.firstOrNull()?.id ?: return@mapNotNull null
                    related to SequelPrequelRelation.from(it.attributes.relation)
                }.distinctBy { it.first }.take(100)
                if (pairs.isNotEmpty()) {
                    val titles = service.viewMangas(pairs.map { it.first }).data.associateBy { it.id }
                    return pairs.mapNotNull { (id, relation) ->
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        failure?.let { throw it }
        return emptyList()
    }
}
// KMK <--
