package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.util.lang.toRelativeString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Instant

@Composable
fun relativeDateText(
    date: Instant?,
): String {
    return relativeDateText(
        localDate = date?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
    )
}

@Composable
fun relativeDateText(
    localDate: LocalDate?,
): String {
    // KUN -->
    val format = relativeDateFormatter()

    return localDate?.let(format)
        ?: stringResource(MR.strings.not_applicable)
    // KUN <--
}

// KUN -->
/**
 * [relativeDateText] as a plain function, for callers that need the text outside of composition.
 */
@Composable
fun relativeDateFormatter(): (LocalDate) -> String {
    val context = LocalContext.current
    val locale = LocalLocale.current

    val preferences = remember { context.appGraph.uiPreferences }
    val relativeTime = remember { preferences.relativeTime.get() }
    val dateFormat = remember { UiPreferences.dateFormat(preferences.dateFormat.get(), locale.platformLocale) }

    return remember(context, relativeTime, dateFormat) {
        { date -> date.toRelativeString(context, relativeTime, dateFormat) }
    }
}
// KUN <--
