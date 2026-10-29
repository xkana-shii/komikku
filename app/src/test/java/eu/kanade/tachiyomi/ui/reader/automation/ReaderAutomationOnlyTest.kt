package eu.kanade.tachiyomi.ui.reader.automation

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

// KNS -->
/**
 * The reader has one automation: this fork's. Komi-kun #80's TachiyomiSY long-strip autoscroll gestures
 * were deliberately left out, so neither their preferences nor their viewer hooks exist, and the KNS
 * automation settings keep their keys and defaults.
 */
class ReaderAutomationOnlyTest {

    @Test
    fun `the KNS automation settings keep their keys and defaults`() {
        val preferences = ReaderPreferences(InMemoryPreferenceStore())
        preferences.autoScroll.key() shouldBe "pref_auto_scroll"
        preferences.autoScrollSpeed.get() shouldBe 5
        preferences.autoFlip.key() shouldBe "pref_auto_flip"
        preferences.autoFlipInterval.get() shouldBe 5
        preferences.automationMaxMinutes.get() shouldBe 15
        preferences.automationMaxChapters.get() shouldBe 5
    }

    @Test
    fun `the TachiyomiSY autoscroll gesture system is absent`() {
        ReaderPreferences::class.java.declaredMethods.map { it.name }
            .filter { it.contains("autoscrollGesture", ignoreCase = true) }
            .shouldBeEmpty()
        WebtoonViewer::class.java.declaredMethods.map { it.name }
            .filter { it.contains("AutoScrollGesture") || it == "performAutoScrollStep" }
            .shouldBeEmpty()
    }
}
// KNS <--
