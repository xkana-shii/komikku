package eu.kanade.presentation.reader.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.ui.reader.automation.AutomationSpeed
import eu.kanade.tachiyomi.ui.reader.automation.AutomationTarget
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.util.system.toast
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Remove
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KNS -->
/**
 * Slower / start-stop / faster buttons for reader automation, shown above the reader's bottom bar
 * when automation is enabled for the current kind of viewer (scrolling or page flipping).
 */
@Composable
fun ReaderAutomationControls(
    viewer: Viewer?,
    preferences: ReaderPreferences,
    running: Boolean,
    onToggle: () -> Unit,
) {
    val autoScroll by preferences.autoScroll.collectAsState()
    val autoFlip by preferences.autoFlip.collectAsState()
    val target = remember(viewer, autoScroll, autoFlip) { AutomationTarget.of(viewer, preferences) } ?: return
    val context = LocalContext.current
    // The reader bars' background, as the previous personal fork matched it.
    val background = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = background,
        disabledContainerColor = background,
    )

    fun adjust(faster: Boolean) {
        if (target is AutomationTarget.Scroll) {
            val pref = preferences.autoScrollSpeed
            val value = if (faster) AutomationSpeed.faster(pref.get()) else AutomationSpeed.slower(pref.get())
            pref.set(value)
            context.toast(context.pluralStringResource(KMR.plurals.pref_auto_scroll_speed_summary, value, value))
        } else {
            val pref = preferences.autoFlipInterval
            val value = if (faster) AutomationSpeed.fasterFlip(pref.get()) else AutomationSpeed.slowerFlip(pref.get())
            pref.set(value)
            context.toast(context.pluralStringResource(KMR.plurals.pref_auto_flip_interval_summary, value, value))
        }
    }

    // As on the previous personal fork: a column at the end, faster above start/stop above slower.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTabletUi()) 24.dp else 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        FilledIconButton(onClick = { adjust(faster = true) }, colors = colors) {
            Icon(MaterialSymbols.Rounded.Add, stringResource(KMR.strings.action_automation_increase_speed))
        }
        FilledIconButton(onClick = onToggle, colors = colors) {
            if (running) {
                Icon(MaterialSymbols.RoundedFilled.Pause, stringResource(KMR.strings.action_automation_stop))
            } else {
                Icon(MaterialSymbols.RoundedFilled.PlayArrow, stringResource(KMR.strings.action_automation_start))
            }
        }
        FilledIconButton(onClick = { adjust(faster = false) }, colors = colors) {
            Icon(MaterialSymbols.Rounded.Remove, stringResource(KMR.strings.action_automation_decrease_speed))
        }
    }
}
// KNS <--
