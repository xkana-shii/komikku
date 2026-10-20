package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.CoverCache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.domain.manga.model.MangaCover
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant
import tachiyomi.domain.manga.model.Manga as DomainManga

class MangaKeyer : Keyer<DomainManga> {
    override fun key(data: DomainManga, options: Options): String {
        return if (data.favorite && data.hasCustomCover()) {
            "${data.id};${data.coverLastModified}"
        } else {
            // KUN -->
            RecoveredCoverKeys.get(data.id, data.thumbnailUrl, data.coverLastModified)
                ?: "${data.thumbnailUrl};${data.coverLastModified}"
            // KUN <--
        }
    }
}

class MangaCoverKeyer(
    private val coverCache: CoverCache,
) : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String {
        return if (data.isMangaFavorite && coverCache.hasCustomCover(data.mangaId)) {
            "${data.mangaId};${data.lastModified}"
        } else {
            // KUN -->
            RecoveredCoverKeys.get(data.mangaId, data.url, data.lastModified)
                ?: "${data.url};${data.lastModified}"
            // KUN <--
        }
    }
}

// KUN -->
/**
 * Remembers the Coil cache key under which an automatically recovered cover was stored.
 *
 * The recovered image is already decoded under the old model's key, while the database changes
 * the URL and leaves the cover timestamp alone. Returning the old key for the rest of the session
 * avoids decoding the same bitmap a second time. The entry stops applying as soon as the timestamp
 * or the URL changes (manual cover edit/refresh), and is gone after a process restart, where the
 * normal URL-based key is used again.
 */
internal object RecoveredCoverKeys {
    private data class Entry(val url: String?, val lastModified: Instant?, val key: String)

    private val entries = ConcurrentHashMap<Long, Entry>()

    fun put(mangaId: Long, url: String?, lastModified: Instant?, key: String) {
        entries[mangaId] = Entry(url, lastModified, key)
    }

    fun get(mangaId: Long, url: String?, lastModified: Instant?): String? {
        val entry = entries[mangaId] ?: return null
        if (entry.lastModified != lastModified || !urlsMatch(entry.url, url)) {
            entries.remove(mangaId, entry)
            return null
        }
        return entry.key
    }

    fun remove(mangaId: Long) {
        entries.remove(mangaId)
    }

    /**
     * Compares the raw strings first: that is the hot path, and parsing a URL on every cover
     * request would be wasteful. Only differing strings are normalized, so URLs that differ only
     * in case or an explicit default port still match.
     */
    private fun urlsMatch(stored: String?, current: String?): Boolean {
        if (stored == current) return true
        val storedUrl = stored?.toHttpUrlOrNull() ?: return false
        val currentUrl = current?.toHttpUrlOrNull() ?: return false
        return storedUrl == currentUrl
    }
}
// KUN <--
