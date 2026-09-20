package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.connections.service.WebhookEvent
import eu.kanade.domain.connections.service.WebhookPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.webhook.WebhookNotifier
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsWebhookScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsWebhookScreen

    @Composable
    override fun getTitleRes() = KMR.strings.webhooks

    @Composable
    override fun getPreferences(): List<Preference> {
        val preferences = remember { Injekt.get<WebhookPreferences>() }
        val categories by remember { Injekt.get<GetCategories>().subscribe() }.collectAsState(emptyList())
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var testing by remember { mutableStateOf(false) }
        val connections = listOf(
            Preference.PreferenceItem.SwitchPreference(preferences.enabled(), stringResource(KMR.strings.pref_webhook_enabled)),
            Preference.PreferenceItem.EditTextPreference(
                preference = preferences.discordUrl(),
                title = stringResource(KMR.strings.webhook_discord_url),
                subtitle = null,
                onValueChanged = { it.isBlank() || it.toHttpUrlOrNull() != null },
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = preferences.genericUrl(),
                title = stringResource(KMR.strings.webhook_generic_url),
                subtitle = null,
                onValueChanged = { it.isBlank() || it.toHttpUrlOrNull() != null },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(KMR.strings.webhook_send_test),
                onClick = if (testing) {
                    null
                } else {
                    {
                        testing = true
                        scope.launchIO {
                            try {
                                Injekt.get<WebhookNotifier>().sendTest()
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
                },
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.webhooks_privacy)),
        )
        val events = listOf(
            WebhookEvent.CHAPTER_STARTED, WebhookEvent.CHAPTER_READ, WebhookEvent.NEW_MANGA_STARTED,
            WebhookEvent.MANGA_FINISHED, WebhookEvent.LIBRARY_UPDATE, WebhookEvent.BACKUP_CREATED,
            WebhookEvent.MANGA_ADDED, WebhookEvent.MANGA_REMOVED, WebhookEvent.DOWNLOADS_FINISHED,
            WebhookEvent.BACKUP_RESTORED, WebhookEvent.MANGA_MIGRATED, WebhookEvent.APP_UPDATED, WebhookEvent.MANGA_CAUGHT_UP,
        ).map { event ->
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.event(event),
                title = stringResource(
                    when (event) {
                        WebhookEvent.CHAPTER_STARTED -> KMR.strings.webhook_chapter_started
                        WebhookEvent.CHAPTER_READ -> KMR.strings.webhook_chapter_read
                        WebhookEvent.NEW_MANGA_STARTED -> KMR.strings.webhook_new_manga_started
                        WebhookEvent.MANGA_FINISHED -> KMR.strings.webhook_manga_finished
                        WebhookEvent.MANGA_ADDED -> KMR.strings.webhook_manga_added
                        WebhookEvent.MANGA_REMOVED -> KMR.strings.webhook_manga_removed
                        WebhookEvent.LIBRARY_UPDATE -> KMR.strings.webhook_library_update
                        WebhookEvent.DOWNLOADS_FINISHED -> KMR.strings.webhook_downloads_finished
                        WebhookEvent.BACKUP_CREATED -> KMR.strings.webhook_backup_created
                        WebhookEvent.BACKUP_RESTORED -> KMR.strings.webhook_backup_restored
                        WebhookEvent.MANGA_MIGRATED -> KMR.strings.webhook_manga_migrated
                        WebhookEvent.APP_UPDATED -> KMR.strings.webhook_app_updated
                        WebhookEvent.MANGA_CAUGHT_UP -> KMR.strings.webhook_manga_caught_up
                        WebhookEvent.TEST -> KMR.strings.webhook_send_test
                    },
                ),
            )
        }
        return listOf(
            Preference.PreferenceGroup(title = stringResource(KMR.strings.pref_category_connections), preferenceItems = connections.toImmutableList()),
            Preference.PreferenceGroup(title = stringResource(KMR.strings.webhook_events), preferenceItems = events.toImmutableList()),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.webhook_excluded_categories),
                preferenceItems = listOf(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        preference = preferences.excludedCategories(),
                        title = stringResource(KMR.strings.webhook_excluded_categories),
                        entries = categories.filterNot { it.isSystemCategory }.sortedBy { it.order }.associate { it.id.toString() to it.name }.toImmutableMap(),
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.webhook_exclusions_help)),
                ).toImmutableList(),
            ),
        )
    }
}
