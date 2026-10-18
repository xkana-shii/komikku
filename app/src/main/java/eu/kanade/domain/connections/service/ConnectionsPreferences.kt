// AM (CONNECTIONS) -->
package eu.kanade.domain.connections.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.connections.ConnectionsService
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class ConnectionsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun connectionsUsername(sync: ConnectionsService) = preferenceStore.getString(
        connectionsUsername(sync.id),
        "",
    )

    fun connectionsPassword(sync: ConnectionsService) = preferenceStore.getString(
        connectionsPassword(sync.id),
        "",
    )

    fun setConnectionsCredentials(sync: ConnectionsService, username: String, password: String) {
        connectionsUsername(sync).set(username)
        connectionsPassword(sync).set(password)
    }

    fun connectionsToken(sync: ConnectionsService) = preferenceStore.getString(
        Preference.privateKey(connectionsToken(sync.id)),
        "",
    )

    val enableDiscordRPC = preferenceStore.getBoolean("pref_enable_discord_rpc", false)

    val discordRPCStatus = preferenceStore.getInt("pref_discord_rpc_status", 1)

    val discordRPCIncognito = preferenceStore.getBoolean("pref_discord_rpc_incognito", false)

    val discordRPCIncognitoCategories = preferenceStore.getStringSet(
        "discord_rpc_incognito_categories",
        emptySet(),
    )

    val useChapterTitles = preferenceStore.getBoolean("pref_discord_rpc_use_chapter_titles", false)

    val discordCustomMessage = preferenceStore.getString("pref_discord_custom_message", "")

    val discordShowProgress = preferenceStore.getBoolean("pref_discord_show_progress", true)

    val discordShowTimestamp = preferenceStore.getBoolean("pref_discord_show_timestamp", true)

    val discordShowButtons = preferenceStore.getBoolean("pref_discord_show_buttons", true)

    val discordShowDownloadButton = preferenceStore.getBoolean("pref_discord_show_download_button", true)

    val discordShowDiscordButton = preferenceStore.getBoolean("pref_discord_show_discord_button", true)

    val discordAccounts = preferenceStore.getString("discord_accounts", "")

    companion object {

        fun connectionsUsername(syncId: Long) = "pref_connections_username_$syncId"

        private fun connectionsPassword(syncId: Long) = "pref_connections_password_$syncId"

        private fun connectionsToken(syncId: Long) = "connection_token_$syncId"
    }
}
// <-- AM (CONNECTIONS)
