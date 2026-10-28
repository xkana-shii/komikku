package eu.kanade.tachiyomi.ui.reader.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.viewer.LongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.NavigationSettings
import eu.kanade.tachiyomi.ui.reader.viewer.PagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.SimplePagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuLongStripSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuPagedSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuSettings
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.SimpleLongStripSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
    val viewerFlow: StateFlow<Viewer?>,
    val onChangeReadingMode: (ReadingMode) -> Unit,
    val onChangeOrientation: (ReaderOrientation) -> Unit,
    val preferences: ReaderPreferences,
    private val basePreferences: BasePreferences,
) : ViewModel() {

    // KUN -->
    // First WebGPU use: adopt TachiyomiSY/Komikku's `pageLayout` dual page choice into `dualPageView`,
    // which is what the WebGPU renderer reads and what its settings chip edits. Without this, enabling
    // the high quality renderer silently reset a dual page layout to single pages (the new pref
    // defaults to NEVER). Seeded once; the user's later `dualPageView` choice wins.
    init {
        if (!preferences.dualPageView.isSet()) {
            preferences.dualPageView.set(legacyDualPageView())
        }
    }
    // KUN <--

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * The reading mode the viewer is made for, with the default resolved.
     */
    val readingMode = combine(mangaFlow, preferences.defaultReadingMode.changes()) { manga, default ->
        ReadingMode.fromPreference(manga?.readingMode?.toInt())
            .takeIf { it != ReadingMode.DEFAULT }
            ?: ReadingMode.fromPreference(default)
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            // KUN -->
            resolveReadingMode(readerState.value),
            // KUN <--
        )

    /**
     * Whether the viewers are the WebGPU ones.
     */
    val isWebGpu = basePreferences.highQualityRenderer

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
        preferences.autoscrollGesturesEnabled,
        preferences.autoscrollGestureToastsEnabled,
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
            autoscrollGesturesEnabled = autoscrollGesturesEnabled.get(),
            autoscrollGestureToastsEnabled = autoscrollGestureToastsEnabled.get(),
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
                readerPreferences = preferences,
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
                readerPreferences = preferences,
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
    ) {
        WebGpuLongStripSettings(
            page = WebGpuLongStripSettings.Page(
                continuousMinWidth = continuousMinWidth.get(),
                continuousGap = continuousGap.get(),
            ),
            common = WebGpuSettings(
                doubleTapGesturesDisabled = disableDoubleTapGestures.get(),
                imageFilters = imageFilters.get(),
                tileFilters = tileFilters.get(),
            ),
        )
    }

    // KUN -->
    fun setReadingMode(readingMode: ReadingMode) = onChangeReadingMode(readingMode)

    fun setOrientation(orientation: ReaderOrientation) = onChangeOrientation(orientation)
    // KUN <--

    /**
     * Whether a viewer must show its tap zones because the reader is new to the user, which it is only once.
     */
    fun takeNavigationOverlayForNewUser(): Boolean {
        val show = preferences.showNavigationOverlayNewUser.get()
        if (show) {
            preferences.showNavigationOverlayNewUser.set(false)
        }
        return show
    }

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

    // KUN -->
    /**
     * The reading mode [readerState] holds now, resolved the way [readingMode] resolves it. The fork's
     * auto-webtoon resolution (R45) stays in `ReaderViewModel.getMangaReadingMode`, which the activity
     * uses; this only reads the stored value, like the flow.
     */
    private fun resolveReadingMode(state: ReaderViewModel.State): ReadingMode {
        return ReadingMode.fromPreference(state.manga?.readingMode?.toInt())
            .takeIf { it != ReadingMode.DEFAULT }
            ?: ReadingMode.fromPreference(preferences.defaultReadingMode.get())
    }

    /**
     * TachiyomiSY/Komikku's pager dual page choice, as the SSIV viewers see it: `pageLayout` plus the
     * "split wide pages" flag (`dualPageSplitPaged` disables pairing, matching `PagerConfig`).
     *
     * The WebGPU renderer never reads `pageLayout`; it reads the newer `dualPageView` pref. This maps
     * the legacy choice onto that pref so a user's dual page layout survives enabling the high quality
     * renderer. Long strip / webtoon layout prefs have no WebGPU counterpart upstream.
     */
    private fun legacyDualPageView(): ReaderPreferences.DualPageView {
        return when (ReaderPreferences.PageLayout.fromValue(preferences.pageLayout.get())) {
            ReaderPreferences.PageLayout.DOUBLE_PAGES -> {
                if (preferences.dualPageSplitPaged.get()) {
                    ReaderPreferences.DualPageView.NEVER
                } else {
                    ReaderPreferences.DualPageView.ALWAYS
                }
            }
            ReaderPreferences.PageLayout.AUTOMATIC -> ReaderPreferences.DualPageView.WIDE
            ReaderPreferences.PageLayout.SINGLE_PAGE -> ReaderPreferences.DualPageView.NEVER
        }
    }
    // KUN <--
}
