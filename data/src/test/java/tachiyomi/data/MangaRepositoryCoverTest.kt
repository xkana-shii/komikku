package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.manga.MangaRepositoryImpl

class MangaRepositoryCoverTest {

    @Test
    fun `cover-only update bumps sync version and preserves the Coil timestamp`() {
        runBlocking {
            val driver = newDriver()
            try {
                val repository = MangaRepositoryImpl(DatabaseBindings.providesDatabase(driver))
                driver.insertManga()

                repository.updateCover(
                    1,
                    "https://old.example/cover.jpg",
                    "https://new.example/cover.jpg",
                ) shouldBe true

                driver.scalarString("SELECT remote_cover FROM manga WHERE id = 1") shouldBe
                    "https://new.example/cover.jpg"
                driver.scalarLong("SELECT state_cover_last_modified FROM manga WHERE id = 1") shouldBe 123
                driver.scalarLong("SELECT version FROM manga WHERE id = 1") shouldBe 2
                ((driver.scalarLong("SELECT last_modified_at FROM manga WHERE id = 1") ?: 0) > 0) shouldBe true
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a mismatched stale URL changes nothing`() {
        runBlocking {
            val driver = newDriver()
            try {
                val repository = MangaRepositoryImpl(DatabaseBindings.providesDatabase(driver))
                driver.insertManga()

                repository.updateCover(
                    1,
                    "https://other.example/cover.jpg",
                    "https://new.example/cover.jpg",
                ) shouldBe false

                driver.scalarString("SELECT remote_cover FROM manga WHERE id = 1") shouldBe
                    "https://old.example/cover.jpg"
                driver.scalarLong("SELECT state_cover_last_modified FROM manga WHERE id = 1") shouldBe 123
                driver.scalarLong("SELECT version FROM manga WHERE id = 1") shouldBe 1
                driver.scalarLong("SELECT last_modified_at FROM manga WHERE id = 1") shouldBe 0
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a row that is being synced is left alone so the write can be retried`() {
        runBlocking {
            val driver = newDriver()
            try {
                val repository = MangaRepositoryImpl(DatabaseBindings.providesDatabase(driver))
                driver.insertManga(isSyncing = 1)

                repository.updateCover(
                    1,
                    "https://old.example/cover.jpg",
                    "https://new.example/cover.jpg",
                ) shouldBe false

                driver.scalarString("SELECT remote_cover FROM manga WHERE id = 1") shouldBe
                    "https://old.example/cover.jpg"
                driver.scalarLong("SELECT state_cover_last_modified FROM manga WHERE id = 1") shouldBe 123
                driver.scalarLong("SELECT version FROM manga WHERE id = 1") shouldBe 1
                driver.scalarLong("SELECT last_modified_at FROM manga WHERE id = 1") shouldBe 0
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

    private suspend fun SqlDriver.insertManga(isSyncing: Int = 0) {
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
                0, 1, 'https://old.example/cover.jpg',
                123, 0, 1, $isSyncing,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(
            null,
            sql,
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
            0,
        ).value

    private fun SqlDriver.scalarString(sql: String): String? =
        executeQuery(
            null,
            sql,
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            0,
        ).value
}
