// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import kotlin.time.Instant

/**
 * Guards the SyncYomi v2 chapter tombstones: the server returns each manga with its complete
 * (remaining) chapter list, so a sync restore has to drop the local chapters it no longer sees.
 */
class RestoreRepositoryChapterTombstoneTest {

    @Test
    fun `a sync restore drops chapters missing from the server's complete list`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val chapterRepository = ChapterRepositoryImpl(database)
                val repository = RestoreRepositoryImpl(
                    database = database,
                    mangaRepository = mangaRepository,
                    chapterRepository = chapterRepository,
                    trackRepository = TrackRepositoryImpl(database),
                    mangaMetadataRepository = mockk(relaxed = true),
                    customMangaRepository = mockk(relaxed = true),
                )

                driver.insertManga()
                chapterRepository.addAll(listOf(chapter("/c1"), chapter("/c2")))

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            // The server tombstoned /c2, so it is absent from the complete list.
                            chapters = listOf(chapter("/c1")),
                            categoryIds = null,
                            history = emptyList(),
                            tracks = emptyList(),
                            excludedScanlators = emptyList(),
                        ),
                    ),
                    isSync = true,
                    restoreCategories = false,
                    removeMissingChaptersBefore = 1_000L,
                    update = { it.toMangaUpdate() },
                )

                chapterRepository.getChapterByMangaId(1).map { it.url } shouldContainExactly listOf("/c1")
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a sync restore drops every local chapter when the server's list is empty`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val chapterRepository = ChapterRepositoryImpl(database)
                val repository = RestoreRepositoryImpl(
                    database = database,
                    mangaRepository = mangaRepository,
                    chapterRepository = chapterRepository,
                    trackRepository = TrackRepositoryImpl(database),
                    mangaMetadataRepository = mockk(relaxed = true),
                    customMangaRepository = mockk(relaxed = true),
                )

                driver.insertManga()
                chapterRepository.addAll(listOf(chapter("/c1"), chapter("/c2")))

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            // Every chapter was tombstoned on the server.
                            chapters = emptyList(),
                            categoryIds = null,
                            history = emptyList(),
                            tracks = emptyList(),
                            excludedScanlators = emptyList(),
                        ),
                    ),
                    isSync = true,
                    restoreCategories = false,
                    removeMissingChaptersBefore = 1_000L,
                    update = { it.toMangaUpdate() },
                )

                chapterRepository.getChapterByMangaId(1).map { it.url } shouldContainExactly emptyList()
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a sync restore keeps a chapter added after the upload snapshot`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val chapterRepository = ChapterRepositoryImpl(database)
                val repository = RestoreRepositoryImpl(
                    database = database,
                    mangaRepository = mangaRepository,
                    chapterRepository = chapterRepository,
                    trackRepository = TrackRepositoryImpl(database),
                    mangaMetadataRepository = mockk(relaxed = true),
                    customMangaRepository = mockk(relaxed = true),
                )

                driver.insertManga()
                chapterRepository.addAll(
                    listOf(
                        chapter("/c1"),
                        // Added by a library update after the sync snapshot; not tombstoned.
                        chapter("/c2", lastModifiedAt = 2_000L),
                    ),
                )

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            chapters = emptyList(),
                            categoryIds = null,
                            history = emptyList(),
                            tracks = emptyList(),
                            excludedScanlators = emptyList(),
                        ),
                    ),
                    isSync = true,
                    restoreCategories = false,
                    removeMissingChaptersBefore = 1_000L,
                    update = { it.toMangaUpdate() },
                )

                chapterRepository.getChapterByMangaId(1).map { it.url } shouldContainExactly listOf("/c2")
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a v1 sync restore keeps chapters the response does not list`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val chapterRepository = ChapterRepositoryImpl(database)
                val repository = RestoreRepositoryImpl(
                    database = database,
                    mangaRepository = mangaRepository,
                    chapterRepository = chapterRepository,
                    trackRepository = TrackRepositoryImpl(database),
                    mangaMetadataRepository = mockk(relaxed = true),
                    customMangaRepository = mockk(relaxed = true),
                )

                driver.insertManga()
                chapterRepository.addAll(listOf(chapter("/c1"), chapter("/c2")))

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            chapters = listOf(chapter("/c1")),
                            categoryIds = null,
                            history = emptyList(),
                            tracks = emptyList(),
                            excludedScanlators = emptyList(),
                        ),
                    ),
                    isSync = true,
                    restoreCategories = false,
                    removeMissingChaptersBefore = null,
                    update = { it.toMangaUpdate() },
                )

                chapterRepository.getChapterByMangaId(1).map { it.url } shouldContainExactly listOf("/c1", "/c2")
            } finally {
                driver.close()
            }
        }
    }

    private fun chapter(url: String, lastModifiedAt: Long = 0L) = Chapter.create().copy(
        mangaId = 1L,
        url = url,
        name = url,
        version = 1L,
        dateFetch = Instant.fromEpochMilliseconds(1_000),
        lastModifiedAt = lastModifiedAt,
    )

    private fun manga() = Manga(
        id = 1L,
        source = 1L,
        favorite = true,
        lastUpdate = null,
        nextUpdate = null,
        fetchInterval = 0,
        dateAdded = Instant.fromEpochMilliseconds(1_000),
        viewerFlags = 0L,
        chapterFlags = 0L,
        coverLastModified = null,
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
        lastModifiedAt = 0L,
        favoriteModifiedAt = null,
        version = 1L,
        notes = "",
        memo = JsonObject(emptyMap()),
    )

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.insertManga() {
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
                123, 0, 1, 0,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }
}
// KUN <--
