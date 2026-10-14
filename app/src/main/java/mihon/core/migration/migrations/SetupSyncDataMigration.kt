package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

@Inject
@ContributesIntoSet(AppScope::class)
class SetupSyncDataMigration(
    private val context: Context,
    // KUN -->
    private val syncPreferences: SyncPreferences,
    // KUN <--
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        // KMK -->
        val syncEnabled = syncPreferences.isSyncEnabled()
        if (syncEnabled) {
            // KMK <--
            SyncDataJob.setupTask(context)
        }
        return true
    }
}
