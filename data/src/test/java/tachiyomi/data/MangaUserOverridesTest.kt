// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.updates.UpdatesRepositoryImpl
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import kotlin.time.Instant

/**
 * The user's edited manga details as a column of the manga row.
 *
 * The overrides reach the read paths through the `user_*` columns of every manga query, the
 * `libraryView`, `historyView` and `updatesView` column lists and the genre adapter, so this covers
 * the whole chain instead of the write alone: a column that is selected in the wrong position or a
 * view that was not extended shows the source value here.
 */
class MangaUserOverridesTest {

    private val overrides = MangaUserInfoUpdate(
        id = 1L,
        title = "Edited title",
        author = "Edited author",
        artist = "Edited artist",
        thumbnailUrl = "https://edited.example/cover.jpg",
        description = "Edited description",
        genre = listOf("Action", "Drama"),
        status = 3L,
    )

    @Test
    fun `every manga query returns the overrides while the source values stay untouched`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaRepositoryImpl(database)
                driver.insertManga()

                repository.updateUserInfoAll(listOf(overrides))

                // A plain row read.
                assertOverrides(repository.getMangaById(1L))
                // The url+source lookup.
                assertOverrides(repository.getMangaByUrlAndSourceId("/manga/1", 1L)!!)
                // getFavorites / getAll / getMangasWithFavoriteTimestamp style lists.
                assertOverrides(repository.getFavorites().single())
                assertOverrides(repository.getAll().single())
                // The libraryView (`SELECT *` over `M.*`).
                val libraryManga = repository.getLibraryManga().single()
                assertOverrides(libraryManga.manga)
                libraryManga.manga.title shouldBe "Edited title"
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `clearing the overrides shows the source values again`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaRepositoryImpl(database)
                driver.insertManga()
                repository.updateUserInfoAll(listOf(overrides))

                repository.updateUserInfoAll(listOf(overrides.copy(title = null, genre = null, status = null)))

                val manga = repository.getMangaById(1L)
                manga.userTitle shouldBe null
                manga.userGenre shouldBe null
                manga.userStatus shouldBe null
                // The accessors fall back to the source values.
                manga.title shouldBe "Source title"
                manga.genre shouldBe listOf("Source genre")
                manga.status shouldBe 0L
                // Fields the clear did not touch keep their override.
                manga.userAuthor shouldBe "Edited author"
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `the history and updates views carry the edited title and cover`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                driver.insertManga()
                mangaRepository.updateUserInfoAll(listOf(overrides))
                driver.insertChapter()
                driver.insertHistory()

                val history = HistoryRepositoryImpl(database)
                    .getHistory("", unfinishedManga = null, unfinishedChapter = null, nonLibraryEntries = null)
                    .first()
                    .single()
                history.title shouldBe "Edited title"
                history.ogTitle shouldBe "Source title"
                history.coverData.userThumbnailUrl shouldBe "https://edited.example/cover.jpg"
                history.coverData.url shouldBe "https://edited.example/cover.jpg"

                val update = UpdatesRepositoryImpl(database)
                    .awaitWithRead(read = false, after = Instant.fromEpochMilliseconds(0), limit = 10)
                    .single()
                update.mangaTitle shouldBe "Edited title"
                update.ogMangaTitle shouldBe "Source title"
                update.coverData.url shouldBe "https://edited.example/cover.jpg"
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a source refresh moves the source values and keeps the overrides`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaRepositoryImpl(database)
                driver.insertManga()
                repository.updateUserInfoAll(listOf(overrides))

                // What a delegated source's details fetch writes (`awaitUpdateFromSource`).
                repository.updateRemote(
                    MangaRemoteUpdate(
                        id = 1L,
                        title = "New source title",
                        author = "New source author",
                        artist = "New source artist",
                        description = "New source description",
                        genre = listOf("New genre"),
                        status = 1L,
                        thumbnailUrl = "https://source.example/new.jpg",
                        updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
                        memo = kotlinx.serialization.json.JsonObject(emptyMap()),
                        initialized = true,
                        coverLastModified = null,
                    ),
                )

                val manga = repository.getMangaById(1L)
                // The edit still wins in the UI...
                manga.title shouldBe "Edited title"
                manga.thumbnailUrl shouldBe "https://edited.example/cover.jpg"
                manga.genre shouldBe listOf("Action", "Drama")
                // ...while the source values behind it moved.
                manga.ogTitle shouldBe "New source title"
                manga.ogThumbnailUrl shouldBe "https://source.example/new.jpg"
                manga.ogGenre shouldBe listOf("New genre")
            } finally {
                driver.close()
            }
        }
    }

    private fun assertOverrides(manga: tachiyomi.domain.manga.model.Manga) {
        manga.userTitle shouldBe "Edited title"
        manga.userAuthor shouldBe "Edited author"
        manga.userArtist shouldBe "Edited artist"
        manga.userThumbnailUrl shouldBe "https://edited.example/cover.jpg"
        manga.userDescription shouldBe "Edited description"
        manga.userGenre shouldBe listOf("Action", "Drama")
        manga.userStatus shouldBe 3L
        // The edited values are what the UI shows; the source values are kept beside them.
        manga.title shouldBe "Edited title"
        manga.thumbnailUrl shouldBe "https://edited.example/cover.jpg"
        manga.ogTitle shouldBe "Source title"
        manga.ogGenre shouldBe listOf("Source genre")
    }

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
                id, source_id, remote_url, remote_title, remote_author, remote_genre,
                remote_status, remote_update_strategy, remote_memo, user_notes,
                user_reader_flags, user_chapter_flags, state_chapter_fetch_interval,
                state_initialized, remote_cover, state_cover_last_modified,
                last_modified_at, version, is_syncing, user_favorite_at, favorite_modified_at
            ) VALUES (
                1, 1, '/manga/1', 'Source title', 'Source author', 'Source genre',
                0, 0, '{}', '',
                0, 0, 0,
                1, 'https://source.example/cover.jpg', 123,
                10, 1, 0, 1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertChapter() {
        execute(
            null,
            """
            INSERT INTO chapter(
                id, manga_id, remote_url, remote_name, remote_chapter_number,
                remote_order, remote_memo, user_read, user_bookmark,
                user_last_page_read, state_date_fetch,
                remote_date_upload, last_modified_at, version, is_syncing
            ) VALUES (
                1, 1, '/manga/1/1', 'Chapter 1', 1.0,
                1, '{}', 0, 0,
                0, 2000,
                2000, 10, 1, 0
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertHistory() {
        execute(
            null,
            "INSERT INTO history(id, chapter_id, manga_id, read_at, read_duration) VALUES (1, 1, 1, 3000, 60)",
            0,
        ).await()
    }
}
// KUN <--
