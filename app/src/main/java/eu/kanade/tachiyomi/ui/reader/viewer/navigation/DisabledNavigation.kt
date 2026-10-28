package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TappingInvertMode
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation

/**
 * Visualization of default state without any inversion
 * +---+---+---+
 * | M | M | M |   P: Previous
 * +---+---+---+
 * | M | M | M |   M: Menu
 * +---+---+---+
 * | M | M | M |   N: Next
 * +---+---+---+
*/
class DisabledNavigation(
    invertMode: TappingInvertMode,
    // KUN -->
    // KMK: the tap-zone size (R31) travels with the navigator.
    smallerTapZone: Boolean = false,
    // KUN <--
) : ViewerNavigation(invertMode, smallerTapZone) {

    override val regionList: List<Region> = emptyList()
}
