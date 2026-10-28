package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.NavigationSettings

// KUN -->
// The step-2 (8124d324a1) settings plane: the webtoon viewers read this instead of the retired
// WebtoonConfig, and it carries the fork's fields below.
// KUN <--
/**
 * Settings only the webtoon viewers use.
 */
data class SimpleLongStripSettings(
    val page: Page,
    val doubleTapZoom: Boolean,
    val navigation: NavigationSettings,
    // KUN -->
    /** KMK: two-finger scaling in the strip; double-tap zoom stays [doubleTapZoom]. */
    val pinchToZoom: Boolean,
    /** KMK: strip zoom ratio (fit screen / 4:3 / 3:2 / 16:9 / 20:9). */
    val webtoonScaleType: ReaderPreferences.WebtoonScaleType,
    /** KMK: only apply [webtoonScaleType] in the "vertical with gaps" mode. */
    val longStripGapSmartScale: Boolean,
    // KUN <--
) {

    /**
     * What the pages are made with, so a change remakes them.
     */
    data class Page(
        val sidePadding: Int,
        val dualPageSplit: Boolean,
        val dualPageInvert: Boolean,
        val dualPageRotateToFit: Boolean,
        val dualPageRotateToFitInvert: Boolean,
    )
}
