// KUN -->
package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings

/**
 * Guards the library-removal tombstones that let a sync upload an entry the user removed from the
 * library: an un-favorite is no longer a favorite, so it matches no upload query unless the
 * `library_removal` row carries it.
 *
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY#1679), adapted to this fork's column names
 * (`is_syncing`, `last_modified_at`, `version`) and to its "ever favorited" upload rule: the fork
 * uploads every entry that has a favorite timestamp, so a removal older than the last push still
 * rides the full upload instead of being dropped by the window.
 */
class MangaSyncQueriesTest {

    @Test
    fun `removing a manga from the library bumps its version`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertManga(id = 1, inLibrary = true)

                driver.setInLibrary(id = 1, inLibrary = false)

                driver.versionOf(database, 1) shouldBe 1L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `adding a manga to the library bumps its version`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertManga(id = 1, inLibrary = false)

                driver.setInLibrary(id = 1, inLibrary = true)

                driver.versionOf(database, 1) shouldBe 1L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a library removal rides the delta upload`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertManga(id = 1, inLibrary = true)
                driver.insertManga(id = 2, inLibrary = true)

                driver.setInLibrary(id = 1, inLibrary = false)

                // The removal is the delta's point; the favorite inserted after it rides along
                // because its own insert moved `last_modified_at` past the window.
                database.mangaQueries.getMangasModifiedSince(1, MangaMapper::mapManga).awaitAsList()
                    .map { it.id to it.favorite } shouldContainExactlyInAnyOrder listOf(1L to false, 2L to true)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a library removal rides the full upload`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertManga(id = 1, inLibrary = true)
                driver.insertManga(id = 2, inLibrary = true)

                driver.setInLibrary(id = 1, inLibrary = false)

                database.mangaQueries.getMangasToSync(0, MangaMapper::mapManga).awaitAsList()
                    .map { it.id to it.favorite } shouldContainExactlyInAnyOrder listOf(1L to false, 2L to true)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a removal whose favorite timestamp was lost rides the full upload inside the window only`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertManga(id = 1, inLibrary = true)
                driver.setInLibrary(id = 1, inLibrary = false)
                // A row written by a build whose insert trigger was missing: no favorite timestamp,
                // so only the removal tombstone keeps it in the upload.
                driver.execute("UPDATE manga SET favorite_modified_at = NULL WHERE id = 1")

                database.mangaQueries.getMangasToSync(0, MangaMapper::mapManga).awaitAsList()
                    .map { it.id } shouldContainExactly listOf(1L)
                // The server already holds this removal; the next push window leaves it out.
                database.mangaQueries.getMangasToSync(Long.MAX_VALUE, MangaMapper::mapManga)
                    .awaitAsList()
                    .shouldBeEmpty()
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a removal applied by sync records no tombstone`() {
        runBlocking {
            val driver = newDriver()
            try {
                driver.insertManga(id = 1, inLibrary = true)

                driver.execute("UPDATE manga SET user_favorite_at = NULL, is_syncing = 1 WHERE id = 1")
                driver.execute("UPDATE manga SET is_syncing = 0 WHERE id = 1")

                // Echoing the removal back would make the next upload fight the server that sent it.
                driver.scalarLong("SELECT count(*) FROM library_removal WHERE manga_id = 1") shouldBe 0L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a local removal records a tombstone`() {
        runBlocking {
            val driver = newDriver()
            try {
                driver.insertManga(id = 1, inLibrary = true)

                driver.setInLibrary(id = 1, inLibrary = false)

                driver.scalarLong("SELECT count(*) FROM library_removal WHERE manga_id = 1") shouldBe 1L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a manga never in the library is not uploaded`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertManga(id = 1, inLibrary = false)

                driver.execute("UPDATE manga SET remote_description = 'new' WHERE id = 1")

                database.mangaQueries.getMangasToSync(0, MangaMapper::mapManga).awaitAsList().shouldBeEmpty()
                database.mangaQueries.getMangasModifiedSince(0, MangaMapper::mapManga).awaitAsList().shouldBeEmpty()
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.execute(sql: String) {
        execute(null, sql, 0).await()
    }

    private suspend fun SqlDriver.insertManga(id: Long, inLibrary: Boolean) {
        val favoriteAt = if (inLibrary) "1000" else "NULL"
        execute(
            """
            INSERT INTO manga (
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy, remote_memo,
                user_favorite_at, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized
            ) VALUES ($id, 1, '/m$id', 'm$id', 0, 0, X'7B7D', $favoriteAt, '', 0, 0, 0, 0)
            """.trimIndent(),
        )
    }

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(null, sql, { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) }, 0)
            .value

    private suspend fun SqlDriver.setInLibrary(id: Long, inLibrary: Boolean) {
        val favoriteAt = if (inLibrary) "2000" else "NULL"
        execute("UPDATE manga SET user_favorite_at = $favoriteAt WHERE id = $id")
    }

    private suspend fun SqlDriver.versionOf(database: Database, id: Long): Long {
        return database.mangaQueries.getMangaById(id, MangaMapper::mapManga).awaitAsOne().version
    }
}
// KUN <--
