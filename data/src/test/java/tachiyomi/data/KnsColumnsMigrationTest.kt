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
 * 57.sqm adds the fork-owned columns. Existing rows must get the neutral defaults (root category, no
 * smart binding, ordinary bookmark color, empty blacklist/scanlator rules, global Smart Merge) and
 * the recreated sync triggers must bump versions for the new state, so it reaches other devices.
 */
class KnsColumnsMigrationTest {

    @Test
    fun `migration 57 adds fork columns with neutral defaults and versioned triggers`() {
        runBlocking {
            val dbFile = Files.createTempFile("kns-columns-migration", ".db")
            Files.copy(Path.of("src/main/sqldelight/55.db"), dbFile, REPLACE_EXISTING)
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
            try {
                listOf(
                    "INSERT INTO category(id, name, `order`, flags, hidden) VALUES (1, 'Reading', 0, 0, 0)",
                    "INSERT INTO manga(id, source_id, remote_url, remote_title, remote_status, remote_update_strategy, " +
                        "remote_memo, user_favorite_at, user_notes, user_reader_flags, user_chapter_flags, " +
                        "state_chapter_fetch_interval, state_initialized) " +
                        "VALUES (1, 1, '/m', 'M', 0, 0, X'7B7D', 1000, '', 0, 0, 0, 1)",
                    "INSERT INTO chapter(id, manga_id, remote_url, remote_name, remote_chapter_number, remote_order, " +
                        "remote_memo, user_read, user_bookmark, user_last_page_read, state_date_fetch) " +
                        "VALUES (1, 1, '/c', 'C', 1.0, 0, X'7B7D', 0, 1, 0, 0)",
                ).forEach { driver.execute(null, it, 0).await() }

                Database.Schema.migrate(driver, 55L, Database.Schema.version).await()

                driver.long("SELECT parent_id FROM category WHERE id = 1") shouldBe 0L
                driver.long("SELECT track_status IS NULL AND update_interval IS NULL FROM category WHERE id = 1") shouldBe 1L
                driver.long("SELECT user_bookmark_color FROM chapter WHERE id = 1") shouldBe 0L
                driver.long(
                    "SELECT user_blacklisted_chapters = '' AND user_scanlator_priority = '' AND " +
                        "user_scanlator_range_rules = '' AND user_smart_merge_override IS NULL FROM manga WHERE id = 1",
                ) shouldBe 1L

                val chapterVersion = driver.long("SELECT version FROM chapter WHERE id = 1")!!
                driver.execute(null, "UPDATE chapter SET user_bookmark_color = 2 WHERE id = 1", 0).await()
                driver.long("SELECT version FROM chapter WHERE id = 1") shouldBe chapterVersion + 1

                val mangaVersion = driver.long("SELECT version FROM manga WHERE id = 1")!!
                driver.execute(null, "UPDATE manga SET user_blacklisted_chapters = 'url:/c' WHERE id = 1", 0).await()
                driver.long("SELECT version FROM manga WHERE id = 1") shouldBe mangaVersion + 1
                driver.execute(null, "UPDATE manga SET user_smart_merge_override = 1 WHERE id = 1", 0).await()
                driver.long("SELECT version FROM manga WHERE id = 1") shouldBe mangaVersion + 2

                val categoryVersion = driver.long("SELECT version FROM category WHERE id = 1")!!
                driver.execute(null, "UPDATE category SET track_status = 1 WHERE id = 1", 0).await()
                driver.long("SELECT version FROM category WHERE id = 1") shouldBe categoryVersion + 1
                driver.execute(null, "UPDATE category SET parent_id = 0, update_interval = 3 WHERE id = 1", 0).await()
                driver.long("SELECT version FROM category WHERE id = 1") shouldBe categoryVersion + 2
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
}
// KNS <--
