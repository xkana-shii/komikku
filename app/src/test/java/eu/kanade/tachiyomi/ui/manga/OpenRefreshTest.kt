package eu.kanade.tachiyomi.ui.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga

// KNS -->
class OpenRefreshTest {
    private val initialized = Manga.create().copy(id = 1, initialized = true, favorite = true)

    @Test
    fun `both settings are off by default and keep the previous fork's keys`() {
        val preferences = LibraryPreferences(InMemoryPreferenceStore())
        preferences.autoRefreshOpenedManga.get() shouldBe false
        preferences.autoResetChapterFlagsOnRefresh.get() shouldBe false
        preferences.autoRefreshOpenedManga.key() shouldBe "auto_refresh_opened_manga"
        preferences.autoResetChapterFlagsOnRefresh.key() shouldBe "auto_reset_chapter_flags_on_refresh"
    }

    @Test
    fun `auto refresh fetches details and chapters of an initialized entry`() {
        OpenRefresh.of(initialized, hasChapters = true, autoRefresh = true, autoResetFlags = false) shouldBe
            OpenRefresh(fetchDetails = true, fetchChapters = true, resetChapterFlags = false)
    }

    @Test
    fun `without auto refresh an initialized entry with chapters fetches nothing`() {
        OpenRefresh.of(initialized, hasChapters = true, autoRefresh = false, autoResetFlags = true) shouldBe
            OpenRefresh(fetchDetails = false, fetchChapters = false, resetChapterFlags = false)
    }

    @Test
    fun `an uninitialized entry and missing chapters are fetched whatever the setting`() {
        val uninitialized = initialized.copy(initialized = false)
        listOf(true, false).forEach { auto ->
            OpenRefresh.of(uninitialized, hasChapters = true, autoRefresh = auto, autoResetFlags = false).run {
                fetchDetails shouldBe true
                fetchChapters shouldBe false
            }
            OpenRefresh.of(initialized, hasChapters = false, autoRefresh = auto, autoResetFlags = false)
                .fetchChapters shouldBe true
        }
    }

    @Test
    fun `chapter flags only reset with the separate setting or outside the library`() {
        OpenRefresh.of(initialized, hasChapters = true, autoRefresh = true, autoResetFlags = true)
            .resetChapterFlags shouldBe true
        // The reset setting alone does nothing without auto refresh.
        OpenRefresh.of(initialized, hasChapters = true, autoRefresh = false, autoResetFlags = true)
            .resetChapterFlags shouldBe false
        // Existing behaviour: entries outside the library start from the default flags.
        OpenRefresh.of(initialized.copy(favorite = false), hasChapters = true, autoRefresh = false, autoResetFlags = false)
            .resetChapterFlags shouldBe true
    }
}
// KNS <--
