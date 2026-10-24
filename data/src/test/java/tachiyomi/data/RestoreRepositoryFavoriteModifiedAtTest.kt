// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.toMangaUpdate
import kotlin.time.Instant

/**
 * Guards the `favorite_modified_at` restore path: it used to be computed by `copyFrom` and then
 * dropped, and the favorite trigger stamped `now` over a value coming from the sync payload.
 */
class RestoreRepositoryFavoriteModifiedAtTest {

    @Test
    fun `a sync restore keeps the incoming favorite timestamp`() {
        runBlocking {
            val restored = restoreAndLoad(
                localFavorite = false,
                localFavoriteModifiedAt = 500L,
                incoming = manga(
                    favorite = true,
                    version = 2L,
                    lastModifiedAt = 20L,
                    favoriteModifiedAt = 2_000L,
                ),
            )

            restored.favorite shouldBe true
            restored.favoriteModifiedAt shouldBe 2_000L
        }
    }

    @Test
    fun `a sync restore does not erase a known favorite timestamp with a null`() {
        runBlocking {
            val restored = restoreAndLoad(
                localFavorite = true,
                localFavoriteModifiedAt = 500L,
                incoming = manga(
                    favorite = true,
                    version = 2L,
                    lastModifiedAt = 20L,
                    favoriteModifiedAt = null,
                ),
            )

            restored.favoriteModifiedAt shouldBe 500L
        }
    }

    @Test
    fun `a sync insert keeps the incoming favorite timestamp`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository)

                repository.restoreManga(
                    entries = listOf(restoredManga(manga(favorite = true, version = 1L, favoriteModifiedAt = 2_000L))),
                    isSync = true,
                    restoreCategories = false,
                    removeMissingChaptersBefore = null,
                    update = { it.toMangaUpdate() },
                )

                val restored = mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1)!!
                restored.favoriteModifiedAt shouldBe 2_000L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an ordinary insert lets the trigger stamp the current time`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository)

                repository.restoreManga(
                    entries = listOf(restoredManga(manga(favorite = true, version = 1L, favoriteModifiedAt = 2_000L))),
                    isSync = false,
                    restoreCategories = false,
                    removeMissingChaptersBefore = null,
                    update = { it.toMangaUpdate() },
                )

                val restored = mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1)!!
                // Ordinary restore does not carry an authoritative favorite timestamp, so the
                // insert trigger stamps `now` instead of persisting the backup's stale value.
                restored.favoriteModifiedAt shouldNotBe 2_000L
                restored.favoriteModifiedAt!! shouldBeGreaterThan 1_000_000_000L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a normal favorite change still stamps the current time`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                driver.insertManga(favorite = false, favoriteModifiedAt = 500L)

                mangaRepository.update(
                    MangaUpdate(id = 1L) {
                        favorite = true
                        dateAdded = Instant.fromEpochMilliseconds(1_000L)
                    },
                )

                val updated = mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1)!!
                updated.favorite shouldBe true
                updated.favoriteModifiedAt shouldNotBe 500L
                updated.favoriteModifiedAt!! shouldBeGreaterThan 1_000_000_000L
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun restoreAndLoad(
        localFavorite: Boolean,
        localFavoriteModifiedAt: Long?,
        incoming: Manga,
    ): Manga {
        val driver = newDriver()
        return try {
            val database = DatabaseBindings.providesDatabase(driver)
            val mangaRepository = MangaRepositoryImpl(database)
            val repository = restoreRepository(database, mangaRepository)

            driver.insertManga(localFavorite, localFavoriteModifiedAt)
            repository.restoreManga(
                entries = listOf(restoredManga(incoming)),
                isSync = true,
                restoreCategories = false,
                removeMissingChaptersBefore = null,
                update = { it.toMangaUpdate() },
            )

            mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1)!!
        } finally {
            driver.close()
        }
    }

    private fun restoreRepository(database: Database, mangaRepository: MangaRepositoryImpl) =
        RestoreRepositoryImpl(
            database = database,
            mangaRepository = mangaRepository,
            chapterRepository = ChapterRepositoryImpl(database),
            trackRepository = TrackRepositoryImpl(database),
            mangaMetadataRepository = mockk(relaxed = true),
        )

    private fun restoredManga(manga: Manga) = RestoredManga(
        manga = manga,
        chapters = emptyList(),
        categoryIds = null,
        history = emptyList(),
        tracks = emptyList(),
        excludedScanlators = emptyList(),
    )

    private fun manga(
        favorite: Boolean,
        version: Long,
        lastModifiedAt: Long = 10L,
        favoriteModifiedAt: Long?,
    ) = Manga.create().copy(
        id = 1L,
        source = 1L,
        favorite = favorite,
        dateAdded = if (favorite) Instant.fromEpochMilliseconds(1_000) else null,
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
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
        notes = "",
        memo = JsonObject(emptyMap()),
    )

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.insertManga(favorite: Boolean, favoriteModifiedAt: Long?) {
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
                123, 10, 1, 0,
                ${if (favorite) "1000" else "NULL"}, ${favoriteModifiedAt ?: "NULL"}
            )
            """.trimIndent(),
            0,
        ).await()
    }
}
// KUN <--
