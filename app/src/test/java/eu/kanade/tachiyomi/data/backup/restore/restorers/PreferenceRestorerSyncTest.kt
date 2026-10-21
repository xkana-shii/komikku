// KUN -->
package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import exh.source.EXH_OLD_ID
import exh.source.EXH_SOURCE_ID
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.interactor.GetCategories

// KNS: kept apart from the fork's PreferenceRestorerTest; the restorer writes one batch, so the
// pinned set is returned (null = untouched) and other values reach PreferenceStore.putAll.
class PreferenceRestorerSyncTest {

    private val pinnedKey = SourcePreferences.PINNED_SOURCES_PREF_KEY

    private fun restorer(store: PreferenceStore): PreferenceRestorer {
        return PreferenceRestorer(
            context = mockk<Context>(relaxed = true),
            getCategories = mockk<GetCategories>(),
            preferenceStore = store,
        )
    }

    private fun restorePins(
        store: StringSetPreferenceStore,
        incoming: Set<String>,
        options: RestoreOptions,
    ): Set<String> {
        // KNS -->
        restorer(store).restorePinnedSourcesPreference(pinnedKey, incoming, store, options)
            ?.let { store.getStringSet(pinnedKey).set(it) }
        // KNS <--
        return store.getStringSet(pinnedKey).get()
    }

    @Test
    fun `default options union the synced pins with the local pins`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(store, setOf("synced"), RestoreOptions())

        assertEquals(setOf("local", "synced"), result)
    }

    @Test
    fun `default options keep the local pins when the synced set is empty`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(store, emptySet(), RestoreOptions())

        assertEquals(setOf("local"), result)
    }

    @Test
    fun `skip option leaves the local pins untouched`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(store, setOf("synced"), RestoreOptions(skipPinnedSources = true))

        assertEquals(setOf("local"), result)
    }

    @Test
    fun `skip option ignores an empty synced set`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(store, emptySet(), RestoreOptions(skipPinnedSources = true))

        assertEquals(setOf("local"), result)
    }

    @Test
    fun `replace option sets the synced pins exactly`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(store, setOf("synced"), RestoreOptions(replacePinnedSources = true))

        assertEquals(setOf("synced"), result)
    }

    @Test
    fun `replace option clears the local pins when the synced set is empty`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(store, emptySet(), RestoreOptions(replacePinnedSources = true))

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `sync restore without protocol v2 unions and preserves local pins`() {
        // Drive, WebDAV and SyncYomi v1 keep the remote preference when both sides carry it, so the
        // restore must not treat the synced set as authoritative.
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(
            store,
            setOf("synced"),
            RestoreOptions(skipPinnedSources = false, replacePinnedSources = false),
        )

        assertEquals(setOf("local", "synced"), result)
    }

    @Test
    fun `sync restore without protocol v2 keeps local pins when the synced set is empty`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(
            store,
            emptySet(),
            RestoreOptions(skipPinnedSources = false, replacePinnedSources = false),
        )

        assertEquals(setOf("local"), result)
    }

    @Test
    fun `sync restore on protocol v2 applies the synced set exactly`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(
            store,
            setOf("synced"),
            RestoreOptions(skipPinnedSources = false, replacePinnedSources = true),
        )

        assertEquals(setOf("synced"), result)
    }

    @Test
    fun `replace option still migrates legacy source ids`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(
            store,
            setOf(EXH_OLD_ID.toString()),
            RestoreOptions(replacePinnedSources = true),
        )

        assertEquals(setOf(EXH_SOURCE_ID.toString()), result)
    }

    @Test
    fun `default options still migrate legacy source ids when unioning`() {
        val store = StringSetPreferenceStore(pinnedKey to setOf("local"))

        val result = restorePins(store, setOf(EXH_OLD_ID.toString()), RestoreOptions())

        assertEquals(setOf("local", EXH_SOURCE_ID.toString()), result)
    }

    @Test
    fun `other string set preferences are not affected by the replace option`() = runTest {
        suspend fun restore(replacePinnedSources: Boolean): Set<String> {
            val key = "another_string_set"
            val store = StringSetPreferenceStore(key to setOf("local"))
            restorer(store).restorePreferences(
                listOf(BackupPreference(key, StringSetPreferenceValue(setOf("synced")))),
                store,
                backupCategories = null,
                options = RestoreOptions(replacePinnedSources = replacePinnedSources),
            )
            return store.getStringSet(key).get()
        }

        assertEquals(setOf("synced"), restore(replacePinnedSources = false))
        assertEquals(setOf("synced"), restore(replacePinnedSources = true))
    }

    @Test
    fun `device-local app state is not restored from an app preference payload`() = runTest {
        val key = Preference.appStateKey("sync_pinned_sources")
        val store = mockk<PreferenceStore>(relaxed = true)
        every { store.getAll() } returns mapOf(key to true)

        restorer(store).restorePreferences(
            listOf(BackupPreference(key, BooleanPreferenceValue(true))),
            store,
            backupCategories = null,
            options = RestoreOptions(),
            isAppPreferences = true,
        )

        // KNS --> written through the batch
        verify(exactly = 1) { store.putAll(emptyMap()) }
        // KNS <--
    }

    @Test
    fun `app-state-like source preference keys are still restored`() = runTest {
        // Source preferences are namespaced separately; the app-state guard must not drop them.
        val key = Preference.appStateKey("source_option")
        val store = mockk<PreferenceStore>(relaxed = true)
        every { store.getAll() } returns mapOf(key to true)

        restorer(store).restorePreferences(
            listOf(BackupPreference(key, BooleanPreferenceValue(true))),
            store,
            backupCategories = null,
            options = RestoreOptions(),
            isAppPreferences = false,
        )

        // KNS --> written through the batch
        verify(exactly = 1) { store.putAll(mapOf(key to true)) }
        // KNS <--
    }
}

/**
 * Minimal store backed by a mutable map. [tachiyomi.core.common.preference.InMemoryPreferenceStore]
 * hands out a fresh preference object per lookup, so writes made through the returned object are
 * not observable; these tests need to read back what the restorer wrote.
 */
private class StringSetPreferenceStore(
    vararg initial: Pair<String, Set<String>>,
) : PreferenceStore {

    private val values = initial.toMap().toMutableMap()

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return object : Preference<Set<String>> {
            override fun key() = key
            override fun get() = values[key] ?: defaultValue
            override fun set(value: Set<String>) {
                values[key] = value
            }

            override fun isSet() = key in values
            override fun delete() {
                values.remove(key)
            }

            override fun defaultValue() = defaultValue
            override fun changes(): Flow<Set<String>> = flowOf(get())
            override fun stateIn(scope: CoroutineScope): StateFlow<Set<String>> =
                MutableStateFlow(get())
        }
    }

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defaultValue: String): Preference<String> = unsupported(key)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> = unsupported(key)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> = unsupported(key)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> = unsupported(key)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = unsupported(key)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = unsupported(key)

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> = unsupported(key)

    override fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): Preference<Set<T>> = unsupported(key)

    private fun unsupported(key: String): Nothing = error("Unexpected preference access: $key")
}
// KUN <--
