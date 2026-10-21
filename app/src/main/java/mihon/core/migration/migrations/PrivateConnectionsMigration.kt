package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.connections.service.ConnectionsPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

// KNS -->
/**
 * Discord accounts (which contain their tokens) and connection passwords used to be stored under
 * ordinary preference keys, so backups exported them. They now use private keys; this moves existing
 * values over once (keeping a newer private value) and removes the public copies. It also cleans up public
 * copies a restored older backup brings back, which is why it runs on every start; it only acts while
 * such keys exist.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class PrivateConnectionsMigration(
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val keys = preferenceStore.getAll().keys.filter { key ->
            key == ConnectionsPreferences.DISCORD_ACCOUNTS_KEY || key.startsWith(PASSWORD_PREFIX)
        }
        keys.forEach { key ->
            val public = preferenceStore.getString(key, "")
            val private = preferenceStore.getString(Preference.privateKey(key), "")
            if (private.get().isBlank()) private.set(public.get())
            public.delete()
        }
        return true
    }

    private companion object {
        val PASSWORD_PREFIX = ConnectionsPreferences.connectionsPasswordKey(0).removeSuffix("0")
    }
}
// KNS <--
