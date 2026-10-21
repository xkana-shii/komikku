package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

// KNS -->
/**
 * 58.sqm recreates updatesView with the chapter's bookmark color. A database that is already at
 * version 58 (old view, no bookmarkColor) must get the new view and keep every row and color.
 */
class UpdatesViewMigrationTest {

    @Test
    fun `migration 58 adds bookmarkColor to updatesView without touching data`() {
        runBlocking {
            val dbFile = Files.createTempFile("updates-view-migration", ".db")
            Files.copy(Path.of("src/main/sqldelight/55.db"), dbFile, REPLACE_EXISTING)
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
            try {
                Database.Schema.migrate(driver, 55L, 58L).await()
                driver.hasColumn("updatesView", "bookmarkColor") shouldBe false

                listOf(
                    "INSERT INTO manga(id, source_id, remote_url, remote_title, remote_status, remote_update_strategy, " +
                        "remote_memo, user_favorite_at, user_notes, user_reader_flags, user_chapter_flags, " +
                        "state_chapter_fetch_interval, state_initialized) " +
                        "VALUES (1, 1, '/m', 'M', 0, 0, X'7B7D', 1000, '', 0, 0, 0, 1)",
                    "INSERT INTO chapter(id, manga_id, remote_url, remote_name, remote_chapter_number, remote_order, " +
                        "remote_memo, user_read, user_bookmark, user_last_page_read, remote_date_upload, " +
                        "state_date_fetch, user_bookmark_color) " +
                        "VALUES (1, 1, '/c', 'C', 1.0, 0, X'7B7D', 0, 1, 0, 1500, 2000, 3)",
                ).forEach { driver.execute(null, it, 0).await() }
                val versionBefore = driver.long("SELECT version FROM chapter WHERE id = 1")

                Database.Schema.migrate(driver, 58L, Database.Schema.version).await()

                driver.hasColumn("updatesView", "bookmarkColor") shouldBe true
                driver.long("SELECT bookmarkColor FROM updatesView WHERE chapterId = 1") shouldBe 3L
                driver.long("SELECT count(*) FROM chapter") shouldBe 1L
                driver.long("SELECT user_bookmark_color FROM chapter WHERE id = 1") shouldBe 3L
                driver.long("SELECT version FROM chapter WHERE id = 1") shouldBe versionBefore
            } finally {
                driver.close()
                Files.deleteIfExists(dbFile)
            }
        }
    }

    private fun SqlDriver.long(sql: String): Long? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value

    private fun SqlDriver.hasColumn(table: String, column: String): Boolean =
        long("SELECT count(*) FROM pragma_table_info('$table') WHERE name = '$column'") == 1L
}
// KNS <--
