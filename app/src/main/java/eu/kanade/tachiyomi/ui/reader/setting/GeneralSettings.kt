package eu.kanade.tachiyomi.ui.reader.setting

// KUN -->
import android.graphics.Color
// KUN <--

// KUN -->
// Destination plane for the step-2 (8124d324a1) settings migration: the activity and every viewer move
// onto this then, and that step deletes ViewerConfig.kt / PagerConfig.kt / WebtoonConfig.kt /
// WebGpuConfig.kt.
// KUN <--
/**
 * Settings for the reader as a whole and for every viewer alike.
 */
data class GeneralSettings(
    val theme: ReaderPreferences.ReaderTheme,
    val keepScreenOn: Boolean,
    val grayscale: Boolean,
    val invertedColors: Boolean,
    val fullscreen: Boolean,
    val drawUnderCutout: Boolean,
    val longTapEnabled: Boolean,
    val usePageTransitions: Boolean,
    val doubleTapAnimDuration: Int,
    val volumeKeysEnabled: Boolean,
    val volumeKeysInverted: Boolean,
    val alwaysShowChapterTransition: Boolean,
    val navigationOverlayOnStart: Boolean,
    // KUN -->
    /**
     * SY: the webtoon viewers' own page-transition toggle. Upstream has a single value for both
     * viewers, which this fork feeds into [usePageTransitions] from the pager toggle; a viewer must
     * read the field that belongs to it.
     */
    val usePageTransitionsWebtoon: Boolean,
    /** SY: seconds between autoscroll steps, `-1` when autoscroll is off. */
    val autoscrollInterval: Float,
    /** SY: glide a screen per autoscroll step instead of jumping one page. */
    val smoothAutoScroll: Boolean,
    /** SY: two-finger tap / long-press / swipe autoscroll gestures. */
    val autoscrollGesturesEnabled: Boolean,
    /** SY: toast what an autoscroll gesture did. */
    val autoscrollGestureToastsEnabled: Boolean,
    // KUN <--
) {
    val automaticBackground: Boolean
        get() = theme == ReaderPreferences.ReaderTheme.AUTOMATIC

    // KUN -->
    /**
     * SY: the colour page holders draw behind a page and in the gutter of a merged spread, derived from
     * [theme] exactly as `PagerConfig.themeToColor` did: [ReaderPreferences.ReaderTheme.AUTOMATIC]
     * resolves to white, not to the night mode the activity chrome follows.
     */
    val pageCanvasColor: Int
        get() = when (theme) {
            ReaderPreferences.ReaderTheme.BLACK -> Color.BLACK
            ReaderPreferences.ReaderTheme.GRAY -> 0x202125
            else -> Color.WHITE
        }
    // KUN <--
}
