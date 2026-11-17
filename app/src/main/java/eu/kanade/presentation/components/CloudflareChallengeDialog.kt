package eu.kanade.presentation.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeCoordinator
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
/**
 * Shows the Cloudflare human verification as an interactive WebView when a request hit an interactive challenge
 * while the app is in the foreground. Closing it (cancel or success) is driven by the coordinator's session.
 */
@Composable
fun CloudflareChallengeDialog(hostToken: Any) {
    // Only the screen that owns the foreground shows it, so an activity switch moves the dialog instead of doubling it
    val activeHost by CloudflareChallengeCoordinator.activeHost.collectAsState()
    val session by CloudflareChallengeCoordinator.session.collectAsState()
    val current = (session as? CloudflareChallengeCoordinator.Session)?.takeIf { activeHost === hostToken } ?: return
    var expanded by rememberSaveable(current) { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    // A new session gets a fresh dialog and WebView host
    key(current) {
        Dialog(
            onDismissRequest = { current.cancel() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Surface(
                modifier = if (expanded) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp, vertical = if (landscape) 8.dp else 24.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(if (landscape) 1f else 0.75f)
                },
                shape = if (expanded) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .then(if (expanded) Modifier.safeDrawingPadding() else Modifier),
                ) {
                    Text(
                        text = stringResource(KMR.strings.cloudflare_verification_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(KMR.strings.cloudflare_verification_subtitle, current.host),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    AndroidView(
                        factory = {
                            (current.webView.parent as? ViewGroup)?.removeView(current.webView)
                            current.webView.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }
                        },
                        onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { current.cancel() }) {
                            Text(text = stringResource(MR.strings.action_cancel))
                        }
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(
                                text = stringResource(
                                    if (expanded) KMR.strings.action_collapse else KMR.strings.action_expand,
                                ),
                            )
                        }
                        TextButton(onClick = { current.retry() }) {
                            Text(text = stringResource(MR.strings.action_retry))
                        }
                    }
                }
            }
        }
    }
}
// KNS <--
