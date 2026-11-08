// KUN -->
package tachiyomi.domain.history.service

import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * The filter is device-local app state: it is never synced and never written to a backup, so a
 * restore cannot touch it. The reset clears both lists and leaves the precedence choice alone,
 * writing an ordinary empty value to each key rather than unsetting it.
 */
class HistoryPreferencesTest {

    @Test
    fun `the category filter preferences are app state`() {
        val preferences = HistoryPreferences(InMemoryPreferenceStore())

        assertTrue(Preference.isAppState(preferences.filterIncludedCategories.key()))
        assertTrue(Preference.isAppState(preferences.filterExcludedCategories.key()))
        assertTrue(Preference.isAppState(preferences.filterCategoryInclusionWins.key()))
    }

    @Test
    fun `clearing the category filter writes explicit empty values`() {
        val store = SerializingStore()
        val preferences = HistoryPreferences(store)
        preferences.filterIncludedCategories.set(listOf(1L, 2L))
        preferences.filterExcludedCategories.set(listOf(3L))
        preferences.filterCategoryInclusionWins.set(true)

        // Positive control: the recorded value is the serialized list shape.
        assertEquals("1,2", store.serialized[preferences.filterIncludedCategories.key()])

        preferences.clearCategoryFilter()

        assertEquals(emptyList<Long>(), preferences.filterIncludedCategories.get())
        assertEquals(emptyList<Long>(), preferences.filterExcludedCategories.get())
        // Both keys still exist, holding the explicit empty string.
        assertTrue(preferences.filterIncludedCategories.isSet())
        assertTrue(preferences.filterExcludedCategories.isSet())
        assertEquals("", store.serialized[preferences.filterIncludedCategories.key()])
        assertEquals("", store.serialized[preferences.filterExcludedCategories.key()])
        // The precedence choice is not the reset's business.
        assertEquals(true, preferences.filterCategoryInclusionWins.get())
    }

    @Test
    fun `clearing the filter twice is stable`() {
        val store = SerializingStore()
        val preferences = HistoryPreferences(store)

        preferences.clearCategoryFilter()
        preferences.clearCategoryFilter()

        assertTrue(preferences.filterIncludedCategories.isSet())
        assertTrue(preferences.filterExcludedCategories.isSet())
        assertEquals("", store.serialized[preferences.filterExcludedCategories.key()])
    }
}

/**
 * Delegating store that keeps the serialized form of every object preference, because
 * [InMemoryPreferenceStore] hands back the deserialized value on [PreferenceStore.getString].
 */
private class SerializingStore(
    private val delegate: PreferenceStore = InMemoryPreferenceStore(),
) : PreferenceStore by delegate {

    val serialized = mutableMapOf<String, String>()

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> {
        val preference = delegate.getObjectFromString(key, defaultValue, serializer, deserializer)
        return object : Preference<T> {
            override fun key() = preference.key()
            override fun get() = preference.get()

            override fun set(value: T) {
                serialized[key] = serializer(value)
                preference.set(value)
            }

            override fun isSet() = preference.isSet()

            override fun delete() {
                serialized.remove(key)
                preference.delete()
            }

            override fun defaultValue() = preference.defaultValue()
            override fun changes() = preference.changes()
            override fun stateIn(scope: CoroutineScope) = preference.stateIn(scope)
        }
    }
}
// KUN <--
