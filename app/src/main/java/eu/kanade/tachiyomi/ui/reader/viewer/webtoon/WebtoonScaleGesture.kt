package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

// KNS -->
/** The Legacy long-strip scale detector route for the current pointer transition. */
internal data class WebtoonScaleGesture(val accepted: Boolean, val quickScale: Boolean) {
    companion object {
        /**
         * Quick scale (double tap, hold the second tap and drag) is a one-finger double-tap-zoom gesture and a pinch
         * is a gesture of two or more fingers governed by pinch-to-zoom. A double tap still being in progress says
         * nothing about a pinch that starts on its held second tap, so the pointer count decides which one this is.
         */
        fun resolve(
            doubleTapInProgress: Boolean,
            doubleTapZoomEnabled: Boolean,
            pinchZoomEnabled: Boolean,
            pointerCount: Int,
        ): WebtoonScaleGesture {
            val quickScale = pointerCount == 1 && doubleTapInProgress && doubleTapZoomEnabled
            val pinch = pointerCount >= 2 && pinchZoomEnabled
            return WebtoonScaleGesture(
                accepted = quickScale || pinch,
                quickScale = quickScale,
            )
        }

        /**
         * Whether a scale step may zoom, checked on every step so a gesture that changes kind half way (a quick scale
         * that gains a second finger) is held to the setting of what it has become.
         */
        fun allowsScale(pointerCount: Int, pinchZoomEnabled: Boolean, quickScaling: Boolean): Boolean {
            return if (pointerCount >= 2) pinchZoomEnabled else quickScaling
        }

        /** Fingers that stay down once the event is handled: the one being lifted by a pointer-up no longer counts. */
        fun effectivePointerCount(pointerCount: Int, isPointerUp: Boolean): Int {
            return if (isPointerUp) pointerCount - 1 else pointerCount
        }
    }
}
// KNS <--
