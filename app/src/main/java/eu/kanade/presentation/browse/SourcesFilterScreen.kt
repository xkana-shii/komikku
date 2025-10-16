package eu.kanade.presentation.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun SourcesFilterScreen(
    navigateUp: () -> Unit,
    state: SourcesFilterScreenModel.State.Success,
    onClickLanguage: (String) -> Unit,
    onClickSource: (Source) -> Unit,
    // SY -->
    onClickSources: (Boolean, List<Source>) -> Unit,
    // SY <--
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_sources),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.source_filter_empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }
        SourcesFilterContent(
            contentPadding = contentPadding,
            state = state,
            onClickLanguage = onClickLanguage,
            onClickSource = onClickSource,
            // SY -->
            onClickSources = onClickSources,
            // SY <--
        )
    }
}

@Composable
private fun SourcesFilterContent(
    contentPadding: PaddingValues,
    state: SourcesFilterScreenModel.State.Success,
    onClickLanguage: (String) -> Unit,
    onClickSource: (Source) -> Unit,
    // SY -->
    onClickSources: (Boolean, List<Source>) -> Unit,
    // SY <--
) {
    val uiPreferences = Injekt.get<UiPreferences>() // <-- Added for flag settings
    FastScrollLazyColumn(
        // KMK -->
        // Using modifier instead of contentPadding so we can use stickyHeader
        modifier = Modifier.padding(contentPadding),
        // KMK <--
    ) {
        state.items.forEach { (language, sources) ->
            val enabled = language in state.enabledLanguages
            // KMK -->
            stickyHeader(
                // KMK <--
                key = language,
                contentType = "source-filter-header",
            ) {
                SourcesFilterHeader(
                    modifier = Modifier
                        // KMK -->
                        .padding(end = MaterialTheme.padding.small)
                        // KMK <--
                        .animateItemFastScroll(),
                    language = language,
                    enabled = enabled,
                    onClickItem = onClickLanguage,
                    uiPreferences = uiPreferences, // <-- pass preferences
                )
            }
            if (enabled) {
                // SY -->
                // KMK -->
                stickyHeader(
                    // KMK <--
                    key = "$STICKY_HEADER_KEY_PREFIX-toggle-$language",
                    contentType = "source-filter-toggle",
                ) {
                    val toggleEnabled = remember(state.disabledSources) {
                        sources.none { it.id.toString() in state.disabledSources }
                    }
                    SourcesFilterToggle(
                        modifier = Modifier
                            // KMK -->
                            .background(MaterialTheme.colorScheme.background)
                            .padding(end = MaterialTheme.padding.small)
                            // KMK <--
                            .animateItemFastScroll(),
                        isEnabled = toggleEnabled,
                        // KMK -->
                        language = language,
                        // KMK <--
                        onClickItem = {
                            onClickSources(!toggleEnabled, sources)
                        },
                        uiPreferences = uiPreferences, // <-- pass preferences
                    )
                }
                // SY <--
                items(
                    items = sources,
                    key = { "source-filter-${it.key()}" },
                    contentType = { "source-filter-item" },
                ) { source ->
                    SourcesFilterItem(
                        modifier = Modifier
                            // KMK -->
                            .padding(end = MaterialTheme.padding.small)
                            // KMK <--
                            .animateItemFastScroll(),
                        source = source,
                        enabled = "${source.id}" !in state.disabledSources,
                        onClickItem = onClickSource,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcesFilterHeader(
    language: String,
    enabled: Boolean,
    onClickItem: (String) -> Unit,
    uiPreferences: UiPreferences, // <-- added
    modifier: Modifier = Modifier,
) {
    val showFlags = uiPreferences.showFlags().get()
    val context = LocalContext.current
    val title = if (showFlags) {
        LocaleHelper.getSourceDisplayName(language, context) +
            // KMK -->
            (
                " (${LocaleHelper.getDisplayName(language)} ${FlagEmoji.getEmojiLangFlag(language)})"
                    .takeIf { language !in listOf("all", "other") } ?: " (${FlagEmoji.getEmojiLangFlag(language)})"
                )
        // KMK <--
    } else {
        LocaleHelper.getSourceDisplayName(language, context)
    }
    SwitchPreferenceWidget(
        modifier = modifier,
        title = title,
        checked = enabled,
        onCheckedChanged = { onClickItem(language) },
    )
}

// SY -->
@Composable
fun SourcesFilterToggle(
    modifier: Modifier,
    isEnabled: Boolean,
    // KMK -->
    language: String,
    // KMK <--
    onClickItem: () -> Unit,
    uiPreferences: UiPreferences, // <-- added
) {
    val showFlags = uiPreferences.showFlags().get()
    val title = if (showFlags) {
        stringResource(SYMR.strings.pref_category_all_sources) +
            // KMK -->
            " (${FlagEmoji.getEmojiLangFlag(language)})"
    } else {
        stringResource(SYMR.strings.pref_category_all_sources)
    }
    SwitchPreferenceWidget(
        modifier = modifier,
        title = title,
        checked = isEnabled,
        onCheckedChanged = { onClickItem() },
    )
}

// SY <--

@Composable
private fun SourcesFilterItem(
    source: Source,
    enabled: Boolean,
    onClickItem: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        // KMK -->
        // showLanguageInContent = false,
        // KMK <--
        onClickItem = { onClickItem(source) },
        action = {
            Checkbox(checked = enabled, onCheckedChange = null)
        },
    )
}
