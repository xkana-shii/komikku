// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.track.model.Track
import kotlin.time.Instant

/**
 * Tracking restore semantics: every payload, winning or not, merges monotonically. Absence from the
 * payload never deletes a local track and an empty list is not delete-all, because the manga payload
 * is versioned per manga rather than per tracking section (a peer with tracking disabled can upload
 * an empty section). Deletion propagation needs protocol tombstones/per-tracker versions.
 */
class RestoreRepositoryTrackingTest {

    @Test
    fun `a winning sync payload merges monotonically and never clears local fields`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val trackRepository = TrackRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository, trackRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)
                driver.insertTrack(
                    trackerId = 10L,
                    title = "Local",
                    remoteId = 1L,
                    remoteUrl = "/local",
                    libraryId = 7L,
                    lastChapterRead = 50.0,
                    totalChapters = 50L,
                    status = 2L,
                    score = 8.0,
                    startDate = 1_000L,
                    finishDate = 2_000L,
                    private = true,
                )
                driver.resetMangaSyncState(version = 5L, lastModifiedAt = 10L)

                // Even a winning payload must not clear a local score, finish date, private flag,
                // library id or progress. Only non-blank/non-zero incoming values are merged in.
                restore(
                    repository,
                    isSync = true,
                    tracks = listOf(
                        track(
                            trackerId = 10L,
                            title = "Remote",
                            remoteId = 99L,
                            remoteUrl = "/remote",
                            libraryId = null,
                            lastChapterRead = 5.0,
                            totalChapters = 20L,
                            status = 1L,
                            score = 0.0,
                            startDate = null,
                            finishDate = null,
                            private = false,
                        ),
                    ),
                )

                val track = trackRepository.getTracksByMangaId(1L).single()
                track.title shouldBe "Remote"
                track.remoteId shouldBe 99L
                track.remoteUrl shouldBe "/remote"
                // null means "not in a tracker list" here, so it never clears a known id.
                track.libraryId shouldBe 7L
                track.lastChapterRead shouldBe 50.0
                track.totalChapters shouldBe 50L
                track.status shouldBe 2L
                track.score shouldBe 8.0
                track.startDate shouldBe Instant.fromEpochMilliseconds(1_000)
                track.finishDate shouldBe Instant.fromEpochMilliseconds(2_000)
                track.private shouldBe true

                // Restoring the child table must not look like a user edit to delta sync.
                val manga = mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1L)!!
                manga.version shouldBe 5L
                manga.lastModifiedAt shouldBe 10L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a winning sync payload inserts new trackers and preserves absent ones`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val trackRepository = TrackRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository, trackRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)
                driver.insertTrack(trackerId = 10L, title = "Keep")
                // Absent from the payload; a peer with tracking disabled may omit it.
                driver.insertTrack(trackerId = 20L, title = "Keep Local")
                driver.resetMangaSyncState(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository,
                    isSync = true,
                    tracks = listOf(
                        track(trackerId = 10L, title = "Keep"),
                        track(trackerId = 30L, title = "New"),
                    ),
                )

                trackRepository.getTracksByMangaId(1L)
                    .map { it.trackerId }
                    .shouldContainExactlyInAnyOrder(10L, 20L, 30L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an empty tracking list preserves local tracks`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val trackRepository = TrackRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository, trackRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)
                driver.insertTrack(trackerId = 10L, title = "A")
                driver.insertTrack(trackerId = 20L, title = "B")
                driver.resetMangaSyncState(version = 5L, lastModifiedAt = 10L)

                restore(repository, isSync = true, tracks = emptyList())

                trackRepository.getTracksByMangaId(1L)
                    .map { it.trackerId }
                    .shouldContainExactlyInAnyOrder(10L, 20L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a losing sync payload preserves the local track`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val trackRepository = TrackRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository, trackRepository)
                driver.insertManga(version = 6L, lastModifiedAt = 20L)
                driver.insertTrack(
                    trackerId = 10L,
                    title = "Local",
                    remoteId = 5L,
                    remoteUrl = "/local",
                    libraryId = 7L,
                    lastChapterRead = 50.0,
                    totalChapters = 50L,
                    status = 2L,
                    score = 8.0,
                    startDate = 1_000L,
                    finishDate = 2_000L,
                    private = true,
                )
                driver.insertTrack(trackerId = 20L, title = "Keep")
                driver.resetMangaSyncState(version = 6L, lastModifiedAt = 20L)

                restore(
                    repository,
                    isSync = true,
                    version = 5L,
                    lastModifiedAt = 10L,
                    tracks = listOf(
                        track(
                            trackerId = 10L,
                            title = "Remote",
                            remoteId = 99L,
                            remoteUrl = "/remote",
                            lastChapterRead = 5.0,
                            totalChapters = 20L,
                            status = 1L,
                            score = 0.0,
                            private = false,
                        ),
                    ),
                )

                val tracks = trackRepository.getTracksByMangaId(1L).associateBy { it.trackerId }
                // A losing payload may still merge fields, but can never delete the local tracker.
                tracks.keys shouldBe setOf(10L, 20L)
                val track = tracks.getValue(10L)
                track.title shouldBe "Remote"
                track.remoteId shouldBe 99L
                track.remoteUrl shouldBe "/remote"
                // A losing payload cannot clear a local library id, progress or score.
                track.libraryId shouldBe 7L
                track.lastChapterRead shouldBe 50.0
                track.totalChapters shouldBe 50L
                track.status shouldBe 2L
                track.score shouldBe 8.0
                track.finishDate shouldBe Instant.fromEpochMilliseconds(2_000)
                track.private shouldBe true
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a manual restore still merges monotonically`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val trackRepository = TrackRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository, trackRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)
                driver.insertTrack(
                    trackerId = 10L,
                    title = "Local",
                    libraryId = 7L,
                    lastChapterRead = 50.0,
                    totalChapters = 50L,
                    status = 2L,
                    score = 8.0,
                )
                driver.resetMangaSyncState(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository,
                    isSync = false,
                    tracks = listOf(
                        track(
                            trackerId = 10L,
                            title = "", // blank keeps the local title
                            libraryId = null,
                            lastChapterRead = 5.0, // lower progress loses
                            totalChapters = 20L,
                            status = 1L,
                            score = 0.0, // zero does not clear a local score
                        ),
                    ),
                )

                val track = trackRepository.getTracksByMangaId(1L).single()
                track.title shouldBe "Local"
                track.libraryId shouldBe 7L
                track.lastChapterRead shouldBe 50.0
                track.totalChapters shouldBe 50L
                track.status shouldBe 2L
                track.score shouldBe 8.0
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a legacy zero library id is stored as null`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val trackRepository = TrackRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository, trackRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository,
                    isSync = true,
                    tracks = listOf(track(trackerId = 10L, title = "New", libraryId = 0L)),
                )

                trackRepository.getTracksByMangaId(1L).single().libraryId.shouldBeNull()
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun restore(
        repository: RestoreRepositoryImpl,
        isSync: Boolean,
        version: Long = 5L,
        lastModifiedAt: Long = 10L,
        tracks: List<Track>,
    ) {
        repository.restoreManga(
            entries = listOf(
                RestoredManga(
                    manga = manga(version = version, lastModifiedAt = lastModifiedAt),
                    chapters = emptyList(),
                    categoryIds = null,
                    history = emptyList(),
                    tracks = tracks,
                    excludedScanlators = emptyList(),
                ),
            ),
            isSync = isSync,
            restoreCategories = false,
            restoreTracking = true,
            restoreHistory = true,
            restoreCustomInfo = true,
            removeMissingChaptersBefore = null,
            update = { it.toMangaUpdate() },
        )
    }

    private fun restoreRepository(
        database: Database,
        mangaRepository: MangaRepositoryImpl,
        trackRepository: TrackRepositoryImpl,
    ) = RestoreRepositoryImpl(
        database = database,
        mangaRepository = mangaRepository,
        chapterRepository = ChapterRepositoryImpl(database),
        trackRepository = trackRepository,
        mangaMetadataRepository = mockk(relaxed = true),
    )

    private fun manga(version: Long, lastModifiedAt: Long) = Manga.create().copy(
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

    private fun track(
        trackerId: Long,
        title: String = "Track",
        remoteId: Long = trackerId * 10,
        remoteUrl: String = "/track/$trackerId",
        libraryId: Long? = null,
        lastChapterRead: Double = 0.0,
        totalChapters: Long = 10L,
        status: Long = 1L,
        score: Double = 0.0,
        startDate: Long? = null,
        finishDate: Long? = null,
        private: Boolean = false,
    ) = Track(
        id = -1L,
        mangaId = 1L,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate?.let { Instant.fromEpochMilliseconds(it) },
        finishDate = finishDate?.let { Instant.fromEpochMilliseconds(it) },
        private = private,
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
        trackerId: Long,
        title: String,
        remoteId: Long = trackerId * 10,
        remoteUrl: String = "/track/$trackerId",
        libraryId: Long? = null,
        lastChapterRead: Double = 0.0,
        totalChapters: Long = 10L,
        status: Long = 1L,
        score: Double = 0.0,
        startDate: Long? = null,
        finishDate: Long? = null,
        private: Boolean = false,
    ) {
        execute(
            null,
            """
            INSERT INTO manga_track(
                id, manga_id, tracker_id, remote_id, remote_url, library_id, title,
                last_chapter_read, total_chapters, status, score, start_date, finish_date, private
            ) VALUES (
                $trackerId, 1, $trackerId, $remoteId, '$remoteUrl', ${libraryId ?: "NULL"}, '$title',
                $lastChapterRead, $totalChapters, $status, $score, ${startDate ?: "NULL"},
                ${finishDate ?: "NULL"}, ${if (private) 1 else 0}
            )
            """.trimIndent(),
            0,
        ).await()
    }
}
// KUN <--
