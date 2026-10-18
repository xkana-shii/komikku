package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
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

// KMK -->
object AutoRereadSettingsScreen : Screen {
    @Suppress("unused")
    private fun readResolve(): Any = AutoRereadSettingsScreen

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val trackPreferences = context.appGraph.trackPreferences

        val items = listOf(
            Preference.PreferenceItem.ListPreference(
                preference = trackPreferences.autoRereadBehavior,
                entries = AutoTrackState.entries.associateWith { stringResource(it.titleRes) },
                title = stringResource(KMR.strings.pref_auto_reread_behaviour_title),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = trackPreferences.autoRereadResetMode,
                entries = AutoRereadResetMode.entries.associateWith { stringResource(it.titleRes) },
                title = stringResource(KMR.strings.pref_auto_reread_reset_to),
            ),
        )

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(KMR.strings.pref_auto_reread_behavior),
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            PreferenceScreen(
                items = items,
                contentPadding = paddingValues,
            )
        }
    }
}
// KMK <--
