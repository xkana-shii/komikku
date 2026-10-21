package eu.kanade.domain.connections.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

// KNS -->
/**
 * Webhook settings, with the previous personal fork's keys. The URLs may contain secrets, so they are
 * private preferences (left out of backups like other private settings).
 */
@SingleIn(AppScope::class)
@Inject
class WebhookPreferences(private val preferenceStore: PreferenceStore) {
    val enabled = preferenceStore.getBoolean("pref_webhook_enabled", false)

    val discordUrl = preferenceStore.getString(Preference.privateKey("pref_webhook_discord_url"), "")

    val genericUrl = preferenceStore.getString(Preference.privateKey("pref_webhook_generic_url"), "")

    fun event(event: WebhookEvent) = preferenceStore.getBoolean("pref_webhook_${event.id}", false)

    /** Category ids (as strings, "0" = default) whose manga never trigger webhooks. */
    val excludedCategories = preferenceStore.getStringSet("pref_webhook_excluded_categories", emptySet())
}

enum class WebhookEvent(val id: String) {
    CHAPTER_STARTED("chapter_started"),
    CHAPTER_READ("chapter_read"),
    NEW_MANGA_STARTED("new_manga_started"),
    MANGA_FINISHED("manga_finished"),
    MANGA_CAUGHT_UP("manga_caught_up"),
    MANGA_ADDED("manga_added"),
    MANGA_REMOVED("manga_removed"),
    LIBRARY_UPDATE("library_update"),
    DOWNLOADS_FINISHED("downloads_finished"),
    BACKUP_CREATED("backup_created"),
    BACKUP_RESTORED("backup_restored"),
    TEST("test"),
}
// KNS <--
