package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.content.Context
import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * Frame layout which contains a [WebtoonRecyclerView]. It's needed to handle touch events,
 * because the recyclerview is scaled and its touch events are translated, which breaks the
 * detectors.
 *
 * TODO consider integrating this class into [WebtoonViewer].
 */
class WebtoonFrame(context: Context) : FrameLayout(context) {

    /**
     * Scale detector, either with pinch or quick scale.
     */
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    /**
     * Fling detector.
     */
    private val flingDetector = GestureDetector(context, FlingListener())

    var doubleTapZoom = true
        set(value) {
            field = value
            recycler?.doubleTapZoom = value
            scaleDetector.isQuickScaleEnabled = value
        }

    // KMK -->
    var pinchToZoom = true
        set(value) {
            field = value
            recycler?.pinchToZoom = value
        }
    // KMK <--

    var zoomOutDisabled = false
        set(value) {
            field = value
            recycler?.zoomOutDisabled = value
        }

    // KNS -->
    /**
     * Fingers on the screen for the event most recently handed to [scaleDetector]. Every scale callback arrives
     * synchronously from inside scaleDetector.onTouchEvent, so at callback time this describes the event that
     * produced it; it is what tells a one-finger quick scale from a pinch (Houri f7ae110).
     */
    private var scalePointerCount = 0
    // KNS <--

    /**
     * Recycler view added in this frame.
     */
    private val recycler: WebtoonRecyclerView?
        get() = getChildAt(0) as? WebtoonRecyclerView

    /**
     * Dispatches a touch event to the detectors.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // KNS -->
        scalePointerCount = WebtoonScaleGesture.effectivePointerCount(
            pointerCount = ev.pointerCount,
            isPointerUp = ev.actionMasked == MotionEvent.ACTION_POINTER_UP,
        )
        // KNS <--
        scaleDetector.onTouchEvent(ev)
        flingDetector.onTouchEvent(ev)

        // Get the bounding box of the recyclerview and translate any motion events to be within it.
        // Used to allow scrolling outside the recyclerview.
        val recyclerRect = Rect()
        recycler?.getHitRect(recyclerRect) ?: return super.dispatchTouchEvent(ev)
        // Shrink the box to account for any rounding issues.
        recyclerRect.inset(1, 1)

        if (recyclerRect.right < recyclerRect.left || recyclerRect.bottom < recyclerRect.top) {
            return super.dispatchTouchEvent(ev)
        }

        ev.setLocation(
            ev.x.coerceIn(recyclerRect.left.toFloat(), recyclerRect.right.toFloat()),
            ev.y.coerceIn(recyclerRect.top.toFloat(), recyclerRect.bottom.toFloat()),
        )
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Scale listener used to delegate events to the recycler view.
     */
    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // KNS --> Refuse the detector before it can scale when neither route is enabled.
            return recycler?.onScaleBegin(scalePointerCount) == true
            // KNS <--
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // KNS -->
            recycler?.onScale(detector.scaleFactor, scalePointerCount)
            // KNS <--
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            recycler?.onScaleEnd()
        }
    }

    /**
     * Fling listener used to delegate events to the recycler view.
     */
    inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            recycler?.onManualScroll()
            return recycler?.zoomFling(velocityX.toInt(), velocityY.toInt()) ?: false
        }
    }
}
