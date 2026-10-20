// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.sync.SyncRepositoryImpl

/**
 * Guards the one-shot SyncYomi force push: every library row must outrank a stale device's counter
 * so the next upload wins, while entries outside the library stay untouched.
 */
class SyncForcePushTest {

    @Test
    fun `force push raises library manga, its chapters and user categories`() {
        runBlocking {
            val driver = newDriver()
            try {
                val repository = SyncRepositoryImpl(DatabaseBindings.providesDatabase(driver))

                driver.insertManga(id = 1, userFavoriteAt = 1_000, favoriteModifiedAt = 500)
                driver.insertChapter(id = 1, mangaId = 1)
                // Removed from the library, but it was favorited once and is still uploaded.
                driver.insertManga(id = 3, userFavoriteAt = null, favoriteModifiedAt = 900)
                driver.insertChapter(id = 3, mangaId = 3)
                driver.insertCategory(id = 1)

                repository.markForForcePush(
                    version = 1_700_000_000_000,
                    lastModifiedAt = 1_600_000_000,
                    bumpCategories = true,
                )

                driver.scalarLong("SELECT version FROM manga WHERE id = 1") shouldBe 1_700_000_000_000
                driver.scalarLong("SELECT version FROM chapter WHERE id = 1") shouldBe 1_700_000_000_000
                driver.scalarLong("SELECT version FROM category WHERE id = 1") shouldBe 1_700_000_000_000
                driver.scalarLong("SELECT version FROM manga WHERE id = 3") shouldBe 1_700_000_000_000
                driver.scalarLong("SELECT version FROM chapter WHERE id = 3") shouldBe 1_700_000_000_000

                // The update also stamps the upload cursor; using a timestamp just before syncStart avoids
                // re-uploading the whole library as the next delta.
                driver.scalarLong("SELECT last_modified_at FROM manga WHERE id = 1") shouldBe 1_600_000_000L
                driver.scalarLong("SELECT last_modified_at FROM chapter WHERE id = 1") shouldBe 1_600_000_000L
                ((driver.scalarLong("SELECT favorite_modified_at FROM manga WHERE id = 1") ?: 0) > 0) shouldBe true
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an entry outside the library is not bumped`() {
        runBlocking {
            val driver = newDriver()
            try {
                val repository = SyncRepositoryImpl(DatabaseBindings.providesDatabase(driver))

                driver.insertManga(id = 2, userFavoriteAt = null, favoriteModifiedAt = null)
                driver.insertChapter(id = 2, mangaId = 2)
                // The system category (id 0) exists from the schema and is never synced.

                repository.markForForcePush(
                    version = 1_700_000_000_000,
                    lastModifiedAt = 1_600_000_000,
                    bumpCategories = true,
                )

                driver.scalarLong("SELECT version FROM manga WHERE id = 2") shouldBe 1
                driver.scalarLong("SELECT version FROM chapter WHERE id = 2") shouldBe 1
                driver.scalarLong("SELECT version FROM category WHERE id = 0") shouldBe 0
                driver.scalarLong("SELECT last_modified_at FROM manga WHERE id = 2") shouldBe 0
                driver.scalarLong("SELECT last_modified_at FROM chapter WHERE id = 2") shouldBe 0
                driver.scalarLong("SELECT favorite_modified_at FROM manga WHERE id = 2") shouldBe null
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a locally higher version is kept`() {
        runBlocking {
            val driver = newDriver()
            try {
                val repository = SyncRepositoryImpl(DatabaseBindings.providesDatabase(driver))

                driver.insertManga(
                    id = 1,
                    userFavoriteAt = 1_000,
                    favoriteModifiedAt = 2_000_000_000,
                    version = 2_000_000_000_000,
                )

                repository.markForForcePush(
                    version = 1_700_000_000_000,
                    lastModifiedAt = 1_600_000_000,
                    bumpCategories = true,
                )

                driver.scalarLong("SELECT version FROM manga WHERE id = 1") shouldBe 2_000_000_000_000
                // The favorite timestamp is raised, never lowered.
                driver.scalarLong("SELECT favorite_modified_at FROM manga WHERE id = 1") shouldBe 2_000_000_000
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `categories are left untouched when category sync is disabled`() {
        runBlocking {
            val driver = newDriver()
            try {
                val repository = SyncRepositoryImpl(DatabaseBindings.providesDatabase(driver))

                driver.insertManga(id = 1, userFavoriteAt = 1_000, favoriteModifiedAt = 500)
                driver.insertChapter(id = 1, mangaId = 1)
                driver.insertCategory(id = 1)
                // The insert trigger already stamps the category; the force push must leave both alone.
                val categoryVersion = driver.scalarLong("SELECT version FROM category WHERE id = 1")
                val categoryModifiedAt = driver.scalarLong("SELECT last_modified_at FROM category WHERE id = 1")

                repository.markForForcePush(
                    version = 1_700_000_000_000,
                    lastModifiedAt = 1_600_000_000,
                    bumpCategories = false,
                )

                // Manga and chapters still win the merge; categories keep the server's copy.
                driver.scalarLong("SELECT version FROM manga WHERE id = 1") shouldBe 1_700_000_000_000
                driver.scalarLong("SELECT version FROM chapter WHERE id = 1") shouldBe 1_700_000_000_000
                driver.scalarLong("SELECT version FROM category WHERE id = 1") shouldBe categoryVersion
                driver.scalarLong("SELECT last_modified_at FROM category WHERE id = 1") shouldBe categoryModifiedAt
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a timestamp newer than the force push is kept`() {
        runBlocking {
            val driver = newDriver()
            try {
                val repository = SyncRepositoryImpl(DatabaseBindings.providesDatabase(driver))

                driver.insertManga(id = 1, userFavoriteAt = 1_000, favoriteModifiedAt = 500)
                driver.insertChapter(id = 1, mangaId = 1)
                // An edit made while the forced upload was in flight.
                driver.execute(null, "UPDATE manga SET last_modified_at = 1700000000 WHERE id = 1", 0).await()
                driver.execute(null, "UPDATE chapter SET last_modified_at = 1700000000 WHERE id = 1", 0).await()

                repository.markForForcePush(
                    version = 1_700_000_000_000,
                    lastModifiedAt = 1_600_000_000,
                    bumpCategories = false,
                )

                // The next delta (which starts at the sync's start time) must still carry the edit:
                // the force stamp never moves a newer timestamp backwards.
                (driver.scalarLong("SELECT last_modified_at FROM manga WHERE id = 1")!! > 1_600_000_000) shouldBe true
                (driver.scalarLong("SELECT last_modified_at FROM chapter WHERE id = 1")!! > 1_600_000_000) shouldBe true
                driver.scalarLong("SELECT version FROM manga WHERE id = 1") shouldBe 1_700_000_000_000
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

    private suspend fun SqlDriver.insertManga(
        id: Long,
        userFavoriteAt: Long?,
        favoriteModifiedAt: Long?,
        version: Long = 1,
    ) {
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
                $id, 1, '/manga/$id', 'Title $id', 0, 0,
                '{}', '', 0, 0,
                0, 1, 'https://example.com/cover.jpg',
                123, 0, $version, 0,
                ${userFavoriteAt ?: "NULL"}, ${favoriteModifiedAt ?: "NULL"}
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertChapter(id: Long, mangaId: Long) {
        execute(
            null,
            """
            INSERT INTO chapter(
                id, manga_id, remote_url, remote_name, remote_chapter_number, remote_order,
                remote_memo, user_read, user_bookmark, user_last_page_read, state_date_fetch,
                last_modified_at, version, is_syncing
            ) VALUES (
                $id, $mangaId, '/manga/$mangaId/chapter/$id', 'Chapter $id', 0, 0,
                '{}', 0, 0, 0, 0,
                0, 1, 0
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertCategory(id: Long) {
        execute(
            null,
            """
            INSERT INTO category(id, name, `order`, flags, hidden, version, uid, last_modified_at, is_syncing)
            VALUES ($id, 'Category $id', $id, 0, 0, 1, 0, 0, 0)
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
}
// KUN <--
