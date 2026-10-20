// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
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
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import kotlin.time.Instant

/**
 * Guards chapter metadata on a restore: a chapter URL that already existed locally used to keep its
 * old title, number, scanlator and fetch date, because `updateFromBackup` only wrote read state.
 * A restored library therefore had the right URL set but stale metadata for every existing chapter.
 */
class RestoreRepositoryChapterMetadataTest {

    @Test
    fun `a newer remote chapter replaces the stale local metadata`() {
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
                        chapter(
                            "/c1",
                            name = "Chapter 1 (old)",
                            chapterNumber = 1.0,
                            scanlator = "Old scanlator",
                            dateFetch = 1_000L,
                            version = 1L,
                            lastModifiedAt = 10L,
                        ),
                    ),
                )

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            chapters = listOf(
                                chapter(
                                    "/c1",
                                    name = "Chapter 1 (new)",
                                    chapterNumber = 2.0,
                                    scanlator = "New scanlator",
                                    dateFetch = 2_000L,
                                    version = 2L,
                                    lastModifiedAt = 20L,
                                ),
                            ),
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

                val restored = chapterRepository.getChapterByMangaId(1).single { it.url == "/c1" }
                restored.name shouldBe "Chapter 1 (new)"
                restored.chapterNumber shouldBe 2.0
                restored.scanlator shouldBe "New scanlator"
                restored.dateFetch shouldBe Instant.fromEpochMilliseconds(2_000L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a newer remote chapter can clear a stale scanlator`() {
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
                    listOf(chapter("/c1", scanlator = "Old scanlator", version = 1L, lastModifiedAt = 10L)),
                )

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            // The source dropped the scanlator; direct assignment must not coalesce it away.
                            chapters = listOf(
                                chapter("/c1", scanlator = null, version = 2L, lastModifiedAt = 20L),
                            ),
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

                val restored = chapterRepository.getChapterByMangaId(1).single { it.url == "/c1" }
                restored.scanlator shouldBe null
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a newer local chapter keeps its metadata against an older remote copy`() {
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
                        chapter(
                            "/c1",
                            name = "Local title",
                            chapterNumber = 3.0,
                            scanlator = "Local scanlator",
                            dateFetch = 3_000L,
                            version = 3L,
                            lastModifiedAt = 100L,
                        ),
                    ),
                )

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            chapters = listOf(
                                chapter(
                                    "/c1",
                                    name = "Remote title",
                                    chapterNumber = 4.0,
                                    scanlator = "Remote scanlator",
                                    dateFetch = 4_000L,
                                    version = 2L,
                                    lastModifiedAt = 10L,
                                ),
                            ),
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

                val restored = chapterRepository.getChapterByMangaId(1).single { it.url == "/c1" }
                restored.name shouldBe "Local title"
                restored.chapterNumber shouldBe 3.0
                restored.scanlator shouldBe "Local scanlator"
                restored.dateFetch shouldBe Instant.fromEpochMilliseconds(3_000L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an ordinary restore keeps the local dateFetch`() {
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
                    listOf(chapter("/c1", dateFetch = 2_000L, bookmark = false, version = 1L, lastModifiedAt = 10L)),
                )

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            // An older backup whose state still has to be merged; its fetch date must
                            // not move the chapter backwards in the Updates feed.
                            chapters = listOf(
                                chapter("/c1", dateFetch = 1_000L, bookmark = true, version = 2L, lastModifiedAt = 20L),
                            ),
                            categoryIds = null,
                            history = emptyList(),
                            tracks = emptyList(),
                            excludedScanlators = emptyList(),
                        ),
                    ),
                    isSync = false,
                    restoreCategories = false,
                    removeMissingChaptersBefore = null,
                    update = { it.toMangaUpdate() },
                )

                val restored = chapterRepository.getChapterByMangaId(1).single { it.url == "/c1" }
                restored.dateFetch shouldBe Instant.fromEpochMilliseconds(2_000L)
                // The rest of the backup state is still merged, so this is not a skipped row.
                restored.bookmark shouldBe true
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an ordinary restore does not lower a chapter version`() {
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
                    listOf(chapter("/c1", sourceOrder = 1L, version = 5L, lastModifiedAt = 10L)),
                )

                repository.restoreManga(
                    entries = listOf(
                        RestoredManga(
                            manga = manga(),
                            // An older backup differing only in order/fetch state must be merged
                            // without moving the stored sync version backwards.
                            chapters = listOf(
                                chapter("/c1", sourceOrder = 5L, version = 2L, lastModifiedAt = 20L),
                            ),
                            categoryIds = null,
                            history = emptyList(),
                            tracks = emptyList(),
                            excludedScanlators = emptyList(),
                        ),
                    ),
                    isSync = false,
                    restoreCategories = false,
                    removeMissingChaptersBefore = null,
                    update = { it.toMangaUpdate() },
                )

                val restored = chapterRepository.getChapterByMangaId(1).single { it.url == "/c1" }
                restored.version shouldBe 5L
            } finally {
                driver.close()
            }
        }
    }

    private fun chapter(
        url: String,
        name: String = url,
        chapterNumber: Double = -1.0,
        scanlator: String? = null,
        dateFetch: Long = 1_000L,
        bookmark: Boolean = false,
        sourceOrder: Long = 0L,
        version: Long = 1L,
        lastModifiedAt: Long = 0L,
    ) = Chapter.create().copy(
        mangaId = 1L,
        url = url,
        name = name,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        dateFetch = Instant.fromEpochMilliseconds(dateFetch),
        bookmark = bookmark,
        sourceOrder = sourceOrder,
        version = version,
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
