package tachiyomi.presentation.core.components

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastLastOrNull
import androidx.compose.ui.util.fastMaxBy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Draws vertical fast scroller to a lazy list
 *
 * Set key with [STICKY_HEADER_KEY_PREFIX] prefix to any sticky header item in the list.
 */
@Composable
fun VerticalFastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbAllowed: () -> Boolean = { true },
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    // KUN -->
    // Label to show next to the thumb while it is dragged, keyed by item index. Null hides it.
    thumbLabel: ((index: Int) -> String?)? = null,
    // KUN <--
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceable = subcompose("content", content).map { it.measure(constraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.totalItemsCount == 0) return@subcompose

            val thumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var thumbOffsetY by remember(thumbTopPadding) { mutableFloatStateOf(thumbTopPadding) }

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()
            val scrolled = remember {
                MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }

            // listState.isScrollInProgress occasionally flickers
            val scrollStateTracker = remember { MutableData(listState.isScrollInProgress) }
            val stableScrollInProgress = scrollStateTracker.value || listState.isScrollInProgress
            scrollStateTracker.value = listState.isScrollInProgress
            val anyScrollInProgress = stableScrollInProgress || isThumbDragged

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val heightPx = contentHeight.toFloat() -
                thumbTopPadding -
                thumbBottomPadding -
                listState.layoutInfo.afterContentPadding
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackHeightPx = heightPx - thumbHeightPx
            val scrollHeightPx = contentHeight.toFloat() -
                listState.layoutInfo.beforeContentPadding -
                listState.layoutInfo.afterContentPadding -
                thumbBottomPadding

            val visibleItems = layoutInfo.visibleItemsInfo
            val topItem = visibleItems.fastFirstOrNull {
                it.bottom >= 0 &&
                    (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX)?.not() ?: true
            } ?: visibleItems.first()
            val bottomItem = visibleItems.fastLastOrNull {
                it.top <= scrollHeightPx &&
                    (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX)?.not() ?: true
            } ?: visibleItems.last()

            val topHiddenProportion = -1f * topItem.top / topItem.size.coerceAtLeast(1)
            val bottomHiddenProportion = (bottomItem.bottom - scrollHeightPx) / bottomItem.size.coerceAtLeast(1)
            val previousSections = topHiddenProportion + topItem.index
            val remainingSections = bottomHiddenProportion + (layoutInfo.totalItemsCount - (bottomItem.index + 1))
            val scrollableSections = previousSections + remainingSections

            val layoutChangeTracker = remember { MutableData(scrollableSections) }
            val layoutChanged = !anyScrollInProgress && abs(layoutChangeTracker.value - scrollableSections) > 0.1
            layoutChangeTracker.value = scrollableSections

            val estimateConfidence = remember { MutableData(remainingSections) }
            if (layoutChanged) estimateConfidence.value = remainingSections
            val maxRemainingSections = remember(estimateConfidence.value) { scrollableSections }
            estimateConfidence.value = max(estimateConfidence.value, remainingSections)

            if (maxRemainingSections < 0.5) return@subcompose

            // KUN -->
            // Item index and offset the thumb lands on at [offsetY], shared by the drag scroll and the
            // thumb label so the pill and the section it would jump to always agree
            fun targetAt(offsetY: Float): Pair<Int, Int> {
                val thumbProportion = (offsetY - thumbTopPadding) / trackHeightPx
                if (thumbProportion <= 0.001f) return 0 to 0
                val scrollRemainingSections = (1f - thumbProportion) * maxRemainingSections
                val currentSection = layoutInfo.totalItemsCount - scrollRemainingSections
                val scrollSectionIndex = currentSection.toInt().coerceAtMost(layoutInfo.totalItemsCount)
                val expectedScrollItem = visibleItems.find { it.index == scrollSectionIndex } ?: visibleItems.first()
                val scrollRelativeOffset = expectedScrollItem.size * (currentSection - scrollSectionIndex)
                val scrollSectionOffset = (scrollRelativeOffset - scrollHeightPx).roundToInt()
                val scrollItemIndex = scrollSectionIndex.coerceIn(0, layoutInfo.totalItemsCount - 1)
                val scrollItemOffset = scrollSectionOffset + (scrollSectionIndex - scrollItemIndex) * bottomItem.size
                return scrollItemIndex to scrollItemOffset
            }
            // KUN <--

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (layoutInfo.totalItemsCount == 0 || !isThumbDragged) return@LaunchedEffect
                val thumbProportion = (thumbOffsetY - thumbTopPadding) / trackHeightPx
                if (thumbProportion <= 0.001f) estimateConfidence.value = -1f
                // KUN -->
                val (index, offset) = targetAt(thumbOffsetY)
                listState.scrollToItem(index = index, scrollOffset = offset)
                // KUN <--
                scrolled.tryEmit(Unit)
            }

            // KUN -->
            // Drag label, derived from where the drag would scroll to rather than from the settled layout
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val labelAlpha = remember { Animatable(0f) }
            var label by remember { mutableStateOf<String?>(null) }
            // This effect only restarts with the label, unlike the drag one, so the mapping is read
            // through a state or the pill would keep naming the section of the first composition
            val currentTargetAt by rememberUpdatedState(::targetAt)
            LaunchedEffect(thumbLabel) {
                if (thumbLabel == null) return@LaunchedEffect
                snapshotFlow { if (isThumbDragged) thumbOffsetY else null }
                    .collectLatest { offsetY ->
                        if (offsetY == null) {
                            delay(ThumbLabelVisibilityDuration)
                            labelAlpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                            label = null
                        } else {
                            labelAlpha.snapTo(1f)
                            label = thumbLabel(currentTargetAt(offsetY).first)
                        }
                    }
            }
            // KUN <--

            // When list scrolled
            if (layoutInfo.totalItemsCount != 0 && !isThumbDragged) {
                val proportion = 1f - remainingSections / maxRemainingSections
                thumbOffsetY = trackHeightPx * proportion + thumbTopPadding
                if (stableScrollInProgress) scrolled.tryEmit(Unit)
            }

            // Thumb alpha
            val alpha = remember { Animatable(0f) }
            val isThumbVisible = alpha.value > 0f
            LaunchedEffect(scrolled, alpha) {
                scrolled
                    .sample(0.1.seconds)
                    .collectLatest {
                        if (thumbAllowed()) {
                            alpha.snapTo(1f)
                            delay(ScrollBarVisibilityDuration)
                            alpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                        } else {
                            alpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                        }
                    }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                    .then(
                        // Recompose opts
                        if (isThumbVisible && !listState.isScrollInProgress) {
                            Modifier.draggable(
                                interactionSource = dragInteractionSource,
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    val newOffsetY = thumbOffsetY + delta
                                    thumbOffsetY = newOffsetY.coerceIn(
                                        thumbTopPadding,
                                        thumbTopPadding + trackHeightPx,
                                    )
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Exclude thumb from gesture area only when needed
                        if (isThumbVisible && !isThumbDragged && !listState.isScrollInProgress) {
                            Modifier.systemGestureExclusion()
                        } else {
                            Modifier
                        },
                    )
                    .height(ThumbLength)
                    .padding(horizontal = 8.dp)
                    .padding(end = endContentPadding)
                    .width(ThumbThickness)
                    .alpha(alpha.value)
                    .background(color = thumbColor, shape = ThumbShape),
            ) {
                // KUN -->
                label?.let { text ->
                    FastScrollThumbLabel(
                        text = text,
                        modifier = Modifier
                            // CenterStart is the side facing the list in both directions, since the scroller sits
                            // on the trailing edge, so the pill goes on that side of the thumb.
                            .align(Alignment.CenterStart)
                            .graphicsLayer { this.alpha = labelAlpha.value }
                            .layout { measurable, constraints ->
                                // Measured unconstrained so the pill sizes to its text inside the thin thumb column
                                val placeable = measurable.measure(Constraints())
                                val gap = ThumbLabelGap.roundToPx()
                                layout(0, placeable.height) {
                                    // Zero-width node, so put the pill on the inner side by hand: placeRelative
                                    // doesn't mirror here
                                    if (isRtl) {
                                        placeable.place(gap, 0)
                                    } else {
                                        placeable.place(-placeable.width - gap, 0)
                                    }
                                }
                            },
                    )
                }
                // KUN <--
            }
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth - scrollerWidth, 0)
            }
        }
    }
}

// KUN -->
@Composable
private fun FastScrollThumbLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ThumbShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
// KUN <--

@Composable
private fun rememberColumnWidthSums(
    columns: GridCells,
    horizontalArrangement: Arrangement.Horizontal,
    contentPadding: PaddingValues,
) = remember<Density.(Constraints) -> List<Int>>(
    columns,
    horizontalArrangement,
    contentPadding,
) {
    { constraints ->
        require(constraints.maxWidth != Constraints.Infinity) {
            "LazyVerticalGrid's width should be bound by parent"
        }
        val horizontalPadding = contentPadding.calculateStartPadding(LayoutDirection.Ltr) +
            contentPadding.calculateEndPadding(LayoutDirection.Ltr)
        val gridWidth = constraints.maxWidth - horizontalPadding.roundToPx()
        with(columns) {
            calculateCrossAxisCellSizes(
                gridWidth,
                horizontalArrangement.spacing.roundToPx(),
            ).toMutableList().apply {
                for (i in 1..<size) {
                    this[i] += this[i - 1]
                }
            }
        }
    }
}

/*
    VerticalGridFastScroller was written with a regularity assumption, so it is slightly inaccurate for layouts with
    varying row sizes.
 */
// TODO: Ideally rewrite VerticalGridFastScroller to use similar logic as VerticalFastScroller
@Composable
fun VerticalGridFastScroller(
    state: LazyGridState,
    columns: GridCells,
    arrangement: Arrangement.Horizontal,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    thumbAllowed: () -> Boolean = { true },
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    content: @Composable () -> Unit,
) {
    val slotSizesSums = rememberColumnWidthSums(
        columns = columns,
        horizontalArrangement = arrangement,
        contentPadding = contentPadding,
    )

    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceable = subcompose("content", content).map { it.measure(constraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = state.layoutInfo
            // KUN --> guard empty layouts and don't cache the scrollable decision
            if (layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.visibleItemsInfo.size >= layoutInfo.totalItemsCount) {
                return@subcompose
            }
            // KUN <--
            val thumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var thumbOffsetY by remember(thumbTopPadding) { mutableFloatStateOf(thumbTopPadding) }

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()
            val scrolled = remember {
                MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val heightPx = contentHeight.toFloat() -
                thumbTopPadding -
                thumbBottomPadding -
                state.layoutInfo.afterContentPadding
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackHeightPx = heightPx - thumbHeightPx

            // KUN --> invalidate the estimate when the content or the column count changes; keeping
            // it cached for the previous list made the thumb reach the end before the actual end
            val columnCount = slotSizesSums(constraints).size.coerceAtLeast(1)
            val scrollRange = remember(columnCount, layoutInfo.totalItemsCount) {
                computeGridScrollRange(state = state, columnCount = columnCount)
            }
            // KUN <--

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (layoutInfo.totalItemsCount == 0 || !isThumbDragged) return@LaunchedEffect
                val visibleItems = state.layoutInfo.visibleItemsInfo
                val startChild = visibleItems.first()
                val endChild = visibleItems.last()
                val laidOutArea = (endChild.offset.y + endChild.size.height) - startChild.offset.y
                val laidOutRows = 1 + abs(endChild.index - startChild.index) / columnCount
                val avgSizePerRow = laidOutArea.toFloat() / laidOutRows

                val scrollRatio = (thumbOffsetY - thumbTopPadding) / trackHeightPx
                val scrollAmt = scrollRatio * (scrollRange.toFloat() - heightPx).coerceAtLeast(1f)
                val rowNumber = (scrollAmt / avgSizePerRow).toInt()
                val rowOffset = scrollAmt - rowNumber * avgSizePerRow

                state.scrollToItem(index = columnCount * rowNumber, scrollOffset = rowOffset.roundToInt())
                scrolled.tryEmit(Unit)
            }

            // When list scrolled
            // KUN --> key on the index too, the offset alone misses row-aligned scrolls
            LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
                if (state.layoutInfo.totalItemsCount == 0 || isThumbDragged) return@LaunchedEffect
                val scrollOffset = computeGridScrollOffset(state = state, columnCount = columnCount)
                /*
                    LazyGridItemInfo doesn't always give the accurate height of the object, so we clamp the proportion
                    at 1 to ensure that there are no issues due to this -- ideally we would correctly compute the value
                 */
                val extraScrollRange = (scrollRange.toFloat() - heightPx).coerceAtLeast(1f)
                val proportion = (scrollOffset.toFloat() / extraScrollRange).coerceAtMost(1f)
                thumbOffsetY = trackHeightPx * proportion + thumbTopPadding
                scrolled.tryEmit(Unit)
            }
            // KUN <--

            // Thumb alpha
            val alpha = remember { Animatable(0f) }
            val isThumbVisible = alpha.value > 0f
            LaunchedEffect(scrolled, alpha) {
                scrolled
                    .sample(0.1.seconds)
                    .collectLatest {
                        if (thumbAllowed()) {
                            alpha.snapTo(1f)
                            delay(ScrollBarVisibilityDuration)
                            alpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                        } else {
                            alpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                        }
                    }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                    .then(
                        // Recompose opts
                        if (isThumbVisible && !state.isScrollInProgress) {
                            Modifier.draggable(
                                interactionSource = dragInteractionSource,
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    val newOffsetY = thumbOffsetY + delta
                                    thumbOffsetY = newOffsetY.coerceIn(
                                        thumbTopPadding,
                                        thumbTopPadding + trackHeightPx,
                                    )
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Exclude thumb from gesture area only when needed
                        if (isThumbVisible && !isThumbDragged && !state.isScrollInProgress) {
                            Modifier.systemGestureExclusion()
                        } else {
                            Modifier
                        },
                    )
                    .height(ThumbLength)
                    .padding(end = endContentPadding)
                    .width(ThumbThickness)
                    .alpha(alpha.value)
                    .background(color = thumbColor, shape = ThumbShape),
            )
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth - scrollerWidth, 0)
            }
        }
    }
}

// TODO: not sure why abs corrections are in the following functions; these can probably be removed

private fun computeGridScrollOffset(state: LazyGridState, columnCount: Int): Int {
    if (state.layoutInfo.totalItemsCount == 0) return 0
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val startChild = visibleItems.first()
    val endChild = visibleItems.last()
    val laidOutArea = (endChild.offset.y + endChild.size.height) - startChild.offset.y
    val laidOutRows = 1 + abs(endChild.index - startChild.index) / columnCount
    val avgSizePerRow = laidOutArea.toFloat() / laidOutRows

    val rowsBefore = min(startChild.index, endChild.index).coerceAtLeast(0) / columnCount
    return (rowsBefore * avgSizePerRow - startChild.offset.y).roundToInt()
}

private fun computeGridScrollRange(state: LazyGridState, columnCount: Int): Int {
    if (state.layoutInfo.totalItemsCount == 0) return 0
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val startChild = visibleItems.first()
    val endChild = visibleItems.last()
    val laidOutArea = (endChild.offset.y + endChild.size.height) - startChild.offset.y
    val laidOutRows = 1 + abs(endChild.index - startChild.index) / columnCount
    val avgSizePerRow = laidOutArea.toFloat() / laidOutRows

    val totalRows = 1 + (state.layoutInfo.totalItemsCount - 1) / columnCount
    val endSpacing = avgSizePerRow - endChild.size.height
    return (endSpacing + (laidOutArea.toFloat() / laidOutRows) * totalRows).roundToInt()
}

private class MutableData<T>(var value: T)

object Scroller {
    const val STICKY_HEADER_KEY_PREFIX = "sticky:"
}

private val ThumbLength = 48.dp
// KUN -->
private val ThumbLabelGap = 8.dp
// KUN <--
private val ThumbThickness = 12.dp
private val ThumbShape = RoundedCornerShape(ThumbThickness / 2)
private val ScrollBarVisibilityDuration = 2.seconds
// KUN -->
private val ThumbLabelVisibilityDuration = 1.seconds
// KUN <--
private val ImmediateFadeOutAnimationSpec = tween<Float>(
    durationMillis = ViewConfiguration.getScrollBarFadeDuration(),
)

private val LazyListItemInfo.top: Int
    get() = offset

private val LazyListItemInfo.bottom: Int
    get() = offset + size
