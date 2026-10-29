package eu.kanade.tachiyomi.ui.reader.viewer

// KNS -->
/** What the pager's SSIV page view is told about zoom for a [ReaderPageImageView.Config]. */
internal data class PagerZoomSettings(
    val zoomEnabled: Boolean,
    val quickScaleEnabled: Boolean,
    val doubleTapZoomsIn: Boolean,
)

/**
 * The Legacy zoom settings as the page views apply them (ported from Houri f7ae110). Every value is derived from the
 * whole config, so applying a config after another one gives the same result as applying it first: the views are
 * rebound, not recreated, and a setting switched off must come back on.
 */
internal object ReaderPageZoom {

    fun pager(config: ReaderPageImageView.Config?) = PagerZoomSettings(
        zoomEnabled = config?.disableZoomIn != true,
        // Quick scale (double tap and drag) is a double-tap-zoom gesture, so it follows that setting
        quickScaleEnabled = config?.doubleTapZoom != false,
        doubleTapZoomsIn = config?.doubleTapZoom != false,
    )

    /** Whether PhotoView, which owns its gestures, may zoom by pinching at all. */
    fun animatedZoomable(config: ReaderPageImageView.Config) = !config.disableZoomIn

    /**
     * The scale a double tap takes an animated page to, or null when the tap must not zoom. Zooming in needs both
     * double-tap zoom and zoom in to be allowed; going back to fit only needs double-tap zoom.
     */
    fun animatedDoubleTapScale(config: ReaderPageImageView.Config, currentScale: Float): Float? {
        return when {
            !config.doubleTapZoom -> null
            currentScale > 1F -> 1F
            config.disableZoomIn -> null
            else -> 2F
        }
    }
}
// KNS <--
