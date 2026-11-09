// KUN -->
package tachiyomi.domain.category.interactor

import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import tachiyomi.domain.updates.service.UpdatesPreferences

/**
 * Category ids of deleted categories are pruned from every filter that stores them, with no write
 * when a preference does not carry the id (so nothing emits).
 */
class PruneCategoryReferencesTest {

    private val store = PruneTrackingStore()
    private val libraryPreferences = LibraryPreferences(store)
    private val downloadPreferences = DownloadPreferences(store)
    private val historyPreferences = HistoryPreferences(store)
    private val updatesPreferences = UpdatesPreferences(store)
    private val upcomingPreferences = UpcomingPreferences(store)
    private val prune = PruneCategoryReferences(
        libraryPreferences = libraryPreferences,
        downloadPreferences = downloadPreferences,
        historyPreferences = historyPreferences,
        updatesPreferences = updatesPreferences,
        upcomingPreferences = upcomingPreferences,
    )

    private fun seedAll(ids: List<String>) {
        val stringIds = ids.toSet()
        val longIds = ids.map { it.toLong() }
        libraryPreferences.updateCategories.set(stringIds)
        libraryPreferences.updateCategoriesExclude.set(stringIds)
        libraryPreferences.filterCategoriesInclude.set(stringIds)
        libraryPreferences.filterCategoriesExclude.set(stringIds)
        downloadPreferences.removeExcludeCategories.set(stringIds)
        downloadPreferences.downloadNewChapterCategories.set(stringIds)
        downloadPreferences.downloadNewChapterCategoriesExclude.set(stringIds)
        historyPreferences.filterIncludedCategories.set(longIds)
        historyPreferences.filterExcludedCategories.set(longIds)
        updatesPreferences.filterIncludedCategories.set(longIds)
        updatesPreferences.filterExcludedCategories.set(longIds)
        upcomingPreferences.filterIncludedCategories.set(longIds)
        upcomingPreferences.filterExcludedCategories.set(longIds)
        store.writes.clear()
    }

    private fun stringFilterValues(): List<Set<String>> = listOf(
        libraryPreferences.updateCategories.get(),
        libraryPreferences.updateCategoriesExclude.get(),
        libraryPreferences.filterCategoriesInclude.get(),
        libraryPreferences.filterCategoriesExclude.get(),
        downloadPreferences.removeExcludeCategories.get(),
        downloadPreferences.downloadNewChapterCategories.get(),
        downloadPreferences.downloadNewChapterCategoriesExclude.get(),
    )

    /** Every `Preference<List<Long>>` the interactor prunes: History, Updates and Upcoming. */
    private fun longFilterValues(): List<List<Long>> = listOf(
        historyPreferences.filterIncludedCategories.get(),
        historyPreferences.filterExcludedCategories.get(),
        updatesPreferences.filterIncludedCategories.get(),
        updatesPreferences.filterExcludedCategories.get(),
        upcomingPreferences.filterIncludedCategories.get(),
        upcomingPreferences.filterExcludedCategories.get(),
    )

    @Test
    fun `prune removes several ids from every category preference`() {
        seedAll(listOf("1", "2", "3"))
        assertEquals(listOf(1L, 2L, 3L), historyPreferences.filterIncludedCategories.get())

        prune.prune(listOf(1L, 2L))

        stringFilterValues().forEach { assertEquals(setOf("3"), it) }
        longFilterValues().forEach { assertEquals(listOf(3L), it) }
    }

    @Test
    fun `prune writes nothing when no preference carries the id`() {
        seedAll(listOf("5"))

        prune.prune(listOf(1L))

        // Not merely "wrote the same value": the store was not touched at all.
        assertEquals(emptyList<String>(), store.writes)
        assertEquals(setOf("5"), libraryPreferences.updateCategories.get())
        assertEquals(listOf(5L), historyPreferences.filterIncludedCategories.get())
    }

    @Test
    fun `prune with an empty collection is a no-op`() {
        seedAll(listOf("1", "2", "3"))

        prune.prune(emptyList())

        assertEquals(emptyList<String>(), store.writes)
        stringFilterValues().forEach { assertEquals(setOf("1", "2", "3"), it) }
        longFilterValues().forEach { assertEquals(listOf(1L, 2L, 3L), it) }
    }
}

/**
 * Delegating store that records every write, because [InMemoryPreferenceStore] cannot tell "wrote
 * the same value" from "did not write".
 */
private class PruneTrackingStore(
    private val delegate: PreferenceStore = InMemoryPreferenceStore(),
) : PreferenceStore by delegate {

    val writes = mutableListOf<String>()

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return track(key, delegate.getStringSet(key, defaultValue))
    }

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> {
        return track(key, delegate.getObjectFromString(key, defaultValue, serializer, deserializer))
    }

    private fun <T> track(key: String, preference: Preference<T>): Preference<T> {
        return object : Preference<T> {
            override fun key() = preference.key()
            override fun get() = preference.get()

            override fun set(value: T) {
                writes += key
                preference.set(value)
            }

            override fun isSet() = preference.isSet()

            override fun delete() {
                writes += key
                preference.delete()
            }

            override fun defaultValue() = preference.defaultValue()
            override fun changes() = preference.changes()
            override fun stateIn(scope: CoroutineScope) = preference.stateIn(scope)
        }
    }
}
// KUN <--
