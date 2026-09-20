package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.manga.components.MarkdownRender
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.more.NewUpdateScreenModel
import eu.kanade.tachiyomi.ui.more.NewUpdateScreenModel.Stage
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.InfoScreen

@Composable
fun NewUpdateScreen(
    versionName: String,
    changelogInfo: String,
    onOpenInBrowser: () -> Unit,
    onRejectUpdate: () -> Unit,
    onAcceptUpdate: () -> Unit,
    downloadState: NewUpdateScreenModel.State = NewUpdateScreenModel.State(),
) {
    InfoScreen(
        icon = Icons.Outlined.NewReleases,
        headingText = stringResource(MR.strings.update_check_notification_update_available),
        subtitleText = stringResource(SYMR.strings.latest_, versionName),
        acceptText = stringResource(
            when (downloadState.stage) {
                Stage.Available -> MR.strings.update_check_confirm
                Stage.Downloading -> KMR.strings.update_downloading
                Stage.Downloaded -> KMR.strings.update_install
                Stage.Failed -> MR.strings.action_retry
            },
        ),
        canAccept = downloadState.stage != Stage.Downloading,
        onAcceptClick = onAcceptUpdate,
        rejectText = stringResource(MR.strings.action_not_now),
        onRejectClick = onRejectUpdate,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.large),
        ) {
            if (downloadState.stage == Stage.Downloading) {
                LinearProgressIndicator(progress = { downloadState.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(KMR.strings.update_progress, downloadState.progress))
            }
            downloadState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            MarkdownRender(
                content = changelogInfo.trimIndent(),
                flavour = GFMFlavourDescriptor(),
            )

            TextButton(
                onClick = onOpenInBrowser,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                Text(text = stringResource(MR.strings.update_check_open))
                Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NewUpdateScreenPreview() {
    TachiyomiPreviewTheme {
        NewUpdateScreen(
            versionName = "v0.99.9",
            changelogInfo = """
                ## Yay
                Foobar

                ### More info
                - Hello
                - World
            """.trimIndent(),
            onOpenInBrowser = {},
            onRejectUpdate = {},
            onAcceptUpdate = {},
        )
    }
}
