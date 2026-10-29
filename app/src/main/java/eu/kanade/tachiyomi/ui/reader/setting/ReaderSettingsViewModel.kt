package eu.kanade.tachiyomi.ui.reader.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.viewer.LongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.NavigationSettings
import eu.kanade.tachiyomi.ui.reader.viewer.PagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.pager.SimplePagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuLongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuPagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuZoomGestures
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.SimpleLongStripSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import kotlin.time.Duration.Companion.seconds

/**
 * The reader's settings, each as one value re-read whenever a preference it's made of changes, so the activity and
 * viewers apply a change in one place instead of each preference calling back.
 */
class ReaderSettingsViewModel(
    readerState: StateFlow<ReaderViewModel.State>,
    val onChangeReadingMode: (ReadingMode) -> Unit,
    val onChangeOrientation: (ReaderOrientation) -> Unit,
    val preferences: ReaderPreferences,
    private val basePreferences: BasePreferences,
    // KUN -->
    // Supplied by the activity as `ReaderViewModel.getMangaReadingMode`, so the settings page picks the
    // same viewer the activity builds, the fork's auto-webtoon resolution (R45) included.
    private val resolveReadingMode: () -> ReadingMode,
    // KUN <--
) : ViewModel() {

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * The reading mode the viewer is made for, with the default resolved.
     */
    // KUN -->
    // Resolved through [resolveReadingMode] rather than from the stored mode, so the page rendered for
    // this manga is the one its viewer is: an auto-webtoon manga (R45) shows the webtoon settings.
    // Re-evaluated whenever the manga, the default mode or the auto-webtoon switch changes.
    val readingMode = combine(
        mangaFlow,
        preferences.defaultReadingMode.changes(),
        preferences.useAutoWebtoon.changes(),
    ) { _, _, _ -> resolveReadingMode() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolveReadingMode())
    // KUN <--

    // KUN -->
    private val viewerMode = MutableStateFlow<ViewerMode?>(null)

    /**
     * Whether the settings page shows the WebGPU viewer's knobs.
     *
     * Read from the viewer the activity built, not from the high quality renderer preference: when
     * [ReadingMode.toViewer]'s capability gate falls back to a pager or webtoon viewer, the knobs
     * that viewer reads are the ones the page must offer. Until a viewer is built - the sheet can be
     * opened first - the preference is all there is, so that is what the page shows.
     */
    val isWebGpuViewer: StateFlow<Boolean> = viewerMode
        .map { it?.isWebGpuViewer ?: basePreferences.highQualityRenderer.get() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, basePreferences.highQualityRenderer.get())

    private val wideViewport = MutableStateFlow<Boolean?>(null)

    /**
     * Whether the built viewer's own box is wider than it is tall - the
     * [ReaderPreferences.DualPageView.WIDE] rule's answer for the WebGPU pager, so the settings
     * sheet offers the dual-page controls exactly while the viewer is in dual page mode.
     *
     * The viewer's own box, not the window: on a foldable, in a split-screen pane, or behind the
     * cutout and system bar insets the viewer is padded by, the window can be landscape while the
     * viewer is portrait, or the reverse.
     *
     * Null until a viewer has been drawn and published its box, which is where the sheet can be
     * opened before; a null reads as not wide, as the viewer's own WIDE test also answers a
     * viewport with no size yet.
     */
    val viewerWideViewport: StateFlow<Boolean?> = wideViewport

    fun setViewerWideViewport(wide: Boolean) {
        wideViewport.value = wide
    }
    // KUN <--

    val general = settings(
        preferences.readerTheme,
        preferences.keepScreenOn,
        preferences.grayscale,
        preferences.invertedColors,
        preferences.fullscreen,
        preferences.drawUnderCutout,
        preferences.readWithLongTap,
        preferences.pageTransitionsPager,
        preferences.doubleTapAnimSpeed,
        preferences.readWithVolumeKeys,
        preferences.readWithVolumeKeysInverted,
        preferences.alwaysShowChapterTransition,
        preferences.showNavigationOverlayOnStart,
        // KUN -->
        preferences.pageTransitionsWebtoon,
        preferences.autoscrollInterval,
        preferences.smoothAutoScroll,
        // KUN <--
    ) {
        GeneralSettings(
            theme = ReaderPreferences.ReaderTheme.fromValue(readerTheme.get()),
            keepScreenOn = keepScreenOn.get(),
            grayscale = grayscale.get(),
            invertedColors = invertedColors.get(),
            fullscreen = fullscreen.get(),
            drawUnderCutout = drawUnderCutout.get(),
            longTapEnabled = readWithLongTap.get(),
            usePageTransitions = pageTransitionsPager.get(),
            doubleTapAnimDuration = doubleTapAnimSpeed.get(),
            volumeKeysEnabled = readWithVolumeKeys.get(),
            volumeKeysInverted = readWithVolumeKeysInverted.get(),
            alwaysShowChapterTransition = alwaysShowChapterTransition.get(),
            navigationOverlayOnStart = showNavigationOverlayOnStart.get(),
            // KUN -->
            usePageTransitionsWebtoon = pageTransitionsWebtoon.get(),
            autoscrollInterval = autoscrollInterval.get(),
            smoothAutoScroll = smoothAutoScroll.get(),
            // KUN <--
        )
    }

    /**
     * The custom brightness, 0 when it's off.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val brightness: StateFlow<Int> = preferences.customBrightness.changes()
        .flatMapLatest { enabled ->
            if (enabled) preferences.customBrightnessValue.changes().sample(0.1.seconds) else flowOf(0)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            if (preferences.customBrightness.get()) preferences.customBrightnessValue.get() else 0,
        )

    /**
     * Settings every paged viewer uses.
     */
    val paged = settings(
        preferences.imageScaleType,
        preferences.zoomStart,
        preferences.cropBorders,
        preferences.landscapeZoom,
        preferences.navigateToPan,
        preferences.navigationModePager,
        preferences.pagerNavInverted,
        // KUN -->
        preferences.pagedDisableZoomIn,
        preferences.pagedDoubleTapZoomEnabled,
        preferences.landscapeZoomType,
        preferences.centerMarginType,
        preferences.smallerTapZone,
        // KUN <--
    ) {
        PagedSettings(
            page = PagedSettings.Page(
                imageScaleType = ReaderPreferences.ScaleType.fromValue(imageScaleType.get()),
                zoomStart = ReaderPreferences.ZoomStartType.fromValue(zoomStart.get()),
                cropBorders = cropBorders.get(),
                landscapeZoom = landscapeZoom.get(),
                // KUN -->
                disableZoomIn = pagedDisableZoomIn.get(),
                doubleTapZoom = pagedDoubleTapZoomEnabled.get(),
                landscapeZoomScaleType = landscapeZoomType.get(),
                centerMarginType = ReaderPreferences.CenterMarginType.fromValue(centerMarginType.get()),
                // KUN <--
            ),
            navigateToPan = navigateToPan.get(),
            navigation = NavigationSettings(
                ReaderPreferences.NavigationMode.fromValue(navigationModePager.get()),
                pagerNavInverted.get(),
                // KUN -->
                smallerTapZone = smallerTapZone.get(),
                // KUN <--
            ),
        )
    }

    /**
     * Settings every long strip viewer uses.
     */
    val longStrip = settings(
        preferences.cropBordersWebtoon,
        preferences.webtoonDisableZoomOut,
        // KUN -->
        preferences.cropBordersContinuousVertical,
        preferences.alwaysDecodeLongStripWithSSIV,
        // KUN <--
    ) {
        LongStripSettings(
            cropBorders = cropBordersWebtoon.get(),
            zoomOutDisabled = webtoonDisableZoomOut.get(),
            // KUN -->
            continuousCropBorders = cropBordersContinuousVertical.get(),
            alwaysDecodeLongStripWithSSIV = alwaysDecodeLongStripWithSSIV.get(),
            // KUN <--
        )
    }

    /**
     * Settings only the pager viewers use.
     */
    val simplePaged = settings(
        preferences.dualPageSplitPaged,
        preferences.dualPageInvertPaged,
        preferences.dualPageRotateToFit,
        preferences.dualPageRotateToFitInvert,
        // KUN -->
        preferences.pageLayout,
        preferences.invertDoublePages,
        // KUN <--
    ) {
        // KUN -->
        val layout = ReaderPreferences.PageLayout.fromValue(pageLayout.get())
        val split = dualPageSplitPaged.get()
        // KUN <--
        SimplePagedSettings(
            dualPageSplit = split,
            dualPageInvert = dualPageInvertPaged.get(),
            dualPageRotateToFit = dualPageRotateToFit.get(),
            dualPageRotateToFitInvert = dualPageRotateToFitInvert.get(),
            // KUN -->
            pageLayout = layout,
            doublePages = layout == ReaderPreferences.PageLayout.DOUBLE_PAGES && !split,
            autoDoublePages = layout == ReaderPreferences.PageLayout.AUTOMATIC,
            invertDoublePages = invertDoublePages.get() && !split,
            // KUN <--
        )
    }

    /**
     * Settings only the webtoon viewers use.
     */
    val simpleLongStrip = settings(
        preferences.webtoonSidePadding,
        preferences.dualPageSplitWebtoon,
        preferences.dualPageInvertWebtoon,
        preferences.dualPageRotateToFitWebtoon,
        preferences.dualPageRotateToFitInvertWebtoon,
        preferences.webtoonDoubleTapZoomEnabled,
        preferences.navigationModeWebtoon,
        preferences.webtoonNavInverted,
        // KUN -->
        preferences.webtoonPinchToZoomEnabled,
        preferences.webtoonScaleType,
        preferences.longStripGapSmartScale,
        preferences.smallerTapZone,
        // KUN <--
    ) {
        SimpleLongStripSettings(
            page = SimpleLongStripSettings.Page(
                sidePadding = webtoonSidePadding.get(),
                dualPageSplit = dualPageSplitWebtoon.get(),
                dualPageInvert = dualPageInvertWebtoon.get(),
                dualPageRotateToFit = dualPageRotateToFitWebtoon.get(),
                dualPageRotateToFitInvert = dualPageRotateToFitInvertWebtoon.get(),
            ),
            doubleTapZoom = webtoonDoubleTapZoomEnabled.get(),
            navigation = NavigationSettings(
                ReaderPreferences.NavigationMode.fromValue(navigationModeWebtoon.get()),
                webtoonNavInverted.get(),
                // KUN -->
                smallerTapZone = smallerTapZone.get(),
                // KUN <--
            ),
            // KUN -->
            pinchToZoom = webtoonPinchToZoomEnabled.get(),
            webtoonScaleType = webtoonScaleType.get(),
            longStripGapSmartScale = longStripGapSmartScale.get(),
            // KUN <--
        )
    }

    /**
     * Settings only the paged WebGPU viewers use.
     */
    val webgpuPaged = settings(
        preferences.dualPageView,
        preferences.transitionAnimation,
        preferences.transitionAnimationDual,
        preferences.cutoutMode,
        preferences.cutoutModeDual,
        preferences.disableDoubleTapGestures,
        preferences.imageFilters,
        preferences.tileFilters,
        // KNS -->
        preferences.pagedDoubleTapZoomEnabled,
        preferences.pagedDisableZoomIn,
        // KNS <--
    ) {
        WebGpuPagedSettings(
            page = WebGpuPagedSettings.Page(
                dualPageView = dualPageView.get(),
                transitionAnimation = transitionAnimation.get(),
                transitionAnimationDual = transitionAnimationDual.get(),
                cutoutMode = cutoutMode.get(),
                cutoutModeDual = cutoutModeDual.get(),
            ),
            common = WebGpuSettings(
                doubleTapGesturesDisabled = disableDoubleTapGestures.get(),
                // KNS -->
                zoomGestures = WebGpuZoomGestures.paged(pagedDoubleTapZoomEnabled.get(), pagedDisableZoomIn.get()),
                // KNS <--
                imageFilters = imageFilters.get(),
                tileFilters = tileFilters.get(),
            ),
        )
    }

    /**
     * Settings only the long strip WebGPU viewer uses.
     */
    val webgpuLongStrip = settings(
        preferences.continuousMinWidth,
        preferences.continuousGap,
        preferences.disableDoubleTapGestures,
        preferences.imageFilters,
        preferences.tileFilters,
        // KNS -->
        preferences.webtoonDoubleTapZoomEnabled,
        preferences.webtoonPinchToZoomEnabled,
        // KNS <--
    ) {
        WebGpuLongStripSettings(
            page = WebGpuLongStripSettings.Page(
                continuousMinWidth = continuousMinWidth.get(),
                continuousGap = continuousGap.get(),
            ),
            common = WebGpuSettings(
                doubleTapGesturesDisabled = disableDoubleTapGestures.get(),
                // KNS -->
                zoomGestures = WebGpuZoomGestures.continuous(
                    webtoonDoubleTapZoomEnabled.get(),
                    webtoonPinchToZoomEnabled.get(),
                ),
                // KNS <--
                imageFilters = imageFilters.get(),
                tileFilters = tileFilters.get(),
            ),
        )
    }

    /**
     * Tap zones of the open viewer. Null until a viewer is made.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val navigation: StateFlow<ViewerNavigation?> = viewerMode
        .flatMapLatest { viewerMode ->
            if (viewerMode == null) return@flatMapLatest flowOf(null)

            val usesWebtoonZones = viewerMode.readingMode.type == ReadingMode.ViewerType.Webtoon &&
                viewerMode.isWebtoonViewer
            val settings = if (usesWebtoonZones) {
                simpleLongStrip.map { it.navigation }
            } else {
                paged.map { it.navigation }
            }
            // KUN -->
            // KMK: the navigators take the tap-zone size (R31) with the inversion, so the flow
            // re-emits when either changes.
            val defaultNavigation: (ReaderPreferences.TappingInvertMode, Boolean) -> ViewerNavigation =
                if (viewerMode.readingMode.direction == ReadingMode.Direction.Vertical) {
                    ::LNavigation
                } else {
                    ::RightAndLeftNavigation
                }
            // KUN <--
            settings.distinctUntilChanged().map { it.navigator(defaultNavigation) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Called when a viewer is made for [readingMode].
     */
    // KUN -->
    // `isWebtoonViewer` and `isWebGpuViewer` are the viewer `ReadingMode.toViewer` built, not the
    // high quality renderer preference: a device that falls back from WebGPU still gets a
    // `WebtoonViewer` for a long strip, and the sheet follows the viewer it actually made.
    fun setViewerReadingMode(
        readingMode: ReadingMode,
        isWebtoonViewer: Boolean,
        isWebGpuViewer: Boolean,
    ) {
        viewerMode.value = ViewerMode(readingMode, isWebtoonViewer, isWebGpuViewer)
        // Unknown again, until the viewer just built is drawn and publishes its own box.
        wideViewport.value = null
    }
    // KUN <--

    // KUN -->
    fun setReadingMode(readingMode: ReadingMode) = onChangeReadingMode(readingMode)

    fun setOrientation(orientation: ReaderOrientation) = onChangeOrientation(orientation)
    // KUN <--

    /**
     * A value [read] from [sources], kept current as any of them changes. Started eagerly, so a viewer reading it
     * before collecting gets what the preferences hold now.
     */
    private fun <T> settings(vararg sources: Preference<*>, read: ReaderPreferences.() -> T): StateFlow<T> {
        return sources.map { it.changes() }
            .merge()
            .map { preferences.read() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.read())
    }

    private data class ViewerMode(
        val readingMode: ReadingMode,
        // KUN --> the viewer `ReadingMode.toViewer` made, not the renderer preference
        val isWebtoonViewer: Boolean,
        val isWebGpuViewer: Boolean,
        // KUN <--
    )
}
