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
 * Komi-kun #80's schema changes arrive here as 61.sqm (library removals), 62.sqm (merge-deduped views)
 * and 63.sqm (merged → merge + merged_entry), after this fork's own shipped 59 and 60. An install on
 * version 61 (the last one this fork shipped before) keeps all of its data through them.
 */
class KomikunPr80MigrationTest {

    @Test
    fun `an install on version 61 keeps its data through 61 to 63`() {
        runBlocking {
            val dbFile = Files.createTempFile("kns-pr80-migration", ".db")
            Files.copy(Path.of("src/main/sqldelight/55.db"), dbFile, REPLACE_EXISTING)
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
            try {
                // The schema this fork shipped last (user_version 61)
                Database.Schema.migrate(driver, 55L, 61L).await()
                seedVersion61(driver)

                Database.Schema.migrate(driver, 61L, Database.Schema.version).await()

                // Colored bookmarks, reading progress, blacklist, scanlator settings, edited details
                driver.long("SELECT user_bookmark_color FROM chapter WHERE id = 1") shouldBe 4L
                driver.long("SELECT user_read FROM chapter WHERE id = 2") shouldBe 1L
                driver.long("SELECT user_last_page_read FROM chapter WHERE id = 3") shouldBe 7L
                driver.text("SELECT user_blacklisted_chapters FROM manga WHERE id = 1") shouldBe "url:/c9"
                driver.text("SELECT user_scanlator_priority FROM manga WHERE id = 1") shouldBe "Kaizen, Myth Toons"
                driver.text("SELECT user_scanlator_range_rules FROM manga WHERE id = 1") shouldBe "6.0:8.0:Myth Toons"
                driver.long("SELECT user_smart_merge_override FROM manga WHERE id = 1") shouldBe 0L
                driver.text("SELECT user_title FROM manga WHERE id = 1") shouldBe "My title"
                driver.long("SELECT count(*) FROM excluded_scanlator WHERE manga_id = 1") shouldBe 1L
                driver.long("SELECT count(*) FROM manga_category WHERE manga_id = 1") shouldBe 1L
                driver.long("SELECT read_at FROM history WHERE chapter_id = 2") shouldBe 5000L

                // The merge: sort mode from the self-reference, info entry, one entry per child
                driver.long("SELECT chapter_sort_mode FROM merge WHERE manga_id = 10") shouldBe 1L
                driver.long("SELECT info_manga_id FROM merge WHERE manga_id = 10") shouldBe 2L
                driver.long("SELECT count(*) FROM merged_entry WHERE merge_id = 10") shouldBe 2L
                driver.long("SELECT count(*) FROM merged_entry WHERE merge_id = 10 AND manga_id = 10") shouldBe 0L
                driver.long("SELECT chapter_priority FROM merged_entry WHERE merge_id = 10 AND manga_id = 1") shouldBe 0L
                driver.long("SELECT chapter_priority FROM merged_entry WHERE merge_id = 10 AND manga_id = 2") shouldBe 1L

                // The views still answer for both kinds of entry, with the bookmark color
                driver.long("SELECT totalCount FROM libraryView WHERE id = 1") shouldBe 3L
                driver.long("SELECT count(*) FROM libraryView WHERE id = 10") shouldBe 1L
                driver.long("SELECT bookmarkColor FROM updatesView WHERE chapterId = 1 AND mangaId = 1") shouldBe 4L

                // Removing an entry from the library leaves a removal tombstone; adding it back clears it
                driver.execute(null, "UPDATE manga SET user_favorite_at = NULL WHERE id = 2", 0).await()
                driver.long("SELECT count(*) FROM library_removal WHERE manga_id = 2") shouldBe 1L
                driver.execute(null, "UPDATE manga SET user_favorite_at = 3000 WHERE id = 2", 0).await()
                driver.long("SELECT count(*) FROM library_removal WHERE manga_id = 2") shouldBe 0L

                // The version trigger still bumps for this fork's columns and for a cleared description
                val version = driver.long("SELECT version FROM manga WHERE id = 1")!!
                driver.execute(null, "UPDATE manga SET user_scanlator_priority = 'Myth Toons' WHERE id = 1", 0).await()
                driver.long("SELECT version FROM manga WHERE id = 1") shouldBe version + 1
                driver.execute(null, "UPDATE manga SET remote_description = NULL WHERE id = 1", 0).await()
                driver.long("SELECT version FROM manga WHERE id = 1") shouldBe version + 2

                driver.long("SELECT count(*) FROM pragma_foreign_key_check") shouldBe 0L
            } finally {
                driver.close()
                Files.deleteIfExists(dbFile)
            }
        }
    }

    private suspend fun seedVersion61(driver: SqlDriver) {
        listOf(
            "INSERT INTO category(id, name, `order`, flags, hidden) VALUES (1, 'Reading', 0, 0, 0)",
            "INSERT INTO manga(id, source_id, remote_url, remote_title, remote_description, remote_status, " +
                "remote_update_strategy, remote_memo, user_favorite_at, user_notes, user_reader_flags, " +
                "user_chapter_flags, state_chapter_fetch_interval, state_initialized, user_blacklisted_chapters, " +
                "user_scanlator_priority, user_scanlator_range_rules, user_smart_merge_override, user_title) VALUES " +
                "(1, 100, '/m1', 'One', 'About', 0, 0, X'7B7D', 1000, '', 0, 0, 0, 1, 'url:/c9', " +
                "'Kaizen, Myth Toons', '6.0:8.0:Myth Toons', 0, 'My title')," +
                "(2, 100, '/m2', 'Two', NULL, 0, 0, X'7B7D', 1000, '', 0, 0, 0, 1, '', '', '', NULL, NULL)," +
                "(10, 6969, '/m1', 'Merged', NULL, 0, 0, X'7B7D', 1000, '', 0, 0, 0, 1, '', '', '', NULL, NULL)",
            "INSERT INTO chapter(id, manga_id, remote_url, remote_name, remote_scanlator, remote_chapter_number, " +
                "remote_order, remote_memo, user_read, user_bookmark, user_bookmark_color, user_last_page_read, " +
                "state_date_fetch) VALUES " +
                "(1, 1, '/c1', 'Ch. 1', 'Kaizen', 1.0, 2, X'7B7D', 0, 1, 4, 0, 2000)," +
                "(2, 1, '/c2', 'Ch. 2', 'Kaizen', 2.0, 1, X'7B7D', 1, 0, 0, 0, 2000)," +
                "(3, 1, '/c3', 'Ch. 3', 'Myth Toons', 3.0, 0, X'7B7D', 0, 0, 0, 7, 2000)," +
                "(4, 2, '/d1', 'Ch. 1', 'Other', 1.0, 0, X'7B7D', 0, 0, 0, 0, 2000)",
            "INSERT INTO excluded_scanlator(manga_id, scanlator) VALUES (1, 'Unknown')",
            "INSERT INTO manga_category(manga_id, category_id) VALUES (1, 1)",
            "INSERT INTO history(manga_id, chapter_id, read_at, read_duration) VALUES (1, 2, 5000, 60)",
            // The merge: self-reference with the sort mode, the info entry, and one child listed twice
            "INSERT INTO merged(_id, info_manga, get_chapter_updates, chapter_sort_mode, chapter_priority, " +
                "download_chapters, merge_id, merge_url, manga_id, manga_url, manga_source) VALUES " +
                "(1, 0, 1, 1, 0, 1, 10, '/m1', 10, '/m1', 6969)," +
                "(2, 0, 1, 0, 0, 1, 10, '/m1', 1, '/m1', 100)," +
                "(3, 1, 1, 0, 1, 1, 10, '/m1', 2, '/m2', 100)," +
                "(4, 0, 0, 0, 5, 0, 10, '/m1', 1, '/m1', 100)",
        ).forEach { driver.execute(null, it, 0).await() }
    }

    private fun SqlDriver.long(sql: String): Long? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value

    private fun SqlDriver.text(sql: String): String? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        }, 0).value
}
// KNS <--
