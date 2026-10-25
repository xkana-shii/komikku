package tachiyomi.data.debug

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.Database
import tachiyomi.domain.debug.repository.DebugRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DebugRepositoryImpl(
    private val database: Database,
) : DebugRepository {

    override suspend fun addAllMangaToLibrary() {
        database.ehQueries.addAllMangaInDatabaseToLibrary()
    }

    override suspend fun fixReaderViewerBackupBug() {
        database.ehQueries.fixReaderViewerBackupBug()
    }

    override suspend fun resetReaderViewerForAllManga() {
        database.ehQueries.resetReaderViewerForAllManga()
    }

    override suspend fun migrateAllNhentaiToOtherLang(newSourceId: Long, oldSourceIds: List<Long>) {
        database.ehQueries.migrateAllNhentaiToOtherLang(newSourceId, oldSourceIds)
    }
}
