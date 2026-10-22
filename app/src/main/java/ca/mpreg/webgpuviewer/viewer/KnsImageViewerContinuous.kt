@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

// KNS --> Adapted from webgpuviewer 53; keep rendering changes in sync when that dependency updates.

package ca.mpreg.webgpuviewer.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.util.fastCoerceIn
import ca.mpreg.webgpuviewer.NormalMotionDurationScale
import ca.mpreg.webgpuviewer.RequestMaxRefreshRate
import ca.mpreg.webgpuviewer.waitForCleanUp
import ca.mpreg.webgpuviewer.waitForDown
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.MovedSecondTapRoute
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.canPanAtHomeScale
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.movedSecondTapRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun KnsImageViewerContinuous(
    modifier: Modifier = Modifier,
    state: ImageViewerContinuousState,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val flingX = remember { Animatable(0f) }
    val decay = remember(density) { splineBasedDecay<Float>(density) }

    // What the platform considers a fling at all. A fixed figure here was three times this on a
    // 420dpi screen, so an ordinary flick scrolled only as far as the finger dragged it and the
    // strip stopped dead where a list would have carried on.
    val minFlingVelocity = remember(view) {
        android.view.ViewConfiguration.get(view.context).scaledMinimumFlingVelocity.toFloat()
    }

    LaunchedEffect(density) {
        state.density = density
    }

    RequestMaxRefreshRate()

    HdrColorModeHost()

    val surfaceModifier = modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
            val touchSlop = viewConfiguration.touchSlop

            /**
             * Walk a scale that overshot back into bounds about [originX]/[originY] - the
             * zoom's anchor, as fractions of the viewport: [originX] from its centre, since the
             * horizontal offset is centred, [originY] from its top, since the camera is. False
             * if the scale was already fine. Position interpolates on 1/scale so the anchor
             * holds.
             */
            fun snapScaleIntoBounds(originX: Float, originY: Float): Boolean {
                val startScale = state.scale
                val targetScale = startScale.fastCoerceIn(state.minScale, state.maxScale)
                if (targetScale == startScale) return false

                val diffEnd = 1f / targetScale - 1f / startScale
                val startOffsetX = state.offsetX
                val maxOffsetX = max(0f, (targetScale - 1f) / (2f * targetScale))
                val endOffsetX =
                    (startOffsetX + originX * diffEnd).fastCoerceIn(-maxOffsetX, maxOffsetX)
                val endScroll = -originY * diffEnd * state.height

                state.animationJob = scope.launch {
                    state.isScaleAnimating = true
                    var applied = 0f
                    try {
                        animate(
                            0f,
                            1f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = 0.002f,
                            ),
                        ) { t, _ ->
                            val newScale = startScale + (targetScale - startScale) * t
                            val c = ((1f / newScale - 1f / startScale) / diffEnd).fastCoerceIn(
                                0f,
                                1f,
                            )
                            state.scale = newScale
                            state.offsetX = startOffsetX + (endOffsetX - startOffsetX) * c
                            // As a step, so scrollBy keeps its page crossings and clamp.
                            state.scrollBy(endScroll * c - applied)
                            applied = endScroll * c
                            state.invalidate()
                        }
                    } finally {
                        state.isScaleAnimating = false
                        state.invalidate()
                    }
                }
                return true
            }

            /** Walk a pan that overshot back to its edge, the scale being fine. */
            fun snapOffsetXIntoBounds() {
                val maxOffsetX = max(0f, (state.scale - 1f) / (2f * state.scale))
                val clampedX = state.offsetX.fastCoerceIn(-maxOffsetX, maxOffsetX)
                if (clampedX == state.offsetX) return
                state.animationJob = scope.launch {
                    val startX = state.offsetX
                    animate(
                        0f,
                        1f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = 0.002f,
                        ),
                    ) { t, _ ->
                        state.offsetX = startX + (clampedX - startX) * t
                        state.invalidate()
                    }
                }
            }

            awaitEachGesture {
                val firstDown = awaitFirstDown(pass = PointerEventPass.Initial)
                state.animationJob?.cancel()
                view.parent?.requestDisallowInterceptTouchEvent(true)

                // A touch on moving content only stops it - see ImageViewer.kt's copy.
                val stoppedMotion = state.isScaleAnimating || state.isFlinging
                if (stoppedMotion) {
                    state.isScaleAnimating = false
                    state.isFlinging = false
                    state.invalidate()
                }

                var longPressed = false
                val longPressJob = if (stoppedMotion) {
                    null
                } else {
                    scope.launch {
                        delay(viewConfiguration.longPressTimeoutMillis.milliseconds)
                        longPressed = true
                        state.onLongTap?.invoke(
                            Offset(
                                firstDown.position.x / state.width,
                                firstDown.position.y / state.height,
                            ),
                        )
                    }
                }

                var gestureDown = firstDown
                if (waitForCleanUp(firstDown.id, doubleTapTimeout, touchSlop) != null) {
                    longPressJob?.cancel()
                    // A touch that only stopped motion can still be the first of a pair.
                    val secondDown =
                        if (state.doubleTapGesturesEnabled) waitForDown(doubleTapTimeout) else null
                    if (secondDown == null) {
                        if (!stoppedMotion) {
                            state.onTap?.invoke(
                                Offset(
                                    firstDown.position.x / state.width,
                                    firstDown.position.y / state.height,
                                ),
                            )
                        }
                        return@awaitEachGesture
                    }

                    if (waitForCleanUp(secondDown.id, doubleTapTimeout, touchSlop) != null) {
                        if (!state.doubleTapZoomEnabled) return@awaitEachGesture
                        if (!state.atHomeScale) {
                            val py = secondDown.position.y / state.height
                            state.animationJob = scope.launch {
                                state.isScaleAnimating = true
                                try {
                                    val startScale = state.scale
                                    val startOffsetX = state.offsetX
                                    val totalDiff = 1f / state.homeScale - 1f / startScale
                                    val px =
                                        if (totalDiff != 0f) -startOffsetX / totalDiff else 0f
                                    animate(
                                        0f,
                                        1f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            visibilityThreshold = 0.002f,
                                        ),
                                    ) { t, _ ->
                                        val newScale =
                                            startScale + (state.homeScale - startScale) * t
                                        val diff = 1f / newScale - 1f / state.scale
                                        state.offsetX += px * diff
                                        state.scale = newScale
                                        state.scrollBy(-py * diff * state.height)
                                        state.invalidate()
                                    }
                                } finally {
                                    state.isScaleAnimating = false
                                    state.invalidate()
                                }
                            }
                        } else {
                            val px = secondDown.position.x / state.width - 0.5f
                            val py = secondDown.position.y / state.height
                            state.animationJob = scope.launch {
                                state.isScaleAnimating = true
                                try {
                                    val startScale = state.scale
                                    val startOffsetX = state.offsetX
                                    animate(
                                        0f,
                                        1f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            visibilityThreshold = 0.002f,
                                        ),
                                    ) { t, _ ->
                                        val newScale =
                                            startScale + (state.doubleTapScale - startScale) * t
                                        val diff = 1f / newScale - 1f / state.scale
                                        state.offsetX += px * diff
                                        state.scale = newScale
                                        state.scrollBy(-py * diff * state.height)
                                        state.invalidate()
                                    }
                                } finally {
                                    state.isScaleAnimating = false
                                    state.invalidate()
                                }
                            }
                        }
                        return@awaitEachGesture
                    } else if (
                        movedSecondTapRoute(state.doubleTapZoomEnabled) == MovedSecondTapRoute.QUICK_SCALE
                    ) {
                        // Double tap drag: zoom by dragging
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPointerInputChange(secondDown)
                        val dragPointerId = secondDown.id
                        val originalScale = state.scale
                        val originalOffsetX = state.offsetX
                        // The anchoring scroll follows the total scale change, but must go
                        // through scrollBy as a step: only scrollBy crosses pages and clamps.
                        var anchorApplied = 0f
                        fun anchorScroll(target: Float) {
                            state.scrollBy(target - anchorApplied)
                            anchorApplied = target
                        }

                        val px = secondDown.position.x / state.width - 0.5f
                        val py = secondDown.position.y / state.height
                        var totalDeltaY = 0f

                        state.isScaleAnimating = true
                        var willFlingZoom = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change =
                                    event.changes.firstOrNull { it.id == dragPointerId }
                                if (change == null || change.changedToUp() || change.isConsumed) break

                                velocityTracker.addPointerInputChange(change)

                                if (change.positionChanged()) {
                                    val pan = event.calculatePan()
                                    totalDeltaY += pan.y

                                    if (totalDeltaY != 0f) {
                                        val newScale =
                                            originalScale * 10f.pow(2 * totalDeltaY / state.height)
                                        val diff = 1f / newScale - 1f / originalScale
                                        state.scale = newScale
                                        state.offsetX = originalOffsetX + px * diff
                                        anchorScroll(-py * diff * state.height)
                                        state.invalidate()
                                        change.consume()
                                    }
                                }
                            }
                            val dragVelocity = velocityTracker.calculateVelocity()
                            // Before the finally below, so isScaleAnimating has no gap
                            // between this drag ending and its fling starting.
                            willFlingZoom =
                                abs(dragVelocity.y) > 200 && state.scale > state.minScale && state.scale < state.maxScale
                        } finally {
                            if (!willFlingZoom) {
                                state.isScaleAnimating = false
                                state.invalidate()
                            }
                        }

                        val velocity = velocityTracker.calculateVelocity()
                        if (willFlingZoom) {
                            state.animationJob = scope.launch(NormalMotionDurationScale) {
                                try {
                                    Animatable(0f).animateDecay(
                                        velocity.y,
                                        exponentialDecay(),
                                    ) {
                                        val newScale =
                                            (originalScale * 10f.pow(2 * (totalDeltaY + value) / state.height)).fastCoerceIn(
                                                state.minScale,
                                                state.maxScale,
                                            )
                                        val diff = 1f / newScale - 1f / originalScale
                                        if (value != 0f && newScale == state.scale) {
                                            throw KnsFlingStalled()
                                        }
                                        val maxOffsetX =
                                            max(0f, (newScale - 1f) / (2f * newScale))
                                        state.scale = newScale
                                        state.offsetX =
                                            (originalOffsetX + px * diff).fastCoerceIn(
                                                -maxOffsetX,
                                                maxOffsetX,
                                            )
                                        anchorScroll(-py * diff * state.height)
                                        state.invalidate()
                                    }
                                } catch (_: KnsFlingStalled) {
                                } finally {
                                    state.isScaleAnimating = false
                                    state.invalidate()
                                }
                            }
                        } else if (!snapScaleIntoBounds(px, py)) {
                            // Only the pan overshot.
                            snapOffsetXIntoBounds()
                        }

                        return@awaitEachGesture
                    }
                    gestureDown = secondDown
                }
                val velocityTracker = VelocityTracker()
                velocityTracker.addPointerInputChange(gestureDown)

                var single = true
                var zoomOriginX = 0f
                var zoomOriginY = 0f
                var lastMoveTime = gestureDown.uptimeMillis
                var lastEventTime = gestureDown.uptimeMillis

                var canceled = false
                state.isPanning = true
                try {
                    do {
                        val event = awaitPointerEvent()
                        canceled = event.changes.any { it.isConsumed }
                        if (longPressed) break
                        if (!canceled) {
                            val change = event.changes[0]

                            if (event.changes.size > 1 && event.changes.all { it.pressed }) {
                                if (single) {
                                    longPressJob?.cancel()
                                    velocityTracker.resetTracking()
                                }
                                single = false
                            }

                            velocityTracker.addPointerInputChange(change)

                            val pan = event.calculatePan()
                            // With pinch off, multi-pointer movement at home scale is inert.
                            val zoom = if (state.pinchZoomEnabled) event.calculateZoom() else 1f
                            // Any two fingers down: a quiet moment mid-pinch is still
                            // a pinch, so generation stays held off.
                            state.isScaleAnimating =
                                event.changes.size > 1 && event.changes.all { it.pressed }

                            lastEventTime = change.uptimeMillis
                            if (change.positionChanged()) lastMoveTime = change.uptimeMillis

                            if (
                                (pan != Offset.Zero || zoom != 1f) &&
                                (canPanAtHomeScale(single, state.pinchZoomEnabled) || !state.atHomeScale)
                            ) {
                                longPressJob?.cancel()

                                if (zoom != 1f) {
                                    velocityTracker.resetTracking()
                                    val centroid =
                                        event.calculateCentroid(useCurrent = true)
                                    val newScale = state.scale * zoom
                                    val diff = 1f / newScale - 1f / state.scale
                                    val cx = centroid.x / state.width - 0.5f
                                    val cy = centroid.y / state.height
                                    // What the snap-back below anchors to.
                                    zoomOriginX = cx
                                    zoomOriginY = cy
                                    state.offsetX += cx * diff
                                    state.scale = newScale
                                    state.scrollBy(-cy * diff * state.height)
                                }

                                if (single) {
                                    val maxOffsetX =
                                        max(0f, (state.scale - 1f) / (2f * state.scale))
                                    state.offsetX =
                                        (state.offsetX + pan.x / state.width / state.scale).fastCoerceIn(
                                            -maxOffsetX,
                                            maxOffsetX,
                                        )
                                } else {
                                    state.offsetX += pan.x / state.width / state.scale
                                }
                                state.scrollBy(-pan.y / state.scale)
                                state.invalidate()
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                } finally {
                    state.isScaleAnimating = false
                    state.isPanning = false
                    state.invalidate()
                }

                longPressJob?.cancel()
                if (longPressed || canceled) return@awaitEachGesture

                if (!snapScaleIntoBounds(zoomOriginX, zoomOriginY)) {
                    val velocity = velocityTracker.calculateVelocity()
                    // Held still before lifting: no fling, however fast it got there.
                    if ((lastEventTime - lastMoveTime) < 100 &&
                        (abs(velocity.y) > minFlingVelocity || abs(velocity.x) > minFlingVelocity)
                    ) {
                        state.animationJob = scope.launch(NormalMotionDurationScale) {
                            state.isFlinging = true
                            try {
                                val speed = hypot(velocity.x, velocity.y)
                                val dirX = velocity.x / speed
                                val dirY = velocity.y / speed
                                flingX.snapTo(0f)
                                var last = 0f
                                flingX.animateDecay(speed, decay) {
                                    val delta = value - last
                                    last = value
                                    val maxOffsetX =
                                        max(0f, (state.scale - 1f) / (2f * state.scale))
                                    val prevOffsetX = state.offsetX
                                    val prevScrollY = state.scrollY
                                    state.offsetX =
                                        (state.offsetX + dirX * delta / state.width / state.scale).fastCoerceIn(
                                            -maxOffsetX,
                                            maxOffsetX,
                                        )
                                    state.scrollBy(-dirY * delta / state.scale)
                                    state.invalidate()
                                    // Pinned on both axes: the decay would run on
                                    // without moving, swallowing the next tap as
                                    // "mid-fling". It never reverses, so one frame
                                    // settles it - but only one that asked to move.
                                    if (delta != 0f && state.offsetX == prevOffsetX && state.scrollY == prevScrollY) {
                                        throw KnsFlingStalled()
                                    }
                                }
                            } catch (_: KnsFlingStalled) {
                            } finally {
                                // Invalidate so generation resumes without waiting on the
                                // next gesture.
                                state.isFlinging = false
                                state.invalidate()
                            }
                        }
                    } else {
                        snapOffsetXIntoBounds()
                    }
                }
            }
        }

    Box {
        ViewerSurface(surfaceModifier) { surface, width, height ->
            try {
                // Before init: the panel's HDR support gates the decode path, which can reach
                // an image before the first frame.
                attachHdrDisplay(view)
                state.init(scope, surface, width, height)
                surface.onChanged { w, h -> state.resize(w, h) }
                // After init, so Hdr.resolve has run and the surface's capability is known.
                attachHdrSurface(view)
                state.invalidate()
                state.collect()
            } finally {
                attachHdrSurface(null)
                state.cleanup()
            }
        }

        ComposePageHostView(state, Modifier.matchParentSize())
    }
}
// KNS <--
