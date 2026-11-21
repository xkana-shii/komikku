// AM (CONNECTIONS) -->
package eu.kanade.domain.connections.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

// KUN -->
// komi-kun moved this class from `:app` to `:domain`, unchanged except for the credentials helpers
// below: `domain`'s `CategoryIdPreferences` inventory has to hold `discordRPCIncognitoCategories`, so
// the pruner and the repair migration can cover it in the same layer as the other category-id
// preferences. `:domain` cannot reference the app's `ConnectionsService`, so the helpers take the
// narrow [ConnectionsServiceId] it implements.
// KUN <--
@Inject
@SingleIn(AppScope::class)
class ConnectionsPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // KUN -->
    fun connectionsUsername(sync: ConnectionsServiceId) = preferenceStore.getString(
        connectionsUsername(sync.id),
        "",
    )

    fun connectionsPassword(sync: ConnectionsServiceId) = preferenceStore.getString(
        connectionsPassword(sync.id),
        "",
    )

    fun setConnectionsCredentials(sync: ConnectionsServiceId, username: String, password: String) {
        connectionsUsername(sync).set(username)
        connectionsPassword(sync).set(password)
    }

    fun connectionsToken(sync: ConnectionsServiceId) = preferenceStore.getString(
        Preference.privateKey(connectionsToken(sync.id)),
        "",
    )
    // KUN <--

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

    // KNS --> the accounts hold Discord tokens, so they are private (never in backups)
    val discordAccounts = preferenceStore.getString(Preference.privateKey(DISCORD_ACCOUNTS_KEY), "")
    // KNS <--

    companion object {

        fun connectionsUsername(syncId: Long) = "pref_connections_username_$syncId"

        // KNS --> private, like tracker passwords
        private fun connectionsPassword(syncId: Long) = Preference.privateKey(connectionsPasswordKey(syncId))

        /** Before KNS: the public keys these were stored under; [PrivateConnectionsMigration] moves them. */
        fun connectionsPasswordKey(syncId: Long) = "pref_connections_password_$syncId"

        const val DISCORD_ACCOUNTS_KEY = "discord_accounts"
        // KNS <--

        private fun connectionsToken(syncId: Long) = "connection_token_$syncId"
    }
}
// <-- AM (CONNECTIONS)
