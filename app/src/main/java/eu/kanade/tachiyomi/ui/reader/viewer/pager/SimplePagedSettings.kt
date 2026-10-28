package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences

// KUN -->
// The step-2 (8124d324a1) settings plane: the pager viewers read this instead of the retired
// PagerConfig, and it carries the fork's dual page fields below.
// KUN <--
/**
 * Settings only the pager viewers use, all of them part of what the pages are made with.
 */
data class SimplePagedSettings(
    val dualPageSplit: Boolean,
    val dualPageInvert: Boolean,
    val dualPageRotateToFit: Boolean,
    val dualPageRotateToFitInvert: Boolean,
    // KUN -->
    /** SY: the stored dual page layout, which [doublePages] and [autoDoublePages] are derived from. */
    val pageLayout: ReaderPreferences.PageLayout,
    /** SY: this manga is read as two pages at once right now. */
    val doublePages: Boolean,
    /** SY: the layout is [ReaderPreferences.PageLayout.AUTOMATIC], so double pages follow the orientation. */
    val autoDoublePages: Boolean,
    /** SY: which side of a pair the first page sits on. */
    val invertDoublePages: Boolean,
    // KUN <--
)
