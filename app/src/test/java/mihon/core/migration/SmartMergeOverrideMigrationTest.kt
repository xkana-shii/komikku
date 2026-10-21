package mihon.core.migration

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import mihon.core.migration.migrations.SmartMergeOverrideMigration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

// KNS -->
class SmartMergeOverrideMigrationTest {

    @Test
    fun `old per-manga overrides move to the manga and their keys are removed`() = runBlocking<Unit> {
        val values = mapOf(
            "pref_smart_scanlator_merge_override_7" to "enabled",
            "pref_smart_scanlator_merge_override_8" to "disabled",
            "pref_smart_scanlator_merge_override_9" to "",
            "pref_smart_scanlator_merge" to true,
        )
        val preferences = values.keys.associateWith { key -> mockk<Preference<String>>(relaxed = true) }
        val store = mockk<PreferenceStore>()
        every { store.getAll() } returns values
        values.forEach { (key, value) ->
            every { preferences.getValue(key).get() } returns (value as? String).orEmpty()
            every { store.getString(key, "") } returns preferences.getValue(key)
        }
        val updates = mutableListOf<MangaUpdate>()
        val repository = mockk<MangaRepository>()
        val captured = slot<MangaUpdate>()
        coEvery { repository.update(capture(captured)) } answers {
            updates += captured.captured
            true
        }

        val result = SmartMergeOverrideMigration(store, repository)(MigrationContext(dryrun = false, previousVersion = 172))

        assertTrue(result)
        assertEquals(mapOf(7L to true, 8L to false), updates.associate { it.id to it.smartMergeOverride })
        assertTrue(updates.all { it.isSet(MangaUpdate::smartMergeOverride) })
        listOf(7, 8, 9).forEach { id ->
            verify { preferences.getValue("pref_smart_scanlator_merge_override_$id").delete() }
        }
        // The global switch is not an override and stays.
        verify(exactly = 0) { preferences.getValue("pref_smart_scanlator_merge").delete() }
        coVerify(exactly = 2) { repository.update(any()) }
    }

    @Test
    fun `a failed update keeps the old key for the next start`() = runBlocking<Unit> {
        val key = "pref_smart_scanlator_merge_override_7"
        val preference = mockk<Preference<String>>(relaxed = true)
        every { preference.get() } returns "enabled"
        val store = mockk<PreferenceStore>()
        every { store.getAll() } returns mapOf(key to "enabled")
        every { store.getString(key, "") } returns preference
        val repository = mockk<MangaRepository>()
        coEvery { repository.update(any()) } returns false

        SmartMergeOverrideMigration(store, repository)(MigrationContext(dryrun = false, previousVersion = 172))

        verify(exactly = 0) { preference.delete() }
    }
}
// KNS <--
