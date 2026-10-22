package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

// KNS --> Adapted from Houri commit 7e06ee89b854a398988b75001dd29cfa81370b22.
/**
 * Keeps a disabled double-tap gesture, including pointers added to its second touch, away from the
 * WebGPU surface. A standalone pinch is still delivered normally.
 */
internal class WebGpuDoubleTapZoomGateLayout(context: Context) : FrameLayout(context) {

    var shouldSwallowDoubleTap: () -> Boolean = { false }

    private val gate = WebGpuDoubleTapGestureGate()
    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean = gate.onDoubleTap(shouldSwallowDoubleTap())
        },
    )

    fun addViewer(view: View) {
        addView(
            view,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return if (gate.shouldConsume(ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL)) {
            true
        } else {
            super.dispatchTouchEvent(ev)
        }
    }
}

/** Pure state transition used by the Android touch gate and gesture regression tests. */
internal class WebGpuDoubleTapGestureGate {
    private var swallowing = false

    fun onDoubleTap(disabled: Boolean): Boolean {
        if (disabled) swallowing = true
        return disabled
    }

    fun shouldConsume(terminalEvent: Boolean): Boolean {
        val consume = swallowing
        if (terminalEvent) swallowing = false
        return consume
    }
}
// KNS <--
