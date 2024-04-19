package mihon.core.migration.migrations

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class DiscordRPCStatusMigration : Migration {
    override val version: Float = 42f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val connectionsPreferences: ConnectionsPreferences by injectLazy()
        val connectionsManager: ConnectionsManager = Injekt.get()
        val context = migrationContext.get<App>() ?: return@withIOContext false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (connectionsPreferences.discordRPCStatus().isSet()) {
            prefs.edit {
                val oldString = try {
                    prefs.getString(connectionsPreferences.discordRPCStatus().key(), null)
                } catch (e: ClassCastException) {
                    null
                } ?: return@edit
                val newInt = when (oldString) {
                    "dnd" -> -1
                    "idle" -> 0
                    else -> 1
                }
                putInt(connectionsPreferences.discordRPCStatus().key(), newInt)
            }
        }

        if (connectionsPreferences.connectionsToken(connectionsManager.discord).get().isNotBlank()) {
            connectionsPreferences.setConnectionsCredentials(connectionsManager.discord, "Discord", "Logged In")
        }

        return@withIOContext true
    }
}
