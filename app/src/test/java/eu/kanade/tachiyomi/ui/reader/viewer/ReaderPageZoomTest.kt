package eu.kanade.tachiyomi.ui.reader.viewer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class ReaderPageZoomTest {

    private fun config(disableZoomIn: Boolean = false, doubleTapZoom: Boolean = true) =
        ReaderPageImageView.Config(zoomDuration = 200, disableZoomIn = disableZoomIn, doubleTapZoom = doubleTapZoom)

    @Test
    fun `pager zoom follows both settings`() {
        ReaderPageZoom.pager(config()) shouldBe PagerZoomSettings(true, true, true)
        // double tap zoom off turns quick scale off as well, pinch stays available
        ReaderPageZoom.pager(config(doubleTapZoom = false)) shouldBe PagerZoomSettings(true, false, false)
        ReaderPageZoom.pager(config(disableZoomIn = true)).zoomEnabled shouldBe false
        ReaderPageZoom.pager(config(disableZoomIn = true, doubleTapZoom = false)) shouldBe
            PagerZoomSettings(false, false, false)
        ReaderPageZoom.pager(null) shouldBe PagerZoomSettings(true, true, true)
    }

    @Test
    fun `rebinding with changed settings gives the same result as a fresh bind`() {
        val locked = ReaderPageZoom.pager(config(disableZoomIn = true, doubleTapZoom = false))
        locked.zoomEnabled shouldBe false
        // the same view bound again after the settings were switched back on
        val rebound = ReaderPageZoom.pager(config())
        rebound shouldBe ReaderPageZoom.pager(config())
        rebound.zoomEnabled shouldBe true
        rebound.quickScaleEnabled shouldBe true
        ReaderPageZoom.animatedZoomable(config(disableZoomIn = true)) shouldBe false
        ReaderPageZoom.animatedZoomable(config()) shouldBe true
    }

    @Test
    fun `an animated page zooms in on double tap only when both settings allow it`() {
        ReaderPageZoom.animatedDoubleTapScale(config(), currentScale = 1f) shouldBe 2f
        ReaderPageZoom.animatedDoubleTapScale(config(), currentScale = 2f) shouldBe 1f
        // double tap zoom off: neither zooming in nor out
        ReaderPageZoom.animatedDoubleTapScale(config(doubleTapZoom = false), currentScale = 1f) shouldBe null
        // disable zoom in: no programmatic zoom in even though double tap zoom is on
        ReaderPageZoom.animatedDoubleTapScale(config(disableZoomIn = true), currentScale = 1f) shouldBe null
        ReaderPageZoom.animatedDoubleTapScale(
            config(disableZoomIn = true, doubleTapZoom = false),
            currentScale = 1f,
        ) shouldBe null
    }

    @Test
    fun `an animated page that is already zoomed can still return to fit`() {
        ReaderPageZoom.animatedDoubleTapScale(config(disableZoomIn = true), currentScale = 1.5f) shouldBe 1f
        ReaderPageZoom.animatedDoubleTapScale(config(doubleTapZoom = false), currentScale = 1.5f) shouldBe null
    }
}
// KNS <--
