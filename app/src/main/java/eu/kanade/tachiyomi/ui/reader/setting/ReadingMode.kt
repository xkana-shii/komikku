package eu.kanade.tachiyomi.ui.reader.setting

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.vector.ImageVector
import ca.mpreg.webgpuviewer.renderer.WebGpuRenderer
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.L2RPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewerContinuous
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import logcat.LogPriority
import mihon.app.di.appGraph
import mihon.icons.custommaterialsymbols.CustomMaterialSymbols
import mihon.icons.custommaterialsymbols.rounded.ReaderLeftToRight
import mihon.icons.custommaterialsymbols.rounded.ReaderLongStrip
import mihon.icons.custommaterialsymbols.rounded.ReaderLongStripGaps
import mihon.icons.custommaterialsymbols.rounded.ReaderRightToLeft
import mihon.icons.custommaterialsymbols.rounded.ReaderVertical
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.MobileGear
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

enum class ReadingMode(
    val stringRes: StringResource,
    val icon: ImageVector,
    val flagValue: Int,
    val direction: Direction? = null,
    val type: ViewerType? = null,
) {
    DEFAULT(MR.strings.label_default, MaterialSymbols.Rounded.MobileGear, 0x00000000),
    LEFT_TO_RIGHT(
        MR.strings.left_to_right_viewer,
        CustomMaterialSymbols.Rounded.ReaderLeftToRight,
        0x00000001,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    RIGHT_TO_LEFT(
        MR.strings.right_to_left_viewer,
        CustomMaterialSymbols.Rounded.ReaderRightToLeft,
        0x00000002,
        Direction.Horizontal,
        ViewerType.Pager,
    ),
    VERTICAL(
        MR.strings.vertical_viewer,
        CustomMaterialSymbols.Rounded.ReaderVertical,
        0x00000003,
        Direction.Vertical,
        ViewerType.Pager,
    ),
    WEBTOON(
        MR.strings.webtoon_viewer,
        CustomMaterialSymbols.Rounded.ReaderLongStrip,
        0x00000004,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    CONTINUOUS_VERTICAL(
        MR.strings.vertical_plus_viewer,
        CustomMaterialSymbols.Rounded.ReaderLongStripGaps,
        0x00000005,
        Direction.Vertical,
        ViewerType.Webtoon,
    ),
    ;

    companion object {
        const val MASK = 0x00000007

        fun fromPreference(preference: Int?): ReadingMode = entries.find { it.flagValue == preference } ?: DEFAULT

        fun isPagerType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode.type is ViewerType.Pager
        }

        // KUN -->
        /**
         * Whether the high quality WebGPU renderer is usable on this device.
         *
         * The library performs its GPU bring-up (native library, instance, adapter, device) in a
         * static initializer that catches its own failures; [WebGpuRenderer.isAvailable] reports the
         * outcome. Probing it lets an unsupported device fall back to the SSIV viewer rather than
         * handing the reader a viewer whose renderer silently no-ops.
         */
        private fun isHighQualityRendererAvailable(): Boolean = runCatching {
            WebGpuRenderer.isAvailable
        }.getOrElse {
            logcat(LogPriority.WARN, it) { "High quality renderer probe failed" }
            false
        }

        /**
         * TachiyomiSY/Komikku's pager dual page choice, as the SSIV viewers see it: `pageLayout` plus the
         * "split wide pages" flag (`dualPageSplitPaged` disables pairing, as `PagerConfig` did).
         *
         * The WebGPU renderer never reads `pageLayout`; it reads the newer `dualPageView` pref. This maps
         * the legacy choice onto that pref so a user's dual page layout survives enabling the high quality
         * renderer. Long strip / webtoon layout prefs have no WebGPU counterpart upstream.
         */
        private fun legacyDualPageView(preferences: ReaderPreferences): ReaderPreferences.DualPageView {
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

        /**
         * First WebGPU use: adopt the legacy `pageLayout` choice into `dualPageView`, which is what the
         * WebGPU renderer reads and what its settings chip edits. Without this, enabling the high quality
         * renderer silently reset a dual page layout to single pages (the new pref defaults to NEVER).
         * Called where a WebGPU viewer is actually built, so a session that never gets one leaves the pref
         * unset; seeded once, and the user's later `dualPageView` choice wins.
         */
        private fun seedDualPageView(preferences: ReaderPreferences) {
            if (!preferences.dualPageView.isSet()) {
                preferences.dualPageView.set(legacyDualPageView(preferences))
            }
        }
        // KUN <--

        fun toViewer(
            preference: Int?,
            activity: ReaderActivity,
            // KMK -->
            @ColorInt seedColor: Int?,
            // KMK <--
        ): Viewer {
            // KUN -->
            // Capability gate: the renderer's static initializer runs the GPU bring-up and records
            // any failure, so `isAvailable` tells us whether the high quality path can actually draw.
            // Without this, an unsupported device would keep a WebGpuViewer whose renderer no-ops,
            // i.e. a blank reader, instead of falling back to the SSIV viewer.
            val preferHighQualityRenderer = activity.appGraph.basePreferences.highQualityRenderer.get()
            val useHighQualityRenderer = preferHighQualityRenderer && isHighQualityRendererAvailable()
            if (preferHighQualityRenderer && !useHighQualityRenderer) {
                logcat(LogPriority.WARN) {
                    "High quality renderer unavailable, falling back to the SSIV viewer: " +
                        runCatching { WebGpuRenderer.unavailableReason }.getOrNull().orEmpty()
                }
            }
            if (useHighQualityRenderer) {
                // KUN -->
                // Seeded here rather than on reader open: only a WebGPU viewer reads `dualPageView`, and a
                // device that falls back to the SSIV viewer must not freeze the pref from its layout.
                seedDualPageView(activity.appGraph.readerPreferences)
                // KUN <--
                return when (fromPreference(preference)) {
                    LEFT_TO_RIGHT -> WebGpuViewer(activity, isReversed = false, isVertical = false)
                    RIGHT_TO_LEFT -> WebGpuViewer(activity, isReversed = true, isVertical = false)
                    VERTICAL -> WebGpuViewer(activity, isReversed = false, isVertical = true)
                    WEBTOON -> WebGpuViewerContinuous(activity, useGap = false)
                    CONTINUOUS_VERTICAL -> WebGpuViewerContinuous(activity, useGap = true)
                    DEFAULT -> throw IllegalStateException("Preference value must be resolved: $preference")
                }
            }
            // KUN <--
            return when (fromPreference(preference)) {
                LEFT_TO_RIGHT -> L2RPagerViewer(
                    activity,
                    // KMK -->
                    seedColor = seedColor,
                    // KMK <--
                )
                RIGHT_TO_LEFT -> R2LPagerViewer(
                    activity,
                    // KMK -->
                    seedColor = seedColor,
                    // KMK <--
                )
                VERTICAL -> VerticalPagerViewer(
                    activity,
                    // KMK -->
                    seedColor = seedColor,
                    // KMK <--
                )
                WEBTOON -> WebtoonViewer(
                    activity,
                    // KMK -->
                    seedColor = seedColor,
                    // KMK <--
                )
                CONTINUOUS_VERTICAL -> WebtoonViewer(
                    activity,
                    isContinuous = false,
                    // KMK -->
                    seedColor = seedColor,
                    // KMK <--
                )
                DEFAULT -> throw IllegalStateException("Preference value must be resolved: $preference")
            }
        }
    }

    sealed interface Direction {
        data object Horizontal : Direction
        data object Vertical : Direction
    }

    sealed interface ViewerType {
        data object Pager : ViewerType
        data object Webtoon : ViewerType
    }
}
