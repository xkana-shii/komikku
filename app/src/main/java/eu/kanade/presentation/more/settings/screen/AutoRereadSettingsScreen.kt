package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.track.model.AutoRereadResetMode
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScreen
import mihon.app.di.appGraph
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

// KNS --> Restores main's dedicated Auto Reread settings screen on the Metro foundation.
object AutoRereadSettingsScreen : Screen {
    private fun readResolve(): Any = AutoRereadSettingsScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val trackPreferences = remember { context.appGraph.trackPreferences }

        val items = listOf(
            Preference.PreferenceItem.ListPreference(
                preference = trackPreferences.autoRereadBehavior,
                entries = AutoTrackState.entries.associateWith { stringResource(it.titleRes) },
                title = stringResource(KMR.strings.pref_auto_reread),
                subtitle = stringResource(KMR.strings.pref_auto_reread_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = trackPreferences.autoRereadResetMode,
                entries = AutoRereadResetMode.entries.associateWith { stringResource(it.titleRes) },
                title = stringResource(KMR.strings.pref_auto_reread_reset_mode),
            ),
        )

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(KMR.strings.pref_auto_reread),
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            PreferenceScreen(items = items, contentPadding = paddingValues)
        }
    }
}
// KNS <--
