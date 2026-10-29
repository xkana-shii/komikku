package tachiyomi.presentation.core.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.forEach
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

// KNS -->
/** Distance from its anchor a sheet has to be dragged to move on to the next one. */
internal const val SHEET_POSITIONAL_THRESHOLD_DP = 56f

/** Fling speed above which the sheet moves to the next anchor whatever the distance travelled. */
internal const val SHEET_VELOCITY_THRESHOLD_DP = 125f

/**
 * Settles the sheet on the anchor the user's gesture points at.
 *
 * `AnchoredDraggableState.settle(animationSpec)` only picks the anchor that is closest by distance, which for a
 * bottom sheet whose lower anchor is a whole screen away means dragging past half the screen: a flick or a drag
 * of a few hundred pixels went back to open. Like `AnchoredDraggableDefaults.flingBehavior` (and the velocity
 * based `settle` of v1.24.8), this takes the next anchor in the direction of the fling when it is quicker than
 * [velocityThresholdPx], otherwise moves on when the sheet was dragged [positionalThresholdPx] away from where it
 * started and returns to it if not. It is used for gestures that arrive through nested scrolling, where
 * `Modifier.anchoredDraggable`'s own fling behaviour does not run.
 */
internal suspend fun AnchoredDraggableState<Int>.settleSheet(
    velocity: Float,
    positionalThresholdPx: Float,
    velocityThresholdPx: Float,
    animationSpec: AnimationSpec<Float>,
) {
    val offset = offset
    if (offset.isNaN()) return
    val origin = anchors.positionOf(settledValue)
    if (origin.isNaN()) return

    val direction = when {
        abs(velocity) >= velocityThresholdPx -> velocity
        abs(offset - origin) >= positionalThresholdPx -> offset - origin
        else -> 0f
    }
    var target = settledValue
    if (direction != 0f) {
        // The next anchor strictly beyond the current offset in that direction; stay if there is none
        var best = Float.NaN
        anchors.forEach { key, position ->
            val ahead = if (direction > 0f) position > offset else position < offset
            val closer = best.isNaN() || abs(position - offset) < abs(best - offset)
            if (ahead && closer) {
                best = position
                target = key
            }
        }
    }
    animateTo(target, animationSpec)
}

/**
 * Lets scrollable content inside the sheet hand its downward overscroll to the sheet: the sheet follows upward
 * scrolls first and downward leftovers afterwards, and settles with the fling velocity when the gesture ends.
 */
internal fun AnchoredDraggableState<Int>.sheetNestedScrollConnection(
    onFling: (velocity: Float) -> Unit,
) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0 && source == NestedScrollSource.UserInput) {
            Offset(0f, dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (source == NestedScrollSource.UserInput) {
            Offset(0f, dispatchRawDelta(available.y))
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.y
        return if (toFling < 0 && offset > anchors.minPosition()) {
            onFling(toFling)
            // since we go to the anchor with tween settling, consume all for the best UX
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        onFling(available.y)
        return if (targetValue != settledValue) {
            available
        } else {
            Velocity.Zero
        }
    }
}
// KNS <--
