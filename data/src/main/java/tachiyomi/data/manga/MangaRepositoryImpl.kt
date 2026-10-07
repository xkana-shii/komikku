package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.transform
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.repository.MangaRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.EmptyCoroutineContext

// KMK -->

class MangaRepositoryImpl(
    private val database: Database,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return database.mangasQueries.getMangaById(id, MangaMapper::mapManga).awaitAsOne()
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return database.mangasQueries.getMangaById(id, MangaMapper::mapManga).subscribeToOne()
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return database.mangasQueries.getMangaByUrlAndSource(
            url,
            sourceId,
            MangaMapper::mapManga,
        ).awaitAsOneOrNull()
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return database.mangasQueries.getMangaByUrlAndSource(
            url,
            sourceId,
            MangaMapper::mapManga,
        ).subscribeToOneOrNull()
    }

    override suspend fun getFavorites(): List<Manga> {
        return database.mangasQueries.getFavorites(MangaMapper::mapManga).awaitAsList()
    }

    override suspend fun getReadMangaNotInLibrary(): List<Manga> {
        return database.mangasQueries.getReadMangaNotInLibrary(MangaMapper::mapManga).awaitAsList()
    }

    override suspend fun getLibraryManga(): List<LibraryManga> {
        // KMK -->
        refillChapterStats()
        // KMK <--
        return database.libraryViewQueries.library(MangaMapper::mapLibraryManga).awaitAsList()
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        // KMK -->
        return database.libraryViewQueries.library(MangaMapper::mapLibraryManga)
            .asFlow()
            .filter { !refillChapterStats() }
            .mapToList(EmptyCoroutineContext)
            // Throttles re-queries during write bursts: while this delay suspends the collector,
            // SQLDelight's conflated invalidation channel holds at most one pending re-query.
            .transform {
                emit(it)
                delay(LIBRARY_THROTTLE_MS)
            }
        // KMK <--
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return database.mangasQueries.getFavoriteBySourceId(sourceId, MangaMapper::mapManga).subscribeToList()
    }

    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<MangaWithChapterCount> {
        return database.mangasQueries.getDuplicateLibraryManga(id, title, MangaMapper::mapMangaWithChapterCount).awaitAsList()
    }

    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return database.mangasQueries.getUpcomingManga(epochMillis, statuses, MangaMapper::mapManga).subscribeToList()
    }

    override suspend fun resetViewerFlags(): Boolean {
        return try {
            database.mangasQueries.resetViewerFlags()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    // KMK -->
    override suspend fun updateLibraryChapterFlags(chapterFlags: Long): Boolean {
        return try {
            database.transaction {
                database.mangasQueries.updateLibraryChapterFlags(chapterFlags)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }
    // KMK <--

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        database.transaction {
            database.mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.map { categoryId ->
                database.mangas_categoriesQueries.insert(mangaId, categoryId)
            }
        }
    }

    override suspend fun update(update: MangaUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return try {
            partialUpdate(*mangaUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun insertNetworkManga(
        manga: List<Manga>,
        // KMK -->
        updateInfo: Boolean,
        // KMK <--
    ): List<Manga> {
        return database.transactionWithResult {
            manga.map {
                database.mangasQueries.insertNetworkManga(
                    source = it.source,
                    url = it.url,
                    // SY -->
                    title = it.ogTitle,
                    artist = it.ogArtist,
                    author = it.ogAuthor,
                    thumbnailUrl = it.ogThumbnailUrl,
                    description = it.ogDescription,
                    genre = it.ogGenre,
                    status = it.ogStatus,
                    // SY <--
                    favorite = it.favorite,
                    lastUpdate = it.lastUpdate,
                    nextUpdate = it.nextUpdate,
                    calculateInterval = it.fetchInterval.toLong(),
                    initialized = it.initialized,
                    viewerFlags = it.viewerFlags,
                    chapterFlags = it.chapterFlags,
                    coverLastModified = it.coverLastModified,
                    dateAdded = it.dateAdded,
                    updateStrategy = it.updateStrategy,
                    version = it.version,
                    memo = it.memo,
                    // SY -->
                    updateTitle = it.ogTitle.isNotBlank(),
                    updateCover = !it.ogThumbnailUrl.isNullOrBlank(),
                    // SY <--
                    updateDetails = it.initialized,
                    // KMK -->
                    updateInfo = updateInfo,
                    // KMK <--
                    mapper = MangaMapper::mapManga,
                )
                    .awaitAsOne()
            }
        }
    }

    private suspend fun partialUpdate(vararg mangaUpdates: MangaUpdate) {
        database.transaction {
            mangaUpdates.forEach { value ->
                database.mangasQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre,
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    mangaId = value.id,
                    updateStrategy = value.updateStrategy,
                    version = value.version,
                    isSyncing = 0,
                    notes = value.notes,
                    memo = value.memo,
                )
            }
        }
    }

    // SY -->
    override suspend fun getMangaBySourceId(sourceId: Long): List<Manga> {
        return database.mangasQueries.getBySource(sourceId, MangaMapper::mapManga).awaitAsList()
    }

    override suspend fun getAll(): List<Manga> {
        return database.mangasQueries.getAll(MangaMapper::mapManga).awaitAsList()
    }

    override suspend fun deleteManga(mangaId: Long) {
        database.mangasQueries.deleteById(mangaId)
    }

    override suspend fun getReadMangaNotInLibraryView(): List<LibraryManga> {
        return database.libraryViewQueries.readMangaNonLibrary(MangaMapper::mapLibraryManga).awaitAsList()
    }
    // SY <--

    // KMK -->
    /**
     * Restores the cached chapter aggregates that writes dropped, returning true if it wrote.
     * Failing only costs speed: the library query aggregates entries without a cached row live.
     */
    private suspend fun refillChapterStats(): Boolean {
        return try {
            database.manga_chapter_statsQueries.hasMissing().awaitAsOne() &&
                database.manga_chapter_statsQueries.refill() > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to refill manga_chapter_stats" }
            false
        }
    }

    companion object {
        private const val LIBRARY_THROTTLE_MS = 250L
    }
    // KMK <--
}
// KMK <--
