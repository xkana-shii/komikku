package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

// KNS -->
/** The Legacy long-strip scale detector route for the current pointer transition. */
internal data class WebtoonScaleGesture(val accepted: Boolean, val quickScale: Boolean) {
    companion object {
        fun resolve(
            doubleTapInProgress: Boolean,
            doubleTapZoomEnabled: Boolean,
            pinchZoomEnabled: Boolean,
        ): WebtoonScaleGesture {
            val quickScale = doubleTapInProgress && doubleTapZoomEnabled
            return WebtoonScaleGesture(
                accepted = quickScale || pinchZoomEnabled,
                quickScale = quickScale,
            )
        }
    }
}
// KNS <--
