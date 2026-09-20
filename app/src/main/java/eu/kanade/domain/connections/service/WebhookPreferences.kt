package eu.kanade.domain.connections.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

// KMK --> URLs may contain secrets; use the existing private-settings backup policy.
class WebhookPreferences(private val store: PreferenceStore) {
    fun enabled() = store.getBoolean("pref_webhook_enabled", false)
    fun discordUrl() = store.getString(Preference.privateKey("pref_webhook_discord_url"), "")
    fun genericUrl() = store.getString(Preference.privateKey("pref_webhook_generic_url"), "")
    fun event(event: WebhookEvent) = store.getBoolean("pref_webhook_${event.id}", false)
    fun excludedCategories() = store.getStringSet("pref_webhook_excluded_categories", emptySet())
}

enum class WebhookEvent(val id: String) {
    CHAPTER_STARTED("chapter_started"),
    CHAPTER_READ("chapter_read"),
    NEW_MANGA_STARTED("new_manga_started"),
    MANGA_FINISHED("manga_finished"),
    MANGA_ADDED("manga_added"),
    MANGA_REMOVED("manga_removed"),
    LIBRARY_UPDATE("library_update"),
    DOWNLOADS_FINISHED("downloads_finished"),
    BACKUP_CREATED("backup_created"),
    BACKUP_RESTORED("backup_restored"),
    MANGA_MIGRATED("manga_migrated"),
    APP_UPDATED("app_updated"),
    MANGA_CAUGHT_UP("manga_caught_up"),
    TEST("test"),
}
// KMK <--
