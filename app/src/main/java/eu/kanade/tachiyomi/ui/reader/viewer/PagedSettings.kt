package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences

// KUN -->
// Destination plane for the step-2 (8124d324a1) settings migration: the pager viewers move onto this
// then, and that step deletes PagerConfig.kt (with it the fork's image/zoom fields below).
// KUN <--
/**
 * Settings every paged viewer uses.
 */
data class PagedSettings(
    val page: Page,
    val navigateToPan: Boolean,
    val navigation: NavigationSettings,
) {

    /**
     * What the pages are made with, so a change remakes them.
     */
    data class Page(
        val imageScaleType: ReaderPreferences.ScaleType,
        val zoomStart: ReaderPreferences.ZoomStartType,
        val cropBorders: Boolean,
        val landscapeZoom: Boolean,
        // KUN -->
        /** KMK: zooming into a paged image is disabled entirely. */
        val disableZoomIn: Boolean,
        /** KMK: paged double-tap zoom, off also pins the double-tap scale at 1x. */
        val doubleTapZoom: Boolean,
        /** KMK: target scale of the landscape-zoom gesture. */
        val landscapeZoomScaleType: ReaderPreferences.LandscapeZoomScaleType,
        /** SY: blank gutter drawn inside a joined spread; a page is remade when it changes. */
        val centerMarginType: ReaderPreferences.CenterMarginType,
        // KUN <--
    )
}
