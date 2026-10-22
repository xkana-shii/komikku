package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

// KNS -->
/**
 * The zoom gestures the High Quality viewers allow, from the same settings the legacy viewers read,
 * so both renderers honour them. Paged: double tap zoom follows "double tap to zoom" (paged), and
 * "disable zoom in" turns pinch and double tap zoom off (a paged page never goes below its home
 * scale, so nothing else zooms). Continuous (long strip): double tap and pinch follow their webtoon
 * settings; "disable zoom out" is applied through the strip's minimum scale.
 */
data class WebGpuZoomGestures(val doubleTapZoom: Boolean, val pinchZoom: Boolean) {

    companion object {
        fun paged(doubleTapZoom: Boolean, disableZoomIn: Boolean) =
            WebGpuZoomGestures(doubleTapZoom = doubleTapZoom && !disableZoomIn, pinchZoom = !disableZoomIn)

        fun continuous(doubleTapZoom: Boolean, pinchZoom: Boolean) =
            WebGpuZoomGestures(doubleTapZoom = doubleTapZoom, pinchZoom = pinchZoom)
    }
}
// KNS <--
