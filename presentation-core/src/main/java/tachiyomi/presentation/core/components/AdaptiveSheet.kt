package tachiyomi.presentation.core.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AdaptiveSheet(
    isTabletUi: Boolean,
    enableSwipeDismiss: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    if (isTabletUi) {
        var targetAlpha by remember { mutableFloatStateOf(0f) }
        val alpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = sheetAnimationSpec,
            label = "alpha",
        )
        val internalOnDismissRequest: () -> Unit = {
            scope.launch {
                targetAlpha = 0f
                onDismissRequest()
            }
        }
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = internalOnDismissRequest,
                )
                .fillMaxSize()
                .alpha(alpha),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .requiredWidthIn(max = 460.dp)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                    )
                    .systemBarsPadding()
                    .padding(vertical = 16.dp)
                    .then(modifier),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = {
                    BackHandler(
                        enabled = remember { derivedStateOf { alpha > 0f } }.value,
                        onBack = internalOnDismissRequest,
                    )
                    content()
                },
            )

            LaunchedEffect(Unit) {
                targetAlpha = 1f
            }
        }
    } else {
        val anchoredDraggableState = rememberSaveable(saver = AnchoredDraggableState.Saver()) {
            AnchoredDraggableState(initialValue = 1)
        }
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = anchoredDraggableState,
            positionalThreshold = { _: Float -> with(density) { SHEET_POSITIONAL_THRESHOLD_DP.dp.toPx() } },
            animationSpec = sheetAnimationSpec,
        )
        val internalOnDismissRequest = {
            if (anchoredDraggableState.settledValue == 0) {
                scope.launch { anchoredDraggableState.animateTo(1) }
            }
        }
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = internalOnDismissRequest,
                )
                .fillMaxSize()
                .onSizeChanged {
                    val anchors = DraggableAnchors {
                        0 at 0f
                        1 at it.height.toFloat()
                    }
                    anchoredDraggableState.updateAnchors(anchors)
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                    )
                    .then(
                        if (enableSwipeDismiss) {
                            Modifier.nestedScroll(
                                remember(anchoredDraggableState, density) {
                                    // KNS -->
                                    // Settle with the fling velocity and the 56 dp / 125 dp/s thresholds
                                    anchoredDraggableState.sheetNestedScrollConnection { velocity ->
                                        scope.launch {
                                            anchoredDraggableState.settleSheet(
                                                velocity = velocity,
                                                positionalThresholdPx = with(density) { SHEET_POSITIONAL_THRESHOLD_DP.dp.toPx() },
                                                velocityThresholdPx = with(density) { SHEET_VELOCITY_THRESHOLD_DP.dp.toPx() },
                                                animationSpec = sheetAnimationSpec,
                                            )
                                        }
                                    }
                                    // KNS <--
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(modifier)
                    .offset {
                        IntOffset(
                            0,
                            anchoredDraggableState.offset
                                .takeIf { it.isFinite() }
                                ?.roundToInt()
                                ?: 0,
                        )
                    }
                    .anchoredDraggable(
                        state = anchoredDraggableState,
                        orientation = Orientation.Vertical,
                        enabled = enableSwipeDismiss,
                        flingBehavior = flingBehavior,
                    )
                    .navigationBarsPadding()
                    .statusBarsPadding(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = {
                    BackHandler(
                        enabled = anchoredDraggableState.targetValue == 0,
                        onBack = internalOnDismissRequest,
                    )
                    content()
                },
            )

            LaunchedEffect(anchoredDraggableState) {
                scope.launch { anchoredDraggableState.animateTo(0) }
                snapshotFlow { anchoredDraggableState.settledValue }
                    .drop(1)
                    .filter { it == 1 }
                    .collectLatest {
                        onDismissRequest()
                    }
            }
        }
    }
}

private val sheetAnimationSpec = tween<Float>(durationMillis = 350)
