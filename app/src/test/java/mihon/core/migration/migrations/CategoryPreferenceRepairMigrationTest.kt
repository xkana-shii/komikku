// KUN -->
package mihon.core.migration.migrations

import eu.kanade.domain.connections.service.ConnectionsPreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import mihon.core.migration.MigrationContext
import mihon.core.migration.nextMigrationTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.category.service.CategoryIdPreferences
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import tachiyomi.domain.updates.service.UpdatesPreferences

/**
 * The one-shot repair removes category ids that no longer exist from the category-id preferences —
 * the seven `Set<String>` filters, the Discord RPC incognito set, the History/Updates/Upcoming
 * `List<Long>` filters the 73f cleanup skipped, and `defaultCategory` — and leaves a clean store
 * untouched, so it is idempotent.
 *
 * The category table is faked: [GetCategories] is the real interactor over a mocked repository whose
 * `getAll()` returns the rows a real database would (the schema seeds the system category, id 0).
 */
class CategoryPreferenceRepairMigrationTest {

    private val store = RepairTrackingStore()
    private val libraryPreferences = LibraryPreferences(store)
    private val downloadPreferences = DownloadPreferences(store)
    private val historyPreferences = HistoryPreferences(store)
    private val updatesPreferences = UpdatesPreferences(store)
    private val upcomingPreferences = UpcomingPreferences(store)
    private val connectionsPreferences = ConnectionsPreferences(store)

    /** Live categories, as `getCategories.await()` returns them: the system category included. */
    private val liveCategories = listOf(
        category(0L, order = -1),
        category(1L, order = 0),
        category(2L, order = 1),
    )

    private val migration = CategoryPreferenceRepairMigration(
        categoryIdPreferences = CategoryIdPreferences(
            libraryPreferences = libraryPreferences,
            downloadPreferences = downloadPreferences,
            historyPreferences = historyPreferences,
            updatesPreferences = updatesPreferences,
            upcomingPreferences = upcomingPreferences,
            connectionsPreferences = connectionsPreferences,
        ),
        getCategories = GetCategories(
            mockk<CategoryRepository>(relaxed = true).also { repository ->
                coEvery { repository.getAll() } returns liveCategories
            },
        ),
    )

    private fun category(id: Long, order: Long) = Category(
        id = id,
        name = "category $id",
        order = order,
        flags = 0L,
        hidden = false,
    )

    private fun seedAll(ids: List<Long>) {
        val stringIds = ids.mapTo(mutableSetOf()) { it.toString() }
        libraryPreferences.updateCategories.set(stringIds)
        libraryPreferences.updateCategoriesExclude.set(stringIds)
        libraryPreferences.filterCategoriesInclude.set(stringIds)
        libraryPreferences.filterCategoriesExclude.set(stringIds)
        downloadPreferences.removeExcludeCategories.set(stringIds)
        downloadPreferences.downloadNewChapterCategories.set(stringIds)
        downloadPreferences.downloadNewChapterCategoriesExclude.set(stringIds)
        connectionsPreferences.discordRPCIncognitoCategories.set(stringIds)
        historyPreferences.filterIncludedCategories.set(ids)
        historyPreferences.filterExcludedCategories.set(ids)
        updatesPreferences.filterIncludedCategories.set(ids)
        updatesPreferences.filterExcludedCategories.set(ids)
        upcomingPreferences.filterIncludedCategories.set(ids)
        upcomingPreferences.filterExcludedCategories.set(ids)
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
        connectionsPreferences.discordRPCIncognitoCategories.get(),
    )

    private fun longFilterValues(): List<List<Long>> = listOf(
        historyPreferences.filterIncludedCategories.get(),
        historyPreferences.filterExcludedCategories.get(),
        updatesPreferences.filterIncludedCategories.get(),
        updatesPreferences.filterExcludedCategories.get(),
        upcomingPreferences.filterIncludedCategories.get(),
        upcomingPreferences.filterExcludedCategories.get(),
    )

    @Test
    fun `stale ids are dropped from every category preference and live ids kept`() = runBlocking {
        // 9 is a deleted category; 0 (system), 1 and 2 are live.
        seedAll(listOf(0L, 1L, 9L))

        assertTrue(migration.invoke(MigrationContext(dryrun = false, previousVersion = 84)))

        stringFilterValues().forEach { assertEquals(setOf("0", "1"), it) }
        longFilterValues().forEach { assertEquals(listOf(0L, 1L), it) }
    }

    @Test
    fun `a stale defaultCategory is deleted`() = runBlocking {
        libraryPreferences.defaultCategory.set(9)

        migration.invoke(MigrationContext(dryrun = false, previousVersion = 84))

        assertFalse(libraryPreferences.defaultCategory.isSet())
        assertEquals(-1, libraryPreferences.defaultCategory.get())
    }

    @Test
    fun `a stale id is dropped from the Discord incognito set and a live id kept`() = runBlocking {
        // 9 is a deleted category; 2 is live. Only the Discord set is seeded, so the assertion
        // cannot be satisfied by another preference.
        connectionsPreferences.discordRPCIncognitoCategories.set(setOf("2", "9"))
        store.writes.clear()

        migration.invoke(MigrationContext(dryrun = false, previousVersion = 84))

        assertEquals(setOf("2"), connectionsPreferences.discordRPCIncognitoCategories.get())
        // It is a category-id preference like the others: one write, for the set that changed.
        assertEquals(listOf("discord_rpc_incognito_categories"), store.writes)
    }

    @Test
    fun `a live defaultCategory is kept without a write`() = runBlocking {
        libraryPreferences.defaultCategory.set(2)
        store.writes.clear()

        migration.invoke(MigrationContext(dryrun = false, previousVersion = 84))

        assertEquals(2, libraryPreferences.defaultCategory.get())
        assertEquals(emptyList<String>(), store.writes)
    }

    @Test
    fun `a second run after a repair writes nothing`() = runBlocking {
        seedAll(listOf(0L, 1L, 9L))
        libraryPreferences.defaultCategory.set(9)
        migration.invoke(MigrationContext(dryrun = false, previousVersion = 84))
        store.writes.clear()

        migration.invoke(MigrationContext(dryrun = false, previousVersion = 84))

        assertEquals(emptyList<String>(), store.writes)
        stringFilterValues().forEach { assertEquals(setOf("0", "1"), it) }
        longFilterValues().forEach { assertEquals(listOf(0L, 1L), it) }
    }

    @Test
    fun `a fresh install with no category preferences is a no-op`() = runBlocking {
        migration.invoke(MigrationContext(dryrun = false, previousVersion = 84))

        // Nothing written at all: the defaults are already "no filter" and "no default category".
        assertEquals(emptyList<String>(), store.writes)
        stringFilterValues().forEach { assertEquals(emptySet<String>(), it) }
        longFilterValues().forEach { assertEquals(emptyList<Long>(), it) }
        assertFalse(libraryPreferences.defaultCategory.isSet())
    }

    @Test
    fun `the repair migration is numbered past the migrations an existing install already ran`() {
        // 85f is `ClearPagePreviewCacheMigration`; the migrator runs only `(applied + 1)..target`, so
        // at 85f or below this migration would never run for an install that already applied it.
        assertEquals(86f, migration.version)
        assertEquals(86, nextMigrationTarget(applied = 85, migrations = listOf(migration)))
    }
}

/**
 * Delegating store that records every write, because [InMemoryPreferenceStore] cannot tell "wrote
 * the same value" from "did not write".
 */
private class RepairTrackingStore(
    private val delegate: PreferenceStore = InMemoryPreferenceStore(),
) : PreferenceStore by delegate {

    val writes = mutableListOf<String>()

    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return track(key, delegate.getInt(key, defaultValue))
    }

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
