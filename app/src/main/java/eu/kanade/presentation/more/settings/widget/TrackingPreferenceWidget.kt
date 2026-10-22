package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.LocalPreferenceHighlighted
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.roundedfilled.Star
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TrackingPreferenceWidget(
    modifier: Modifier = Modifier,
    tracker: Tracker,
    isLoggedIn: Boolean,
    isRefreshing: Boolean,
    // KNS --> Keep main's preferred tracker affordance with the current icon set.
    isPriority: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    // KNS <--
    onClick: (() -> Unit)? = null,
) {
    val highlighted = LocalPreferenceHighlighted.current
    val scope = rememberCoroutineScope { Dispatchers.IO }
    Box(modifier = Modifier.highlightBackground(highlighted)) {
        Row(
            modifier = modifier
                // KNS --> A long press changes only the preferred tracker; normal taps keep login/logout behavior.
                .combinedClickable(
                    enabled = onClick != null || onLongClick != null,
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                )
                // KNS <--
                .fillMaxWidth()
                .padding(horizontal = PrefsHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackLogoIcon(tracker)
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = tracker.name,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = TitleFontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    val displayName = tracker.getDisplayUsername()
                    if (isLoggedIn && displayName.isNotBlank()) {
                        Text(
                            text = displayName,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // KNS --> The star mirrors main's placement while using the newer Material Symbols icon.
            if (isPriority) {
                Icon(
                    imageVector = MaterialSymbols.RoundedFilled.Star,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp),
                    tint = Color(0xFFFFC107),
                    contentDescription = stringResource(KMR.strings.pref_priority_tracker),
                )
            }
            // KNS <--
            if (isLoggedIn) {
                if (tracker !is EnhancedTracker) {
                    IconButton(
                        enabled = !isRefreshing,
                        onClick = {
                            scope.launch { tracker.refreshUser() }
                        },
                    ) {
                        if (!isRefreshing) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Refresh,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(32.dp),
                                contentDescription = stringResource(MR.strings.refresh_tracker_profile),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = MaterialSymbols.Rounded.Check,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp),
                    tint = Color(0xFF4CAF50),
                    contentDescription = stringResource(MR.strings.login_success),
                )
            }
        }
    }
}
