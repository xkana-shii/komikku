package tachiyomi.domain.track.service

import kotlinx.serialization.json.Json

// KNS -->
/**
 * The preferred tracker of each manga, kept as one bounded JSON preference (manga id → tracker id) like
 * the previous personal fork. Malformed or oversized payloads decode as empty.
 */
object PreferredTrackerMap {
    private const val MAX_ENTRIES = 5000
    private const val MAX_LENGTH = 256_000

    fun decode(raw: String): Map<Long, Long> {
        if (raw.isBlank() || raw.length > MAX_LENGTH) return emptyMap()
        return runCatching {
            Json.decodeFromString<Map<String, Long>>(raw).entries.asSequence()
                .mapNotNull { (key, value) -> key.toLongOrNull()?.takeIf { it >= 0 && value > 0 }?.let { it to value } }
                .take(MAX_ENTRIES)
                .toMap()
        }.getOrDefault(emptyMap())
    }

    /** Sets (or with a null [trackerId], clears) the preferred tracker of [mangaId]. */
    fun update(raw: String, mangaId: Long, trackerId: Long?): String {
        val map = decode(raw).toMutableMap()
        map.remove(mangaId)
        if (mangaId >= 0 && trackerId != null && trackerId > 0) map[mangaId] = trackerId
        return Json.encodeToString(
            map.entries.toList().takeLast(MAX_ENTRIES).associate { it.key.toString() to it.value },
        )
    }
}
// KNS <--
