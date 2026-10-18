package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import exh.source.ExhPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

@Inject
@ContributesIntoSet(AppScope::class)
class IntegratedHentaiMigration : Migration {
    override val version: Float = 71f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val isHentaiEnabled = migrationContext.get<ExhPreferences>()?.isHentaiEnabled
            ?: return@withIOContext false
        if (!isHentaiEnabled.isSet() || isHentaiEnabled.get()) {
            isHentaiEnabled.set(true)
        }
        return@withIOContext true
    }
}
