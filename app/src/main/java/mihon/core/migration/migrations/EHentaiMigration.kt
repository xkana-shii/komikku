package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import exh.source.EH_OLD_ID
import exh.source.EH_SOURCE_ID
import exh.source.EXH_OLD_ID
import exh.source.EXH_SOURCE_ID
import mihon.core.migration.MigrateUtils
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.repository.SourceMigrationRepository

@Inject
@ContributesIntoSet(AppScope::class)
class EHentaiMigration(
    private val sourceMigrationRepository: SourceMigrationRepository,
    private val sourcePreferences: SourcePreferences,
) : Migration {
    override val version: Float = 72f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        MigrateUtils.updateSourceId(sourceMigrationRepository, sourcePreferences, EH_SOURCE_ID, EH_OLD_ID)
        MigrateUtils.updateSourceId(sourceMigrationRepository, sourcePreferences, EXH_SOURCE_ID, EXH_OLD_ID)

        return@withIOContext true
    }
}
