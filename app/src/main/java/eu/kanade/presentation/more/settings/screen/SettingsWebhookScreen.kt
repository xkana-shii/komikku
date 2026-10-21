package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.connections.service.WebhookEvent
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import mihon.app.di.appGraph
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KNS -->
/** Webhook delivery settings: endpoints, a test, the events that are sent and excluded categories. */
object SettingsWebhookScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsWebhookScreen

    @Composable
    override fun getTitleRes() = KMR.strings.webhooks

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val graph = remember { context.appGraph }
        val preferences = remember { graph.webhookPreferences }
        val categories by remember { graph.getCategories.subscribe() }.collectAsState(emptyList())
        val scope = rememberCoroutineScope()
        var testing by remember { mutableStateOf(false) }

        fun sendTest() {
            scope.launchIO {
                try {
                    graph.webhookNotifier.sendTest()
                    withUIContext { context.toast(KMR.strings.webhook_test_sent) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withUIContext { context.toast(with(context) { e.formattedMessage }) }
                } finally {
                    withUIContext { testing = false }
                }
            }
        }

        val connection = listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.enabled,
                title = stringResource(KMR.strings.pref_webhook_enabled),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = preferences.discordUrl,
                title = stringResource(KMR.strings.webhook_discord_url),
                subtitle = null,
                onValueChanged = { it.isBlank() || it.toHttpUrlOrNull() != null },
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = preferences.genericUrl,
                title = stringResource(KMR.strings.webhook_generic_url),
                subtitle = null,
                onValueChanged = { it.isBlank() || it.toHttpUrlOrNull() != null },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(KMR.strings.webhook_send_test),
                onClick = {
                    testing = true
                    sendTest()
                }.takeUnless { testing },
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.webhooks_privacy)),
        )
        val events = WebhookEvent.entries.filter { it != WebhookEvent.TEST }.map { event ->
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.event(event),
                title = stringResource(event.titleRes()),
            )
        }
        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.webhook_connection),
                preferenceItems = connection,
            ),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.webhook_events),
                preferenceItems = events,
            ),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.webhook_excluded_categories),
                preferenceItems = listOf(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        preference = preferences.excludedCategories,
                        title = stringResource(KMR.strings.webhook_excluded_categories),
                        entries = categories.filterNot { it.isSystemCategory }
                            .sortedBy { it.order }
                            .associate { it.id.toString() to it.name },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.webhook_exclusions_help)),
                ),
            ),
        )
    }

    private fun WebhookEvent.titleRes(): StringResource = when (this) {
        WebhookEvent.CHAPTER_STARTED -> KMR.strings.webhook_chapter_started
        WebhookEvent.CHAPTER_READ -> KMR.strings.webhook_chapter_read
        WebhookEvent.NEW_MANGA_STARTED -> KMR.strings.webhook_new_manga_started
        WebhookEvent.MANGA_FINISHED -> KMR.strings.webhook_manga_finished
        WebhookEvent.MANGA_CAUGHT_UP -> KMR.strings.webhook_manga_caught_up
        WebhookEvent.MANGA_ADDED -> KMR.strings.webhook_manga_added
        WebhookEvent.MANGA_REMOVED -> KMR.strings.webhook_manga_removed
        WebhookEvent.LIBRARY_UPDATE -> KMR.strings.webhook_library_update
        WebhookEvent.DOWNLOADS_FINISHED -> KMR.strings.webhook_downloads_finished
        WebhookEvent.BACKUP_CREATED -> KMR.strings.webhook_backup_created
        WebhookEvent.BACKUP_RESTORED -> KMR.strings.webhook_backup_restored
        WebhookEvent.TEST -> KMR.strings.webhook_send_test
    }
}
// KNS <--
