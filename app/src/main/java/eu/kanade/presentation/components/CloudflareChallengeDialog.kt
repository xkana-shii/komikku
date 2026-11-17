package eu.kanade.presentation.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
fun CloudflareChallengeDialog() {
    val session by CloudflareChallengeCoordinator.session.collectAsState()
    val current = session ?: return
    var expanded by rememberSaveable(current) { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    Dialog(
        onDismissRequest = { current.cancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = if (expanded) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .padding(horizontal = 16.dp, vertical = if (landscape) 8.dp else 24.dp)
                    .fillMaxWidth()
            },
            shape = if (expanded) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .then(if (expanded) Modifier.statusBarsPadding() else Modifier),
            ) {
                Text(
                    text = stringResource(KMR.strings.cloudflare_verification_title),
                    style = MaterialTheme.typography.headlineSmall,
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
                    modifier = if (expanded) {
                        Modifier.fillMaxWidth().weight(1f)
                    } else {
                        Modifier.fillMaxWidth().heightIn(min = 200.dp, max = if (landscape) 220.dp else 420.dp)
                    },
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
// KNS <--
