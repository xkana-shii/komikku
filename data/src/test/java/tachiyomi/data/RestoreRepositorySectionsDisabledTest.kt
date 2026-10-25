// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.track.model.Track
import kotlin.time.Instant

/**
 * The section flags are a hard gate: a disabled tracking/history/custom-info section must not read
 * or write anything, so it cannot change rows, the manga version or its timestamps. History stays
 * independent of tracking and library state because the user keeps it that way.
 */
class RestoreRepositorySectionsDisabledTest {

    @Test
    fun `tracking off leaves tracks untouched even for a winning payload`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val trackRepository = TrackRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository, trackRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)
                driver.insertTrack(id = 1L, trackerId = 10L, title = "Local", lastChapterRead = 50.0)
                driver.insertTrack(id = 2L, trackerId = 20L, title = "Keep", lastChapterRead = 5.0)
                driver.resetMangaSyncState(version = 5L, lastModifiedAt = 10L)

                val before = trackRepository.getTracksByMangaId(1L)

                // A changed track, a new tracker and a missing tracker: nothing may be written
                // while the section is disabled.
                restore(
                    repository = repository,
                    isSync = true,
                    restoreTracking = false,
                    tracks = listOf(
                        track(trackerId = 10L, title = "Remote", lastChapterRead = 1.0, score = 0.0),
                        track(trackerId = 30L, title = "New", lastChapterRead = 1.0),
                    ),
                )
                // ... and an empty payload is still a no-op.
                restore(repository = repository, isSync = true, restoreTracking = false, tracks = emptyList())

                trackRepository.getTracksByMangaId(1L) shouldBe before
                mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1L)!!.version shouldBe 5L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `history off leaves history untouched and never writes a tombstone`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val chapterRepository = ChapterRepositoryImpl(database)
                val repository = restoreRepository(
                    database,
                    mangaRepository,
                    TrackRepositoryImpl(database),
                    chapterRepository,
                )
                driver.insertManga(version = 5L, lastModifiedAt = 10L)
                val chapters = chapterRepository.addAll(listOf(chapter("/c1"), chapter("/c2")))
                driver.insertHistory(chapters[0].id, readAt = 5_000L, readDuration = 100L)
                // A removed entry: read_at is NULL, its duration is preserved.
                driver.insertHistory(chapters[1].id, readAt = null, readDuration = 50L)

                restore(
                    repository = repository,
                    isSync = true,
                    restoreHistory = false,
                    chapters = listOf(chapter("/c1"), chapter("/c2")),
                    history = listOf(RestoredHistory("/c1", Instant.fromEpochMilliseconds(9_000), 200L)),
                    removeMissingChaptersBefore = 1_000L,
                )
                // An empty payload must not touch history either while the section is disabled.
                restore(
                    repository = repository,
                    isSync = true,
                    restoreHistory = false,
                    chapters = listOf(chapter("/c1"), chapter("/c2")),
                    history = emptyList(),
                    removeMissingChaptersBefore = 1_000L,
                )

                val history = database.historyQueries.getHistoryByMangaId(1L).awaitAsList()
                    .associateBy { it.chapter_id }
                history.getValue(chapters[0].id).read_at shouldBe Instant.fromEpochMilliseconds(5_000)
                history.getValue(chapters[0].id).read_duration shouldBe 100L
                history.getValue(chapters[1].id).read_at shouldBe null
                history.getValue(chapters[1].id).read_duration shouldBe 50L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `custom info off never applies the incoming overrides`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                val repository = RestoreRepositoryImpl(
                    database = database,
                    mangaRepository = mangaRepository,
                    chapterRepository = ChapterRepositoryImpl(database),
                    trackRepository = TrackRepositoryImpl(database),
                    mangaMetadataRepository = mockk(relaxed = true),
                    customMangaRepository = customMangaRepository,
                )
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    restoreCustomInfo = false,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = "Custom"),
                )

                verify(exactly = 0) { customMangaRepository.set(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a manual restore with every section disabled is a no-op`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val chapterRepository = ChapterRepositoryImpl(database)
                val trackRepository = TrackRepositoryImpl(database)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                val repository = RestoreRepositoryImpl(
                    database = database,
                    mangaRepository = mangaRepository,
                    chapterRepository = chapterRepository,
                    trackRepository = trackRepository,
                    mangaMetadataRepository = mockk(relaxed = true),
                    customMangaRepository = customMangaRepository,
                )
                driver.insertManga(version = 5L, lastModifiedAt = 10L)
                val chapters = chapterRepository.addAll(listOf(chapter("/c1"), chapter("/c2")))
                driver.insertTrack(id = 1L, trackerId = 10L, title = "Local", lastChapterRead = 50.0)
                driver.insertHistory(chapters[0].id, readAt = 5_000L, readDuration = 100L)
                driver.resetMangaSyncState(version = 5L, lastModifiedAt = 10L)

                val tracksBefore = trackRepository.getTracksByMangaId(1L)

                restore(
                    repository = repository,
                    isSync = false,
                    restoreTracking = false,
                    restoreHistory = false,
                    restoreCustomInfo = false,
                    chapters = listOf(chapter("/c1"), chapter("/c2")),
                    tracks = listOf(
                        track(trackerId = 10L, title = "Backup", lastChapterRead = 1.0),
                        track(trackerId = 30L, title = "New", lastChapterRead = 1.0),
                    ),
                    history = listOf(RestoredHistory("/c1", Instant.fromEpochMilliseconds(9_000), 200L)),
                    removeMissingChaptersBefore = 1_000L,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = "Custom"),
                )

                trackRepository.getTracksByMangaId(1L) shouldBe tracksBefore
                val history = database.historyQueries.getHistoryByMangaId(1L).awaitAsList()
                history.single().chapter_id shouldBe chapters[0].id
                history.single().read_at shouldBe Instant.fromEpochMilliseconds(5_000)
                history.single().read_duration shouldBe 100L
                verify(exactly = 0) { customMangaRepository.set(any()) }
                mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1L)!!.version shouldBe 5L
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun restore(
        repository: RestoreRepositoryImpl,
        isSync: Boolean,
        restoreTracking: Boolean = true,
        restoreHistory: Boolean = true,
        restoreCustomInfo: Boolean = true,
        chapters: List<Chapter> = emptyList(),
        tracks: List<Track> = emptyList(),
        history: List<RestoredHistory> = emptyList(),
        removeMissingChaptersBefore: Long? = null,
        customMangaInfo: CustomMangaInfo? = null,
    ) {
        repository.restoreManga(
            entries = listOf(
                RestoredManga(
                    manga = manga(),
                    chapters = chapters,
                    categoryIds = null,
                    history = history,
                    tracks = tracks,
                    excludedScanlators = emptyList(),
                    customMangaInfo = customMangaInfo,
                ),
            ),
            isSync = isSync,
            restoreCategories = false,
            restoreTracking = restoreTracking,
            restoreHistory = restoreHistory,
            restoreCustomInfo = restoreCustomInfo,
            removeMissingChaptersBefore = removeMissingChaptersBefore,
            update = { it.toMangaUpdate() },
        )
    }

    private fun restoreRepository(
        database: Database,
        mangaRepository: MangaRepositoryImpl,
        trackRepository: TrackRepositoryImpl,
        chapterRepository: ChapterRepositoryImpl = ChapterRepositoryImpl(database),
    ) = RestoreRepositoryImpl(
        database = database,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        trackRepository = trackRepository,
        mangaMetadataRepository = mockk(relaxed = true),
        customMangaRepository = mockk(relaxed = true),
    )

    private fun manga(version: Long = 5L, lastModifiedAt: Long = 10L) = Manga.create().copy(
        id = 1L,
        source = 1L,
        favorite = true,
        dateAdded = Instant.fromEpochMilliseconds(1_000),
        url = "/manga/1",
        ogTitle = "Title",
        ogArtist = null,
        ogAuthor = null,
        ogThumbnailUrl = null,
        ogDescription = null,
        ogGenre = null,
        ogStatus = 0L,
        updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        initialized = true,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = 500L,
        version = version,
        notes = "",
        memo = JsonObject(emptyMap()),
    )

    private fun chapter(url: String) = Chapter.create().copy(
        mangaId = 1L,
        url = url,
        name = url,
        dateFetch = Instant.fromEpochMilliseconds(1_000),
        version = 1L,
        lastModifiedAt = 10L,
    )

    private fun track(
        trackerId: Long,
        title: String = "Track",
        lastChapterRead: Double = 0.0,
        score: Double = 0.0,
        libraryId: Long? = null,
    ) = Track(
        id = -1L,
        mangaId = 1L,
        trackerId = trackerId,
        remoteId = trackerId * 10,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = 10,
        status = 1,
        score = score,
        remoteUrl = "/track/$trackerId",
        startDate = null,
        finishDate = null,
        private = false,
    )

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.insertManga(version: Long, lastModifiedAt: Long) {
        execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                remote_memo, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized, remote_cover,
                state_cover_last_modified, last_modified_at, version, is_syncing,
                user_favorite_at, favorite_modified_at
            ) VALUES (
                1, 1, '/manga/1', 'Title', 0, 0,
                '{}', '', 0, 0,
                0, 1, 'https://example.com/cover.jpg',
                123, $lastModifiedAt, $version, 0,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    // Track writes bump the manga version through the user-edit triggers, so reset the sync state
    // after seeding the rows the test wants to observe.
    private suspend fun SqlDriver.resetMangaSyncState(version: Long, lastModifiedAt: Long) {
        execute(
            null,
            "UPDATE manga SET version = $version, last_modified_at = $lastModifiedAt WHERE id = 1",
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertTrack(
        id: Long,
        trackerId: Long,
        title: String,
        lastChapterRead: Double,
    ) {
        execute(
            null,
            """
            INSERT INTO manga_track(
                id, manga_id, tracker_id, remote_id, remote_url, library_id, title,
                last_chapter_read, total_chapters, status, score, start_date, finish_date, private
            ) VALUES (
                $id, 1, $trackerId, ${trackerId * 10}, '/track/$trackerId', NULL, '$title',
                $lastChapterRead, 10, 1, 0.0, NULL, NULL, 0
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertHistory(chapterId: Long, readAt: Long?, readDuration: Long) {
        execute(
            null,
            """
            INSERT INTO history(manga_id, chapter_id, read_at, read_duration)
            VALUES (1, $chapterId, ${readAt ?: "NULL"}, $readDuration)
            """.trimIndent(),
            0,
        ).await()
    }
}
// KUN <--
