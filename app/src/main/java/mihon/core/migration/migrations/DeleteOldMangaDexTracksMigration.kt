package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.track.repository.TrackRepository

class DeleteOldMangaDexTracksMigration : Migration {
    override val version: Float = 17f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val trackRepository = migrationContext.get<TrackRepository>() ?: return@withIOContext false
        // Delete old mangadex trackers
        trackRepository.deleteByTrackerId(6)
        return@withIOContext true
    }
}
