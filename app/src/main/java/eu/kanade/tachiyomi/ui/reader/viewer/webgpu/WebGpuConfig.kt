package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Configuration used by pager viewers.
 */
class WebGpuConfig(
    private val viewer: WebGpuViewer,
    scope: CoroutineScope,
    readerPreferences: ReaderPreferences,
) : ViewerConfig(readerPreferences, scope) {

    var theme = readerPreferences.readerTheme.get()
        private set

    var automaticBackground = false
        private set

    var dualPageSplitChangedListener: ((Boolean) -> Unit)? = null

    var imageScaleType = 1
        private set

    var imageZoomType = ReaderPageImageView.ZoomStartPosition.LEFT
        private set

    var imageCropBorders = false
        private set

    var imageCropBordersWebtoon = false
        private set

    var navigateToPan = false
        private set

    var landscapeZoom = false
        private set

    var transitionAnimation = ReaderPreferences.TransitionAnimation.BASIC
        private set

    var transitionAnimationDual = ReaderPreferences.TransitionAnimation.BASIC
        private set

    var cutoutMode = ReaderPreferences.CutoutMode.AVOID
        private set

    var cutoutModeDual = ReaderPreferences.CutoutMode.AVOID
        private set

    var dualPageView = ReaderPreferences.DualPageView.NEVER
        private set

    var continuousMinWidth = 100
        private set

    var zoomOutDisabled = false
        private set

    var continuousGap = 10
        private set

    init {
        // KUN -->
        // First WebGPU use: adopt TachiyomiSY/Komikku's `pageLayout` dual page choice into
        // `dualPageView`, which is what the WebGPU renderer reads and what its settings chip edits.
        // Without this, enabling the high quality renderer silently reset a dual page layout to
        // single pages (the new pref defaults to NEVER). Seeded once; the user's later `dualPageView`
        // choice wins.
        if (!readerPreferences.dualPageView.isSet()) {
            readerPreferences.dualPageView.set(legacyDualPageView())
        }
        dualPageView = readerPreferences.dualPageView.get()
        // KUN <--

        readerPreferences.readerTheme
            .register(
                {
                    theme = it
                    automaticBackground = it == 3
                },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.imageScaleType
            .register({ imageScaleType = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.zoomStart
            .register({ zoomTypeFromPreference(it) }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.cropBorders
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.cropBordersWebtoon
            .register(
                { imageCropBordersWebtoon = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.navigateToPan
            .register({ navigateToPan = it })

        readerPreferences.landscapeZoom
            .register({ landscapeZoom = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.navigationModePager
            .register({ navigationMode = it }, { updateNavigation(navigationMode) })

        readerPreferences.pagerNavInverted
            .register({ tappingInverted = it }, { navigator.invertMode = it })
        readerPreferences.pagerNavInverted.changes()
            .drop(1)
            .onEach { navigationModeChangedListener?.invoke() }
            .launchIn(scope)

        readerPreferences.dualPageSplitPaged
            .register(
                { dualPageSplit = it },
                {
                    imagePropertyChangedListener?.invoke()
                    dualPageSplitChangedListener?.invoke(it)
                },
            )

        readerPreferences.dualPageInvertPaged
            .register({ dualPageInvert = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageRotateToFit
            .register(
                { dualPageRotateToFit = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.dualPageRotateToFitInvert
            .register(
                { dualPageRotateToFitInvert = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.transitionAnimation.register(
            { transitionAnimation = it },
            { imagePropertyChangedListener?.invoke() },
        )

        readerPreferences.transitionAnimationDual.register(
            { transitionAnimationDual = it },
            { imagePropertyChangedListener?.invoke() },
        )

        readerPreferences.cutoutMode.register(
            { cutoutMode = it },
            { imagePropertyChangedListener?.invoke() },
        )

        readerPreferences.cutoutModeDual.register(
            { cutoutModeDual = it },
            { imagePropertyChangedListener?.invoke() },
        )

        readerPreferences.dualPageView.register(
            { dualPageView = it },
            { imagePropertyChangedListener?.invoke() },
        )

        readerPreferences.continuousMinWidth.register(
            { continuousMinWidth = it },
            { imagePropertyChangedListener?.invoke() },
        )

        readerPreferences.webtoonDisableZoomOut.register(
            { zoomOutDisabled = it },
            { imagePropertyChangedListener?.invoke() },
        )

        readerPreferences.continuousGap.register(
            { continuousGap = it },
            { imagePropertyChangedListener?.invoke() },
        )
    }

    // KUN -->
    /**
     * TachiyomiSY/Komikku's pager dual page choice, as the SSIV viewers see it: `pageLayout` plus the
     * "split wide pages" flag (`dualPageSplitPaged` disables pairing, matching [PagerConfig]).
     *
     * The WebGPU renderer never reads `pageLayout`; it reads the newer `dualPageView` pref. This maps
     * the legacy choice onto that pref so a user's dual page layout survives enabling the high quality
     * renderer. Long strip / webtoon layout prefs have no WebGPU counterpart upstream.
     */
    private fun legacyDualPageView(): ReaderPreferences.DualPageView {
        return when (readerPreferences.pageLayout.get()) {
            PagerConfig.PageLayout.DOUBLE_PAGES -> {
                if (readerPreferences.dualPageSplitPaged.get()) {
                    ReaderPreferences.DualPageView.NEVER
                } else {
                    ReaderPreferences.DualPageView.ALWAYS
                }
            }
            PagerConfig.PageLayout.AUTOMATIC -> ReaderPreferences.DualPageView.WIDE
            else -> ReaderPreferences.DualPageView.NEVER
        }
    }
    // KUN <--

    private fun zoomTypeFromPreference(value: Int) {
        imageZoomType = when (value) {
            // Auto
            1 -> if (viewer.isReversed) {
                ReaderPageImageView.ZoomStartPosition.RIGHT
            } else {
                ReaderPageImageView.ZoomStartPosition.LEFT
            }
            // Left
            2 -> ReaderPageImageView.ZoomStartPosition.LEFT
            // Right
            3 -> ReaderPageImageView.ZoomStartPosition.RIGHT
            // Center
            else -> ReaderPageImageView.ZoomStartPosition.CENTER
        }
    }

    override var navigator: ViewerNavigation = defaultNavigation()
        set(value) {
            field = value.also { it.invertMode = this.tappingInverted }
        }

    override fun defaultNavigation(): ViewerNavigation {
        return if (viewer.isVertical) {
            LNavigation(readerPreferences)
        } else {
            RightAndLeftNavigation(readerPreferences)
        }
    }

    override fun updateNavigation(navigationMode: Int) {
        navigator = when (navigationMode) {
            0 -> defaultNavigation()
            1 -> LNavigation(readerPreferences)
            2 -> KindlishNavigation(readerPreferences)
            3 -> EdgeNavigation(readerPreferences)
            4 -> RightAndLeftNavigation(readerPreferences)
            5 -> DisabledNavigation(readerPreferences)
            else -> defaultNavigation()
        }
        navigationModeChangedListener?.invoke()
    }
}
