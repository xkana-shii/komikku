package eu.kanade.presentation.browse.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import mihon.app.di.appGraph
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.util.collectAsState

// KNS -->
/** The "Show language flags" setting, observed so Browse follows a change right away. */
@Composable
fun showLanguageFlags(): Boolean {
    val showFlags by LocalContext.current.appGraph.uiPreferences.showFlags.collectAsState()
    return showFlags
}

/** [FlagEmoji] for [lang] followed by a space, or nothing when flags are turned off. */
fun languageFlagPrefix(lang: String, showFlags: Boolean): String =
    if (showFlags) FlagEmoji.getEmojiLangFlag(lang) + " " else ""

/** The short language mark after a source name: its flag, or the language code when flags are off. */
fun languageMark(lang: String, showFlags: Boolean): String =
    if (showFlags) FlagEmoji.getEmojiLangFlag(lang) else lang.uppercase()
// KNS <--
