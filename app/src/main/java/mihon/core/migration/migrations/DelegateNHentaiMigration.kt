package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import exh.source.NHENTAI_OLD_ID
import exh.source.NHENTAI_SOURCE_ID
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.repository.SourceMigrationRepository

class DelegateNHentaiMigration(
    private val sourceMigrationRepository: SourceMigrationRepository,
    private val sourcePreferences: SourcePreferences?,
) : Migration {
    override val version: Float = 6f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        MigrateUtils.updateSourceId(sourceMigrationRepository, sourcePreferences, NHENTAI_SOURCE_ID, NHENTAI_OLD_ID)

        return@withIOContext true
    }
}
