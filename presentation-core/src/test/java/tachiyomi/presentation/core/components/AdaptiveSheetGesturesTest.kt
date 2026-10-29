package tachiyomi.presentation.core.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

// KNS -->
/**
 * The sheet's lower anchor is a whole screen away (here 2000 px, 1 px = 1 dp), the open anchor is at 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveSheetGesturesTest {

    private val spec = tween<Float>(durationMillis = 350)

    // Runs animations to their end without waiting for real frames
    private val frameClock = object : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            time += 16_000_000L
            return onFrame(time)
        }
    }

    private fun openSheet() = AnchoredDraggableState(initialValue = 0).apply {
        updateAnchors(
            DraggableAnchors {
                0 at 0f
                1 at 2000f
            },
        )
    }

    private suspend fun AnchoredDraggableState<Int>.release(velocity: Float) {
        settleSheet(
            velocity = velocity,
            positionalThresholdPx = 56f,
            velocityThresholdPx = 125f,
            animationSpec = spec,
        )
    }

    private fun runSheetTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        withContext(frameClock) { block() }
    }

    @Test
    fun `closest anchor settling ignores a flick, which made the sheet sticky`() = runSheetTest {
        val state = openSheet()
        state.dispatchRawDelta(300f)
        state.settle(spec)
        // 300 of 2000 px is far from half, so it returned to open whatever the gesture speed was
        state.settledValue shouldBe 0
    }

    @Test
    fun `a quick short downward flick dismisses`() = runSheetTest {
        val state = openSheet()
        state.dispatchRawDelta(30f)
        state.release(velocity = 3000f)
        state.settledValue shouldBe 1
    }

    @Test
    fun `a slow drag beyond the threshold dismisses`() = runSheetTest {
        val state = openSheet()
        state.dispatchRawDelta(200f)
        state.release(velocity = 20f)
        state.settledValue shouldBe 1
    }

    @Test
    fun `holding still then dragging past the threshold dismisses`() = runSheetTest {
        val state = openSheet()
        state.dispatchRawDelta(60f)
        state.release(velocity = 0f)
        state.settledValue shouldBe 1
    }

    @Test
    fun `too little movement returns to open`() = runSheetTest {
        val state = openSheet()
        state.dispatchRawDelta(30f)
        state.release(velocity = 40f)
        state.settledValue shouldBe 0
        state.offset shouldBe 0f
    }

    @Test
    fun `a tap or a flick that is not quick enough does not dismiss`() = runSheetTest {
        val state = openSheet()
        state.release(velocity = 0f)
        state.settledValue shouldBe 0
        state.dispatchRawDelta(10f)
        state.release(velocity = 100f)
        state.settledValue shouldBe 0
    }

    @Test
    fun `an upward fling after dragging down cancels the dismissal`() = runSheetTest {
        val state = openSheet()
        state.dispatchRawDelta(150f)
        state.release(velocity = -800f)
        state.settledValue shouldBe 0
    }

    @Test
    fun `dragging back above the threshold before releasing keeps the sheet open`() = runSheetTest {
        val state = openSheet()
        state.dispatchRawDelta(150f)
        state.dispatchRawDelta(-130f)
        state.release(velocity = 0f)
        state.settledValue shouldBe 0
    }

    @Test
    fun `a closed sheet stays closed and an upward flick reopens it`() = runSheetTest {
        val state = openSheet()
        state.dispatchRawDelta(200f)
        state.release(velocity = 0f)
        state.settledValue shouldBe 1
        state.release(velocity = 5000f)
        state.settledValue shouldBe 1
        state.dispatchRawDelta(-400f)
        state.release(velocity = -2000f)
        state.settledValue shouldBe 0
    }

    @Test
    fun `a fast downward fling left over by content at its top dismisses`() = runSheetTest {
        val state = openSheet()
        val flings = mutableListOf<Float>()
        val connection = state.sheetNestedScrollConnection { velocity -> flings += velocity }
        // list already at its top: nothing consumed, everything left over
        connection.onPostFling(consumed = Velocity.Zero, available = Velocity(0f, 2500f))
        flings shouldBe listOf(2500f)
        // as the sheet does, settle with the reported velocity
        state.release(flings.single())
        state.settledValue shouldBe 1
    }

    @Test
    fun `downward drag from content at its top moves the sheet and release settles it`() = runSheetTest {
        val state = openSheet()
        val flings = mutableListOf<Float>()
        val connection = state.sheetNestedScrollConnection { velocity -> flings += velocity }
        // the list could not scroll further up, so the leftover goes to the sheet
        val taken = connection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 90f),
            source = NestedScrollSource.UserInput,
        )
        taken shouldBe Offset(0f, 90f)
        state.offset shouldBe 90f
        connection.onPostFling(Velocity.Zero, Velocity(0f, 10f))
        state.release(flings.single())
        state.settledValue shouldBe 1
    }

    @Test
    fun `a list that can still scroll is not taken over by the sheet`() = runSheetTest {
        val state = openSheet()
        val connection = state.sheetNestedScrollConnection { error("no fling expected") }
        // scrolling the list down: pre-scroll only reacts to upward movement of an already lowered sheet
        connection.onPreScroll(Offset(0f, 80f), NestedScrollSource.UserInput) shouldBe Offset.Zero
        // the list consumed all of it, so nothing is left for the sheet
        connection.onPostScroll(Offset(0f, 80f), Offset.Zero, NestedScrollSource.UserInput) shouldBe Offset.Zero
        state.offset shouldBe 0f
        state.settledValue shouldBe 0
    }

    @Test
    fun `programmatic scrolling does not drag the sheet`() = runSheetTest {
        val state = openSheet()
        val connection = state.sheetNestedScrollConnection { error("no fling expected") }
        connection.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.SideEffect) shouldBe Offset.Zero
        state.offset shouldBe 0f
    }

    @Test
    fun `dismissal is reported once however often the sheet settles`() = runTest(UnconfinedTestDispatcher()) {
        withContext(frameClock) {
            val state = openSheet()
            val dismissals = mutableListOf<Int>()
            val job = launch {
                snapshotFlow { state.settledValue }
                    .drop(1)
                    .filter { it == 1 }
                    .toList(dismissals)
            }
            state.dispatchRawDelta(300f)
            state.release(velocity = 4000f)
            Snapshot.sendApplyNotifications()
            yield()
            state.release(velocity = 4000f)
            state.release(velocity = 0f)
            Snapshot.sendApplyNotifications()
            yield()
            job.cancel()
            state.settledValue shouldBe 1
            dismissals.size shouldBe 1
        }
    }
}
// KNS <--
