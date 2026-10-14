package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import eu.kanade.domain.extension.interactor.GetExtensionLanguages.Companion.getLanguageIconID
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.InstalledSourceIcon
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Folder
import mihon.icons.materialsymbols.rounded.LocalLibrary
import mihon.icons.materialsymbols.roundedfilled.Warning
import tachiyomi.domain.source.model.Source
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.source.local.isLocal

@Composable
internal fun DownloadsBadge(count: Int) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
    // KMK -->
    useLangIcon: Boolean = true,
    // KMK <--
) {
    // KMK -->
    if (!isLocal && sourceLanguage.isNotEmpty()) {
        if (useLangIcon) {
            val iconResId = getLanguageIconID(sourceLanguage) ?: R.drawable.globe
            Badge(
                painter = painterResource(id = iconResId),
                color = Color.Transparent,
                modifier = Modifier
                    .width(25.dp)
                    .height(18.dp),
            )
        } else {
            // KMK <--
            Badge(
                text = sourceLanguage.uppercase(),
                color = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

// KMK -->
@Composable
fun SourceIconBadge(
    source: Source?,
) {
    if (source == null) return

    when {
        source.isStub -> {
            Badge(
                imageVector = MaterialSymbols.RoundedFilled.Warning,
                iconColor = MaterialTheme.colorScheme.error,
                color = MaterialTheme.colorScheme.errorContainer,
            )
        }
        source.isLocal() -> {
            Badge(
                imageVector = MaterialSymbols.Rounded.Folder,
                color = MaterialTheme.colorScheme.tertiary,
                iconColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        else -> {
            // Mihon b15af71b94 moved installed-extension icon loading to Coil, replacing `Source.icon()`.
            val painter = rememberAsyncImagePainter(model = InstalledSourceIcon(source.id))
            val state by painter.state.collectAsState()
            if (state is AsyncImagePainter.State.Success) {
                Badge(
                    painter = painter,
                    modifier = Modifier
                        .scale(1.3f)
                        .height(18.dp),
                )
            } else {
                // Default source icon (while loading or if the source has no icon)
                Badge(
                    imageVector = MaterialSymbols.Rounded.LocalLibrary,
                    color = MaterialTheme.colorScheme.tertiary,
                    iconColor = MaterialTheme.colorScheme.onTertiary,
                )
            }
        }
    }
}
// KMK <--

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        BadgeGroup {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "en")
            LanguageBadge(isLocal = false, sourceLanguage = "en", useLangIcon = false)
            LanguageBadge(isLocal = false, sourceLanguage = "vi")
        }
    }
}
