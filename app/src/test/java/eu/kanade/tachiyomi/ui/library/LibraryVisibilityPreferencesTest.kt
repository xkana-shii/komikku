package eu.kanade.tachiyomi.ui.library

import android.content.SharedPreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.SmartMergeSettings

// KMK -->
class LibraryVisibilityPreferencesTest {
    @Test
    fun `20129 overrides use one listener ignore navigation and survive delete clear and bursts`() = runTest {
        val values = (1L..20129L).associate { "pref_smart_scanlator_merge_override_$it" to "disabled" as Any }.toMutableMap()
        val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        var reads = 0
        val preferences = mockk<SharedPreferences> {
            every { all } answers {
                reads++
                values.toMap()
            }
            every { registerOnSharedPreferenceChangeListener(any()) } answers {
                listeners.add(firstArg())
                Unit
            }
            every { unregisterOnSharedPreferenceChangeListener(any()) } answers {
                listeners.remove(firstArg())
                Unit
            }
        }
        val libraryPreferences = LibraryPreferences(AndroidPreferenceStore(mockk(), preferences))
        val emissions = mutableListOf<SmartMergeSettings>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            libraryPreferences.smartScanlatorMergeSettings().collect { emissions.add(it) }
        }
        testScheduler.runCurrent()
        listeners.size shouldBe 1
        emissions.last().enabledFor(42) shouldBe false
        fun notify(key: String?) {
            listeners.toList().forEach { it.onSharedPreferenceChanged(preferences, key) }
        }
        val initialReads = reads
        repeat(100) { notify("last_used_category") }
        testScheduler.runCurrent()
        reads shouldBe initialReads
        repeat(1000) { id ->
            values["pref_smart_scanlator_merge_override_${id + 1}"] = "enabled"
            notify("pref_smart_scanlator_merge_override_${id + 1}")
        }
        testScheduler.runCurrent()
        (1L..1000L).all(emissions.last()::enabledFor) shouldBe true
        values.remove("pref_smart_scanlator_merge_override_42")
        notify("pref_smart_scanlator_merge_override_42")
        testScheduler.runCurrent()
        emissions.last().enabledFor(42) shouldBe false
        values.clear()
        values["pref_smart_scanlator_merge"] = true
        notify(null)
        testScheduler.runCurrent()
        emissions.last().enabledFor(42) shouldBe true
        job.cancel()
        testScheduler.runCurrent()
        listeners.size shouldBe 0
        println("20129 overrides: 1 active listener, 100 navigation writes cause 0 snapshot reads, 1000-key burst preserved")
    }
}
// KMK <--
