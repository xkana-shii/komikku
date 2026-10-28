package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import android.graphics.RectF
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TappingInvertMode
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation

/**
 * Visualization of default state without any inversion
 * +---+---+---+
 * | N | M | P |   P: Move Right
 * +---+---+---+
 * | N | M | P |   M: Menu
 * +---+---+---+
 * | N | M | P |   N: Move Left
 * +---+---+---+
 */
class RightAndLeftNavigation(
    invertMode: TappingInvertMode,
    // KUN -->
    // KMK: the tap-zone size (R31) travels with the navigator.
    smallerTapZone: Boolean = false,
    // KUN <--
) : ViewerNavigation(invertMode, smallerTapZone) {

    override val regionList: List<Region> = listOf(
        Region(
            rectF = RectF(0f, 0f, regionSize1, 1f),
            type = NavigationRegion.LEFT,
        ),
        Region(
            rectF = RectF(regionSize2, 0f, 1f, 1f),
            type = NavigationRegion.RIGHT,
        ),
    )
}
