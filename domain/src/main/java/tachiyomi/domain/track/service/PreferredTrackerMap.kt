package tachiyomi.domain.track.service

import kotlinx.serialization.json.Json

// KMK --> Bounded preference payloads, independent of database IDs on other devices.
object PreferredTrackerMap {
    private const val MAX_ENTRIES = 5000
    private const val MAX_LENGTH = 256_000

    fun decode(raw: String): Map<Long, Long> {
        if (raw.length > MAX_LENGTH) return emptyMap()
        return runCatching {
            Json.decodeFromString<Map<String, Long>>(raw).entries.asSequence()
                .mapNotNull { (key, value) -> key.toLongOrNull()?.takeIf { it >= 0 && value > 0 }?.let { it to value } }
                .take(MAX_ENTRIES).toMap()
        }.getOrDefault(emptyMap())
    }

    fun update(raw: String, id: Long, trackerId: Long?): String {
        val map = decode(raw).toMutableMap()
        map.remove(id)
        if (id >= 0 && trackerId != null && trackerId > 0) map[id] = trackerId
        return Json.encodeToString(map.entries.toList().takeLast(MAX_ENTRIES).associate { it.key.toString() to it.value })
    }
}
// KMK <--
