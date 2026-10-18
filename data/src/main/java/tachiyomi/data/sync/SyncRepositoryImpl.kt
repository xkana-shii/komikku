package tachiyomi.data.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.Database
import tachiyomi.data.manga.MangaMapper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.sync.repository.SyncRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SyncRepositoryImpl(
    private val database: Database,
) : SyncRepository {

    override suspend fun resetSyncing() {
        database.transaction {
            database.mangaQueries.resetIsSyncing()
            database.chapterQueries.resetIsSyncing()
            database.categoryQueries.resetIsSyncing()
        }
    }

    override suspend fun getMangasWithFavoriteTimestamp(): List<Manga> {
        return database.mangaQueries
            .getMangasWithFavoriteTimestamp(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getAllManga(): List<Manga> {
        return database.mangaQueries
            .getAllManga(MangaMapper::mapManga)
            .awaitAsList()
    }

    override suspend fun getMangasModifiedSince(since: Long): List<Manga> {
        return database.mangaQueries
            .getMangasModifiedSince(since, MangaMapper::mapManga)
            .awaitAsList()
    }
}
