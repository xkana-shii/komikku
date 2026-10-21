package tachiyomi.core.common.preference

interface PreferenceStore {

    fun getString(key: String, defaultValue: String = ""): Preference<String>

    fun getLong(key: String, defaultValue: Long = 0): Preference<Long>

    fun getInt(key: String, defaultValue: Int = 0): Preference<Int>

    fun getFloat(key: String, defaultValue: Float = 0f): Preference<Float>

    fun getBoolean(key: String, defaultValue: Boolean = false): Preference<Boolean>

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Preference<Set<String>>

    fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T>

    fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T>

    fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): Preference<Set<T>>

    fun getAll(): Map<String, *>

    // KNS -->
    /**
     * Writes several values ([Int], [Long], [Float], [String], [Boolean] or a `Set<String>`) at once.
     * A store backed by one file writes them in a single transaction, so a large batch (a backup
     * restore) does not copy the whole store once per value and observers see the values together.
     */
    fun putAll(values: Map<String, Any>) {
        values.forEach { (key, value) ->
            when (value) {
                is Int -> getInt(key).set(value)
                is Long -> getLong(key).set(value)
                is Float -> getFloat(key).set(value)
                is String -> getString(key).set(value)
                is Boolean -> getBoolean(key).set(value)
                is Set<*> -> getStringSet(key).set(value.filterIsInstance<String>().toSet())
                else -> throw IllegalArgumentException("Unsupported preference type for $key")
            }
        }
    }
    // KNS <--
}

fun PreferenceStore.getLongArray(
    key: String,
    defaultValue: List<Long>,
): Preference<List<Long>> {
    return getObjectFromString(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.joinToString(",") },
        deserializer = { it.split(",").mapNotNull { l -> l.toLongOrNull() } },
    )
}

inline fun <reified T : Enum<T>> PreferenceStore.getEnum(
    key: String,
    defaultValue: T,
): Preference<T> {
    return getObjectFromString(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.name },
        deserializer = {
            try {
                enumValueOf(it)
            } catch (_: IllegalArgumentException) {
                defaultValue
            }
        },
    )
}

inline fun <reified T : Enum<T>> PreferenceStore.getEnumSet(
    key: String,
    defaultValue: Set<T>,
): Preference<Set<T>> {
    return getObjectSetFromStringSet(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.name },
        deserializer = {
            try {
                enumValueOf<T>(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        },
    )
}
