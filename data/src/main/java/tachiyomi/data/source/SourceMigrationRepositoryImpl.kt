package tachiyomi.data.source

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.Database
import tachiyomi.domain.source.repository.SourceMigrationRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SourceMigrationRepositoryImpl(
    private val database: Database,
) : SourceMigrationRepository {

    override suspend fun migrateSource(newId: Long, oldId: Long) {
        database.ehQueries.migrateSource(newId, oldId)
        // KUN -->
        database.ehQueries.migrateMergedSource(newId, oldId)
        // Migrate saved searches & feeds
        database.ehQueries.migrateSourceSavedSearch(newId, oldId)
        database.ehQueries.migrateSourceFeed(newId, oldId)
        // KUN <--
    }
}
