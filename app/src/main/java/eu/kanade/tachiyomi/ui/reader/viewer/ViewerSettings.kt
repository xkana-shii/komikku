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
// Destination plane for the step-2 (8124d324a1) settings migration: the viewers move onto these
// settings objects then, and that step deletes ViewerConfig.kt.
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
    /**
     * KMK: this fork's [ViewerNavigation] subclasses still take the preferences because the tap-zone
     * size above is read inside them; step 3 gives them `(invertMode, isReversed)` instead and this
     * property goes with it.
     */
    val readerPreferences: ReaderPreferences,
    // KUN <--
) {

    fun navigator(default: () -> ViewerNavigation): ViewerNavigation {
        val navigation = when (mode) {
            ReaderPreferences.NavigationMode.DEFAULT -> default()
            // KUN -->
            ReaderPreferences.NavigationMode.L_SHAPED -> LNavigation(readerPreferences)
            ReaderPreferences.NavigationMode.KINDLISH -> KindlishNavigation(readerPreferences)
            ReaderPreferences.NavigationMode.EDGE -> EdgeNavigation(readerPreferences)
            ReaderPreferences.NavigationMode.RIGHT_AND_LEFT -> RightAndLeftNavigation(readerPreferences)
            ReaderPreferences.NavigationMode.DISABLED -> DisabledNavigation(readerPreferences)
            // KUN <--
        }
        return navigation.also { it.invertMode = inverted }
    }
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
