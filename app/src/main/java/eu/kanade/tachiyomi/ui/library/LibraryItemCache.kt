package eu.kanade.tachiyomi.ui.library

// KMK --> Small collection-local cache: keys describe inputs, values keep Compose identity.
internal class LibraryItemCache<K, V> {
    private val entries = mutableMapOf<Long, Pair<K, V>>()
    fun retain(ids: Set<Long>) {
        entries.keys.retainAll(ids)
    }
    suspend fun get(id: Long, key: K, create: suspend () -> V): V {
        entries[id]?.takeIf { it.first == key }?.let { return it.second }
        return create().also { entries[id] = key to it }
    }
}
// KMK <--
