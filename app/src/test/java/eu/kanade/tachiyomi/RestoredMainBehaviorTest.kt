package eu.kanade.tachiyomi

import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.components.languageFlagPrefix
import eu.kanade.presentation.browse.components.languageMark
import eu.kanade.presentation.library.components.SubcategoryRowVisibility
import eu.kanade.presentation.manga.components.ScanlatorStats
import eu.kanade.presentation.manga.components.scanlatorStatsOf
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchViewModel
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuDoubleTapGestureGate
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuZoomGestures
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonScaleGesture
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ScanlatorCoverage
import tachiyomi.domain.source.interactor.UpdateSavedSearch
import tachiyomi.presentation.core.icons.FlagEmoji

// KNS -->
/** Behavior restored from the previous personal fork's `main` (scanlators, Library, Browse, reader, feeds). */
class RestoredMainBehaviorTest {

    // Scanlator chapter counts and coverage --------------------------------------------------------

    @Test
    fun `each scanlator shows all its chapters and the numbers it covers`() {
        val chapters = listOf(1.0, 2.0, 3.0, 5.0).map { chapter(it, "A") } +
            listOf(1.0, 2.0).map { chapter(it, "B") } + chapter(4.0, " ") + chapter(6.0, null)
        scanlatorStatsOf(chapters) shouldBe mapOf(
            "A" to ScanlatorStats(4, ScanlatorCoverage(listOf("1–3", "5"), hiddenCount = 0)),
            "B" to ScanlatorStats(2, ScanlatorCoverage(listOf("1–2"), hiddenCount = 0)),
        )
    }

    // Subcategory row ------------------------------------------------------------------------------

    @Test
    fun `tapping the selected tab toggles the subcategory row, other tabs keep it as it is`() {
        var visible = true
        fun tap(tapped: Int, current: Int): Int? {
            val result = SubcategoryRowVisibility.onTabTap(tapped, current, visible)
            visible = result.rowVisible
            return result.scrollTo
        }
        tap(0, current = 0) shouldBe null
        visible shouldBe false
        // Hidden stays hidden while switching categories.
        tap(2, current = 0) shouldBe 2
        visible shouldBe false
        tap(1, current = 2) shouldBe 1
        visible shouldBe false
        // Only the selected tab shows it again, and hides it again.
        tap(1, current = 1) shouldBe null
        visible shouldBe true
        tap(3, current = 1) shouldBe 3
        visible shouldBe true
        tap(3, current = 3) shouldBe null
        visible shouldBe false
    }

    // Language flags -------------------------------------------------------------------------------

    @Test
    fun `language flags use the existing show_flags setting, on by default`() {
        val store = mockk<PreferenceStore>(relaxed = true)
        val flags = mockk<Preference<Boolean>>()
        every { store.getBoolean("show_flags", true) } returns flags
        UiPreferences(store).showFlags shouldBe flags

        languageFlagPrefix("ja", showFlags = true) shouldBe FlagEmoji.getEmojiLangFlag("ja") + " "
        languageFlagPrefix("ja", showFlags = false) shouldBe ""
        languageMark("pt-BR", showFlags = true) shouldBe FlagEmoji.getEmojiLangFlag("pt-BR")
        languageMark("pt-BR", showFlags = false) shouldBe "PT-BR"
    }

    // High Quality zoom settings -------------------------------------------------------------------

    @Test
    fun `paged High Quality zoom follows double tap zoom and disable zoom in`() {
        WebGpuZoomGestures.paged(doubleTapZoom = true, disableZoomIn = false) shouldBe WebGpuZoomGestures(true, true)
        WebGpuZoomGestures.paged(doubleTapZoom = false, disableZoomIn = false) shouldBe WebGpuZoomGestures(false, true)
        // Disabling zoom in removes every zoom gesture, double tap included even when it is enabled.
        WebGpuZoomGestures.paged(doubleTapZoom = true, disableZoomIn = true) shouldBe WebGpuZoomGestures(false, false)
        WebGpuZoomGestures.paged(doubleTapZoom = false, disableZoomIn = true) shouldBe WebGpuZoomGestures(false, false)
    }

    @Test
    fun `long strip High Quality zoom follows its double tap and pinch settings independently`() {
        listOf(true, false).forEach { doubleTap ->
            listOf(true, false).forEach { pinch ->
                WebGpuZoomGestures.continuous(doubleTap, pinch) shouldBe WebGpuZoomGestures(doubleTap, pinch)
            }
        }
    }

    @Test
    fun `High Quality consumes the whole prohibited second gesture outside the WebGPU surface`() {
        // The renderer skips its double-tap window while its zoom route is disabled.
        WebGpuZoomGestures.continuous(doubleTapZoom = false, pinchZoom = false).run {
            doubleTapGestures(disableDoubleTapGestures = false) shouldBe false
            pinchZoom shouldBe false
        }

        val gate = WebGpuDoubleTapGestureGate()
        gate.onDoubleTap(disabled = true) shouldBe true
        gate.shouldConsume(terminalEvent = false) shouldBe true // second down
        gate.shouldConsume(terminalEvent = false) shouldBe true // added pointer / movement
        gate.shouldConsume(terminalEvent = true) shouldBe true // final up
        gate.shouldConsume(terminalEvent = false) shouldBe false // next independent gesture

        // An allowed double tap and its following events pass through.
        gate.onDoubleTap(disabled = false) shouldBe false
        gate.shouldConsume(terminalEvent = false) shouldBe false

        // Paged "disable zoom in" removes both renderer zoom routes and its pair window.
        WebGpuZoomGestures.paged(doubleTapZoom = true, disableZoomIn = true)
            .doubleTapGestures(disableDoubleTapGestures = false) shouldBe false
        // The general "disable double tap gestures" setting still wins.
        WebGpuZoomGestures.continuous(doubleTapZoom = true, pinchZoom = true)
            .doubleTapGestures(disableDoubleTapGestures = true) shouldBe false
    }

    @Test
    fun `Legacy scale routing keeps double tap zoom and pinch zoom independent`() {
        WebtoonScaleGesture.resolve(true, doubleTapZoomEnabled = true, pinchZoomEnabled = false) shouldBe
            WebtoonScaleGesture(accepted = true, quickScale = true)
        WebtoonScaleGesture.resolve(true, doubleTapZoomEnabled = false, pinchZoomEnabled = true) shouldBe
            WebtoonScaleGesture(accepted = true, quickScale = false)
        WebtoonScaleGesture.resolve(true, doubleTapZoomEnabled = false, pinchZoomEnabled = false) shouldBe
            WebtoonScaleGesture(accepted = false, quickScale = false)
        WebtoonScaleGesture.resolve(false, doubleTapZoomEnabled = true, pinchZoomEnabled = false) shouldBe
            WebtoonScaleGesture(accepted = false, quickScale = false)
        WebtoonScaleGesture.resolve(false, doubleTapZoomEnabled = false, pinchZoomEnabled = true) shouldBe
            WebtoonScaleGesture(accepted = true, quickScale = false)
    }

    // Feeds (saved searches) -----------------------------------------------------------------------

    @Test
    fun `saving under an existing name replaces that saved search, otherwise a new one is added`() {
        val searches = listOf(1L to "Romance", 2L to "Action")
        UpdateSavedSearch.existingFor("Romance", searches) { it.second }?.first shouldBe 1L
        UpdateSavedSearch.existingFor("  romance ", searches) { it.second }?.first shouldBe 1L
        UpdateSavedSearch.existingFor("Drama", searches) { it.second } shouldBe null
    }

    // Global Search categories ---------------------------------------------------------------------

    @Test
    fun `a Global Search category limits the search to its sources, none keeps every source`() {
        val entries = setOf("1|Manga", "2|Manga", "3|Novel", "x|Manga")
        GlobalSearchViewModel.sourcesInCategory("", entries) shouldBe null
        GlobalSearchViewModel.sourcesInCategory("Manga", entries) shouldBe setOf(1L, 2L)
        GlobalSearchViewModel.sourcesInCategory("Novel", entries) shouldBe setOf(3L)
        GlobalSearchViewModel.sourcesInCategory("Webtoon", entries) shouldBe emptySet()
    }

    private fun chapter(number: Double, scanlator: String?) =
        Chapter.create().copy(id = (number * 10).toLong(), chapterNumber = number, scanlator = scanlator)
}
// KNS <--
