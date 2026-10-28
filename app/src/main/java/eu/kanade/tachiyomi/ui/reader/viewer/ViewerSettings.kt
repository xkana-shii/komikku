package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView.ZoomStartPosition
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// KUN -->
// The step-2 (8124d324a1) settings plane: every viewer reads these instead of the retired
// ViewerConfig / PagerConfig / WebtoonConfig / WebGpuConfig objects.
// KUN <--
/**
 * A viewer's tap zones, as the preferences store them.
 */
data class NavigationSettings(
    val mode: ReaderPreferences.NavigationMode,
    val inverted: ReaderPreferences.TappingInvertMode,
    // KUN -->
    /** KMK: shrink the prev/next tap zones from 33% to 25%, growing the menu region. */
    val smallerTapZone: Boolean,
    // KUN <--
) {

    // KUN -->
    // KMK: upstream's value-based navigator, plus the tap-zone size (R31) as a second value every
    // navigator needs for its geometry.
    fun navigator(default: (ReaderPreferences.TappingInvertMode, Boolean) -> ViewerNavigation): ViewerNavigation {
        return when (mode) {
            ReaderPreferences.NavigationMode.DEFAULT -> default(inverted, smallerTapZone)
            ReaderPreferences.NavigationMode.L_SHAPED -> LNavigation(inverted, smallerTapZone)
            ReaderPreferences.NavigationMode.KINDLISH -> KindlishNavigation(inverted, smallerTapZone)
            ReaderPreferences.NavigationMode.EDGE -> EdgeNavigation(inverted, smallerTapZone)
            ReaderPreferences.NavigationMode.RIGHT_AND_LEFT -> RightAndLeftNavigation(inverted, smallerTapZone)
            ReaderPreferences.NavigationMode.DISABLED -> DisabledNavigation(inverted, smallerTapZone)
        }
    }
    // KUN <--
}

/**
 * Where a page starts zoomed for this setting, where [auto] is what automatic means for the viewer.
 */
fun ReaderPreferences.ZoomStartType.position(auto: ZoomStartPosition): ZoomStartPosition {
    return when (this) {
        ReaderPreferences.ZoomStartType.AUTOMATIC -> auto
        ReaderPreferences.ZoomStartType.LEFT -> ZoomStartPosition.LEFT
        ReaderPreferences.ZoomStartType.RIGHT -> ZoomStartPosition.RIGHT
        ReaderPreferences.ZoomStartType.CENTER -> ZoomStartPosition.CENTER
    }
}

/**
 * Runs [action] in [scope] with each value and the one before it, null for the first, so a viewer applies a change
 * in one place by comparing the parts it reacts to.
 */
fun <T : Any> Flow<T>.collectChanges(scope: CoroutineScope, action: (old: T?, new: T) -> Unit) {
    scope.launch {
        var old: T? = null
        collect {
            action(old, it)
            old = it
        }
    }
}
