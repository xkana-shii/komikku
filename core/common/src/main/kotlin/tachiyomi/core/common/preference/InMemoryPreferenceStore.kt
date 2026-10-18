package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Local-copy implementation of PreferenceStore mostly for test and preview purposes
 */
class InMemoryPreferenceStore(
    initialPreferences: Sequence<InMemoryPreference<*>> = sequenceOf(),
) : PreferenceStore {

    // KMK --> Shared backing values and one key observer per collector, matching Android.
    private val values = ConcurrentHashMap<String, Any>().apply {
        initialPreferences.forEach { pref -> if (pref.isSet()) put(pref.key(), pref.get() as Any) }
    }
    private val listeners = CopyOnWriteArraySet<(String?) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> preference(key: String, default: T): Preference<T> = object : Preference<T> {
        override fun key() = key
        override fun defaultValue() = default
        override fun get(): T = values[key] as? T ?: default
        override fun isSet() = values.containsKey(key)
        override fun set(value: T) {
            values[key] = value as Any
            listeners.forEach { it(key) }
        }
        override fun delete() {
            values.remove(key)
            listeners.forEach { it(key) }
        }
        override fun changes(): Flow<T> = keyChanges { it == key }.map { get() }
        override fun stateIn(scope: CoroutineScope) = changes().stateIn(scope, SharingStarted.Eagerly, get())
    }

    override fun getString(key: String, defaultValue: String) = preference(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = preference(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = preference(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float) = preference(key, defaultValue)
    override fun getBoolean(key: String, defaultValue: Boolean) = preference(key, defaultValue)
    override fun getStringSet(key: String, defaultValue: Set<String>) = preference(key, defaultValue)
    override fun <T> getObjectFromString(key: String, defaultValue: T, serializer: (T) -> String, deserializer: (String) -> T) =
        preference(key, defaultValue)
    override fun <T> getObjectFromInt(key: String, defaultValue: T, serializer: (T) -> Int, deserializer: (Int) -> T) =
        preference(key, defaultValue)
    override fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ) = preference(key, defaultValue)

    override fun keyChanges(predicate: (String) -> Boolean): Flow<String?> = callbackFlow {
        val listener: (String?) -> Unit = { key ->
            if (key == null || predicate(key)) trySend(key)
        }
        listeners.add(listener)
        trySend(null)
        awaitClose { listeners.remove(listener) }
    }.buffer(Channel.UNLIMITED)

    override fun getAll(): Map<String, *> = values.toMap()
    // KMK <--

    class InMemoryPreference<T>(
        private val key: String,
        private var data: T?,
        private val defaultValue: T,
    ) : Preference<T> {
        override fun key(): String = key

        override fun get(): T = data ?: defaultValue()

        override fun isSet(): Boolean = data != null

        override fun delete() {
            data = null
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flow { data }

        override fun stateIn(scope: CoroutineScope): StateFlow<T> {
            return changes().stateIn(scope, SharingStarted.Eagerly, get())
        }

        override fun set(value: T) {
            data = value
        }
    }
}
