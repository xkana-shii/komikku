@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

// KNS --> Adapted from webgpuviewer 53; keep rendering changes in sync when that dependency updates.

package ca.mpreg.webgpuviewer.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastForEach
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import ca.mpreg.webgpuviewer.NormalMotionDurationScale
import ca.mpreg.webgpuviewer.RequestMaxRefreshRate
import ca.mpreg.webgpuviewer.findActivity
import ca.mpreg.webgpuviewer.orZero
import ca.mpreg.webgpuviewer.renderer.Hdr
import ca.mpreg.webgpuviewer.waitForCleanUp
import ca.mpreg.webgpuviewer.waitForDown
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.MovedSecondTapRoute
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.canPanAtHomeScale
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.movedSecondTapRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun KnsImageViewer(
    modifier: Modifier = Modifier,
    state: ImageViewerState,
) {
    val scope = rememberCoroutineScope()

    val fling = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    val view = LocalView.current
    val density = LocalDensity.current

    // The platform's bar for "this gesture was a fling", the same one a scrolling list uses. A
    // fixed figure here was several times it, so a gentle pan across a zoomed page stopped dead
    // the moment the finger lifted.
    val minFlingVelocity = remember(view) {
        android.view.ViewConfiguration.get(view.context).scaledMinimumFlingVelocity.toFloat()
    }
    val context = LocalContext.current

    val unfolded by produceState(false, context) {
        val activity = context.findActivity() ?: return@produceState
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            value = info.displayFeatures.any { it is FoldingFeature }
        }
    }
    val statusBarPx = remember(context) {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id != 0) context.resources.getDimensionPixelSize(id).toFloat() else 0f
    }
    val cutoutPx = WindowInsets.displayCutout.getTop(density).let { px ->
        if (px == 0 && unfolded) statusBarPx else px.toFloat()
    }

    LaunchedEffect(density) {
        state.density = density
    }

    LaunchedEffect(state.avoidCutout, cutoutPx) {
        state.cutoutTopPx = if (state.avoidCutout) cutoutPx else 0f
    }

    RequestMaxRefreshRate()

    HdrColorModeHost()

    // SurfaceView or TextureView depending on whether HDR is reachable - see [ViewerSurface],
    // which also carries the mihonapp/mihon#3773 history behind that choice.
    val surfaceModifier = modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
            val touchSlop = viewConfiguration.touchSlop

            awaitEachGesture {
                val firstDown = awaitFirstDown(pass = PointerEventPass.Initial)
                val wasScrolling = state.pageOffset != 0f
                val pageTurnJob = state.animationJob
                val page = state.getPage(0) ?: return@awaitEachGesture
                page.animationJob?.cancel()

                view.parent?.requestDisallowInterceptTouchEvent(true)

                // A touch on a moving page only stops it: no long press, no tap, but still a
                // double tap, drag or pinch. Cleared here, not in the cancelled job's finally,
                // which runs late enough to swallow the next touch too.
                val stoppedMotion = page.isScaleAnimating || page.isFlinging
                if (stoppedMotion) {
                    page.isScaleAnimating = false
                    page.isFlinging = false
                    page.invalidate()
                }

                var longPressed = false

                val edgeThreshold = 50f
                val nearEdge =
                    firstDown.position.x < edgeThreshold || firstDown.position.x > state.width - edgeThreshold
                val longPressJob = if (!nearEdge && !stoppedMotion) {
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
                } else {
                    null
                }

                var gestureDown = firstDown
                if (waitForCleanUp(firstDown.id, doubleTapTimeout, touchSlop) != null) {
                    longPressJob?.cancel()
                    // A stop settles below and fires no tap, but still waits out the double
                    // tap window: it can be the first of a pair.
                    val secondDown =
                        if (state.doubleTapGesturesEnabled) waitForDown(doubleTapTimeout) else null
                    if (secondDown == null) {
                        pageTurnJob?.cancel()
                        if (state.pageOffset != 0f) {
                            state.animationJob = scope.launch {
                                Animatable(state.pageOffset).animateTo(
                                    0f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = 0.002f,
                                    ),
                                ) {
                                    state.pageOffset = value
                                    state.invalidate()
                                }
                            }
                        }
                        page.animateTo(Offset(0.5f, 0.5f))
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
                        // double tap — let any in-progress page turn finish committing first
                        val tapX = secondDown.position.x / state.width
                        val tapY = secondDown.position.y / state.height
                        scope.launch {
                            pageTurnJob?.join()
                            val zoomPage = state.getPage(0) ?: return@launch
                            if (zoomPage.atHomeScale) {
                                zoomPage.animateTo(
                                    Offset(tapX, tapY),
                                    targetScale = zoomPage.doubleTapScale,
                                )
                            } else {
                                zoomPage.animateTo(
                                    Offset(tapX, tapY),
                                    targetScale = zoomPage.homeScale,
                                )
                            }
                        }
                        return@awaitEachGesture
                    } else if (
                        movedSecondTapRoute(state.doubleTapZoomEnabled) == MovedSecondTapRoute.QUICK_SCALE
                    ) {
                        // double tap drag
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPointerInputChange(secondDown)

                        val dragPointerId = secondDown.id

                        val originalScale = page.scale
                        val originalX = page.x
                        val originalY = page.y
                        var totalDeltaY = 0f

                        state.animationJob?.cancel()

                        page.isScaleAnimating = true
                        var willFlingZoom = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change =
                                    event.changes.firstOrNull { it.id == dragPointerId }

                                if (change == null || change.changedToUp() || change.isConsumed) {
                                    break
                                }

                                velocityTracker.addPointerInputChange(change)

                                if (change.positionChanged()) {
                                    val pan = event.calculatePan()
                                    totalDeltaY += pan.y
                                    if (totalDeltaY != 0f) {
                                        val px = secondDown.position.x / state.width - 0.5f
                                        val py = secondDown.position.y / state.height - 0.5f

                                        val newScale =
                                            originalScale * 10f.pow(2 * totalDeltaY / state.height)

                                        val diff = 1 / newScale - 1 / originalScale

                                        page.setPos(
                                            (originalX + px * diff).orZero(),
                                            (originalY + py * diff).orZero(),
                                            newScale,
                                        )

                                        change.consume()
                                    }
                                }
                            }
                            val dragVelocity = velocityTracker.calculateVelocity()
                            // Decided before the finally below, so isScaleAnimating has no gap
                            // between this drag ending and its fling starting.
                            willFlingZoom =
                                abs(dragVelocity.y) > 200 && page.scale > page.homeScale && page.scale < page.maxScale
                        } finally {
                            if (!willFlingZoom) {
                                page.isScaleAnimating = false
                                page.invalidate()
                            }
                        }

                        val velocity = velocityTracker.calculateVelocity()
                        if (willFlingZoom) {
                            // fling zoom
                            page.animationJob = scope.launch(NormalMotionDurationScale) {
                                try {
                                    Animatable(0f).animateDecay(
                                        velocity.y,
                                        exponentialDecay(),
                                    ) {
                                        val px = secondDown.position.x / state.width - 0.5f
                                        val py = secondDown.position.y / state.height - 0.5f

                                        val newScale =
                                            originalScale * 10f.pow(2 * (totalDeltaY + value) / state.height)

                                        val scale =
                                            newScale.fastCoerceIn(page.homeScale, page.maxScale)
                                        val diff = 1 / scale - 1 / originalScale

                                        val x = (originalX + px * diff).orZero()
                                        val y = (originalY + py * diff).orZero()

                                        val minX = page.minX(scale)
                                        val maxX = page.maxX(scale)
                                        val minY = page.minY(scale)
                                        val maxY = page.maxY(scale)

                                        val prevScale = page.scale
                                        page.setPos(
                                            x.fastCoerceIn(minX, maxX),
                                            y.fastCoerceIn(minY, maxY),
                                            scale,
                                        )
                                        if (value != 0f && scale == prevScale) throw KnsFlingStalled()
                                    }
                                } catch (_: KnsFlingStalled) {
                                } finally {
                                    page.isScaleAnimating = false
                                    page.invalidate()
                                }
                            }
                        } else {
                            page.animateTo(
                                Offset(
                                    secondDown.position.x / state.width,
                                    secondDown.position.y / state.height,
                                ),
                            )
                        }

                        return@awaitEachGesture
                    }
                    gestureDown = secondDown
                }
                if (!wasScrolling) page.animateTo(Offset(0.5f, 0.5f))
                pageTurnJob?.cancel()

                var lastMoveTime = gestureDown.uptimeMillis
                var lastEventTime: Long = gestureDown.uptimeMillis
                var acc = Offset.Zero

                var scaleOrigin = Offset(0.5f, 0.5f)

                var single = true
                var pageTurning = wasScrolling

                // If grabbing mid-animation, update firstPos so panning continues smoothly
                if (wasScrolling) {
                    state.firstPos = gestureDown.position
                }

                val velocityTracker = VelocityTracker()
                velocityTracker.addPointerInputChange(gestureDown)

                page.animationJob?.cancel()

                var canceled = false
                try {
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        canceled = event.changes.any { it.isConsumed }
                        if (longPressed) break
                        if (canceled) {
                            longPressJob?.cancel()
                        } else {
                            val change = event.changes[0]
                            lastEventTime = change.uptimeMillis

                            if (change.positionChanged()) {
                                lastMoveTime = change.uptimeMillis
                            }

                            val centroid = event.calculateCentroid(useCurrent = true)

                            val twoFingers =
                                event.changes.size > 1 && event.changes.all { it.pressed }
                            page.isScaleAnimating = twoFingers

                            var pointerCountChanged = false
                            if (twoFingers) {
                                if (single && !pageTurning) {
                                    longPressJob?.cancel()
                                    velocityTracker.resetTracking()
                                    acc = Offset.Zero
                                    pointerCountChanged = true
                                }
                                if (!pageTurning) {
                                    velocityTracker.addPointerInputChange(change)
                                    single = false
                                    scaleOrigin = Offset(
                                        centroid.x / state.width,
                                        centroid.y / state.height,
                                    )
                                }
                            } else if (single) {
                                velocityTracker.addPointerInputChange(change)
                            }

                            if (pointerCountChanged) continue

                            val pan = event.calculatePan()
                            if (acc.getDistance() > touchSlop) longPressJob?.cancel()
                            acc += pan

                            if (pageTurning) {
                                val prev = state.pageOffset
                                // Always the raw drag direction - pageOffset's sign is
                                // also the live visual pan amount (see renderSnapshot's use
                                // of it as a transition's frac), so it has to keep tracking
                                // the finger 1:1 regardless of reading direction. isReversed
                                // is resolved separately, in getPage/onPageChange, where it
                                // only affects which page a crossing reveals - not how far
                                // the finger has to move to cause one.
                                val panAmount =
                                    if (state.isVertical) -pan.y / state.height else -pan.x / state.width
                                state.pageOffset += panAmount
                                if ((prev > 0f && state.pageOffset <= 0f) || (prev < 0f && state.pageOffset >= 0f)) {
                                    state.pageOffset = 0f
                                    pageTurning = false
                                    acc = Offset.Zero
                                }
                                state.currentPos = event.changes[0].position
                                state.invalidate()
                                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                            } else {
                                // With pinch off, multi-pointer movement at home scale is inert.
                                val zoom =
                                    if (state.pinchZoomEnabled) event.calculateZoom() else 1f

                                if (
                                    (zoom != 1f || pan != Offset.Zero) &&
                                    (canPanAtHomeScale(single, state.pinchZoomEnabled) || !page.atHomeScale)
                                ) {
                                    val newScale = page.scale * zoom
                                    val diff = 1 / newScale - 1 / page.scale

                                    var x = page.x + (pan.x / state.width) / page.scale
                                    var y = page.y + (pan.y / state.height) / page.scale

                                    x += (centroid.x / state.width - 0.5f) * diff
                                    y += (centroid.y / state.height - 0.5f) * diff

                                    val minX = page.minX(newScale)
                                    val maxX = page.maxX(newScale)
                                    val minY = page.minY(newScale)
                                    val maxY = page.maxY(newScale)

                                    if (single) {
                                        val clampedX = x.fastCoerceIn(minX, maxX)
                                        val clampedY = y.fastCoerceIn(minY, maxY)
                                        val overflow = if (state.isVertical) {
                                            y - clampedY
                                        } else {
                                            x - clampedX
                                        }
                                        val isBiased = if (state.isVertical) {
                                            abs(acc.y) > abs(acc.x)
                                        } else {
                                            abs(acc.x) > abs(acc.y)
                                        }
                                        if (overflow != 0f && isBiased) {
                                            page.animateTo(Offset(0.5f, 0.5f))
                                            pageTurning = true
                                            state.firstPos = gestureDown.position
                                            state.pageOffset += -overflow * page.scale
                                            state.invalidate()
                                        }
                                        x = clampedX
                                        y = clampedY
                                    }

                                    page.setPos(x.orZero(), y.orZero(), newScale)

                                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                } finally {
                    page.isScaleAnimating = false
                    page.invalidate()
                }

                longPressJob?.cancel()
                if (longPressed || canceled) return@awaitEachGesture

                if (pageTurning) {
                    val velocity = velocityTracker.calculateVelocity()
                    // Always raw, matching panAmount above - see that comment.
                    val initialVelocity =
                        if (state.isVertical) -velocity.y / state.height else -velocity.x / state.width

                    // Flicking opposite to current direction = go back to 0
                    val flickingOpposite =
                        (state.pageOffset > 0f && initialVelocity < -0.5f) || (state.pageOffset < 0f && initialVelocity > 0.5f)

                    val target = when {
                        flickingOpposite -> 0f
                        initialVelocity > 1f && state.haveNext -> 1f
                        initialVelocity < -1f && state.havePrev -> -1f
                        state.pageOffset > 0.5f && state.haveNext -> 1f
                        state.pageOffset < -0.5f && state.havePrev -> -1f
                        else -> 0f
                    }

                    state.animationJob = scope.launch {
                        val anim = Animatable(state.pageOffset)
                        anim.updateBounds(lowerBound = -1f, upperBound = 1f)
                        anim.animateTo(
                            target,
                            initialVelocity = initialVelocity,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = 0.002f,
                            ),
                        ) {
                            state.pageOffset = value
                            state.invalidate()
                        }
                    }
                } else {
                    val minX = page.minX(page.scale)
                    val maxX = page.maxX(page.scale)
                    val minY = page.minY(page.scale)
                    val maxY = page.maxY(page.scale)

                    val velocity = velocityTracker.calculateVelocity()
                    // Bounded by minScale/maxScale, the pair a release settles back
                    // into (see animateTo): a pinch past either springs back, so there is
                    // no fling to start - but zoomed out below homeScale is a resting
                    // place like any other, and pans there like any other.
                    if ((page.scale >= page.minScale) && (page.scale <= page.maxScale) && (lastEventTime - lastMoveTime) < 100 && (
                            abs(
                                velocity.x,
                            ) > minFlingVelocity || abs(velocity.y) > minFlingVelocity
                            ) && (
                            page.x.fastCoerceIn(
                                minX,
                                maxX,
                            ) == page.x || page.y.fastCoerceIn(minY, maxY) == page.y
                            )
                    ) {
                        // fling pan
                        page.animationJob = scope.launch(NormalMotionDurationScale) {
                            page.isFlinging = true
                            try {
                                fling.snapTo(Offset.Zero)
                                var lastOffset = Offset.Zero
                                fling.animateDecay(
                                    Offset(velocity.x, velocity.y),
                                    exponentialDecay<Offset>(),
                                ) {
                                    val delta = value - lastOffset
                                    lastOffset = value
                                    val dx = (delta.x / state.width) / page.scale
                                    val dy = (delta.y / state.height) / page.scale
                                    val prevX = page.x
                                    val prevY = page.y
                                    val newX =
                                        (page.x + dx).fastCoerceIn(minX, maxX).orZero()
                                    val newY =
                                        (page.y + dy).fastCoerceIn(minY, maxY).orZero()
                                    page.setPos(newX, newY)
                                    // Pinned on both axes: the decay would run on
                                    // without moving anything, swallowing the next tap
                                    // as "mid-fling". It never reverses, so one such
                                    // frame settles it - but only one that asked for a
                                    // move, the first frame being the initial value.
                                    if ((dx != 0f || dy != 0f) && newX == prevX && newY == prevY) {
                                        throw KnsFlingStalled()
                                    }
                                }
                            } catch (_: KnsFlingStalled) {
                                // Nothing left to glide.
                            } finally {
                                page.isFlinging = false
                            }
                        }
                    } else {
                        page.animateTo(scaleOrigin)
                    }
                }
            }
        }

    Box {
        ViewerSurface(surfaceModifier) { surface, width, height ->
            try {
                // Before init: whether the panel does HDR at all gates the decode path, which can
                // reach an image before the first frame is drawn.
                attachHdrDisplay(view)
                state.init(scope, surface, width, height)
                surface.onChanged { w, h -> state.resize(w, h) }
                // After init, so Hdr.resolve has run and the surface's capability is known too.
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

/**
 * Holds the window's HDR colour mode while this viewer is composed, and restores it on window refocus.
 *
 * [Hdr] otherwise ties the mode to the first/last HDR image; held all session it would ramp panel brightness
 * on SDR reading. While unfocused, the system silently drops the colour mode and the surface's extended range.
 */
@Composable
internal fun HdrColorModeHost() {
    val view = LocalView.current
    val focused = LocalWindowInfo.current.isWindowFocused

    DisposableEffect(view) {
        Hdr.attachColorModeHost(view)
        onDispose { Hdr.detachColorModeHost(view) }
    }

    LaunchedEffect(focused) {
        if (focused) Hdr.resyncPresentation()
    }
}

/**
 * Ends a fling from inside a frame, once its position is pinned on every axis - an [Animatable]
 * block has no non-suspending way to stop one. Caught where it is thrown; a
 * [CancellationException] only so an escape ends the fling coroutine quietly.
 */
internal class KnsFlingStalled : CancellationException("fling has nothing left to move")
// KNS <--
