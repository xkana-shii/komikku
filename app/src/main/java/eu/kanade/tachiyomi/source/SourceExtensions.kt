package eu.kanade.tachiyomi.source

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import exh.source.EH_PACKAGE
import exh.source.LOCAL_SOURCE_PACKAGE
import exh.source.isEhBasedSource
import tachiyomi.domain.source.model.StubSource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.domain.ui.UiPreferences

fun Source.getNameForMangaInfo(
    // SY -->
    mergeSources: List<Source>? = null,
    // SY <--
    uiPreferences: UiPreferences? = null, // Optional UiPreferences for flag setting
): String {
    val preferences = Injekt.get<SourcePreferences>()
    val enabledLanguages = preferences.enabledLanguages().get()
        .filterNot { it in listOf("none") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    val showFlags = uiPreferences?.showFlags()?.get() ?: true
    return when {
        // SY -->
        !mergeSources.isNullOrEmpty() -> getMergedSourcesString(
            mergeSources,
            enabledLanguages,
            hasOneActiveLanguages,
            showFlags,
        )
        // SY <--
        // KMK -->
        isLocalOrStub() -> toString()
        // KMK <--
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages ->
            // KMK -->
            if (showFlags) {
                "$name (${FlagEmoji.getEmojiLangFlag(lang)})"
            } else {
                "$name (${lang.uppercase()})"
            }
        // KMK <--
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> "$name (${lang.uppercase()})"
        else ->
            // KMK -->
            if (showFlags) {
                "$name (${FlagEmoji.getEmojiLangFlag(lang)})"
            } else {
                "$name (${lang.uppercase()})"
            }
        // KMK <--
    }
}

// SY -->
private fun getMergedSourcesString(
    mergeSources: List<Source>,
    enabledLangs: List<String>,
    onlyName: Boolean,
    showFlags: Boolean = true,
): String {
    return if (onlyName) {
        mergeSources.joinToString { source ->
            when {
                // KMK -->
                source.isLocalOrStub() -> source.toString()
                // KMK <--
                source.lang !in enabledLangs ->
                    // KMK -->
                    if (showFlags) {
                        "${source.name} (${FlagEmoji.getEmojiLangFlag(source.lang)})"
                    } else {
                        "${source.name} (${source.lang.uppercase()})"
                    }
                // KMK <--
                else ->
                    "${source.name} (${source.lang.uppercase()})"
            }
        }
    } else {
        mergeSources.joinToString { source ->
            // KMK -->
            if (source.isLocalOrStub()) {
                source.toString()
            } else {
                if (showFlags) {
                    "${source.name} (${FlagEmoji.getEmojiLangFlag(source.lang)})"
                } else {
                    "${source.name} (${source.lang.uppercase()})"
                }
            }
            // KMK <--
        }
    }
}
// SY <--

fun Source.isLocalOrStub(): Boolean = isLocal() || this is StubSource

// KMK -->
fun Source.isIncognitoModeEnabled(incognitoExtensions: Set<String>? = null): Boolean {
    val extensionPackage = when {
        isLocal() -> LOCAL_SOURCE_PACKAGE
        isEhBasedSource() -> EH_PACKAGE
        else -> Injekt.get<ExtensionManager>().getExtensionPackage(id)
    }
    return extensionPackage in (incognitoExtensions ?: Injekt.get<SourcePreferences>().incognitoExtensions().get())
}
// KMK <--
