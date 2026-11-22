// KUN -->
package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Drops the retired history tombstone queue once, on upgrade.
 *
 * `__APP_STATE_sync_pending_deleted_history_keys` held one `history` tombstone key per removed
 * entry, kept until the server acknowledged it. The queue lives on the rows now
 * (`history.pending_deletion`), so the set is dead: nothing reads it, and it would otherwise sit in
 * the preference store forever — a "clear all history" on a build that predates r10711 could have
 * left thousands of keys there. The removals it described are not lost, because a removal no server
 * acknowledged is not reconstructible from the set alone (it holds keys, not rows): the next local
 * read or removal re-establishes the state.
 *
 * Numbered `87f`: [CategoryPreferenceRepairMigration] took `86f`, and `Migrator` runs only the
 * versions in `(applied + 1)..target`, so a version an install already applied would never run.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class DeleteRetiredHistoryQueueMigration(
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version: Float = 87f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        // Guarded on `isSet`: a fresh install never wrote the key, and a store that already dropped
        // it is left alone, so the migration is safe to run twice.
        val retired = preferenceStore.getStringSet(
            Preference.appStateKey("sync_pending_deleted_history_keys"),
            emptySet(),
        )
        if (retired.isSet()) retired.delete()

        return@withContext true
    }
}
// KUN <--
