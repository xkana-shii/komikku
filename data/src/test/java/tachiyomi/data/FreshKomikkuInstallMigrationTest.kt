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
 * Komikku creates `mangas_categories` without `last_modified_at` on a fresh install; only migration
 * 28 adds the column to databases that upgraded through it. 49.sqm has to migrate both shapes, so the
 * column is dropped from the `49.db` snapshot here to reproduce a database that was installed fresh
 * on Komikku and then went through 47.sqm/48.sqm.
 */
class FreshKomikkuInstallMigrationTest {

    @Test
    fun `migration from version 49 works without mangas_categories last_modified_at`() {
        runBlocking {
            val dbFile = Files.createTempFile("komikku-fresh-install-migration-test", ".db")
            Files.copy(Path.of("src/main/sqldelight/49.db"), dbFile, REPLACE_EXISTING)
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
            try {
                // The old schema's triggers would otherwise fire while seeding
                listOf(
                    "update_last_modified_at_mangas", "update_manga_version", "update_last_modified_at_chapters",
                    "update_chapter_and_manga_version", "update_category_version", "insert_category_uid",
                    "system_category_delete_trigger", "update_last_modified_at_mangas_categories",
                    "insert_manga_category_update_version", "delete_manga_category_update_version",
                    "update_last_favorited_at_mangas",
                ).forEach { driver.execute(null, "DROP TRIGGER IF EXISTS $it", 0).await() }
                driver.execute(null, "ALTER TABLE mangas_categories DROP COLUMN last_modified_at", 0).await()
                driver.columnCount("mangas_categories", "last_modified_at") shouldBe 0

                listOf(
                    "INSERT OR REPLACE INTO categories(_id,name,sort,flags,manga_order,hidden,version,uid,last_modified_at,is_syncing) " +
                        "VALUES (0,'',-1,0,'',0,0,0,0,0),(1,'Reading',0,0,'',0,0,777,0,0)",
                    "INSERT INTO mangas(_id,source,url,artist,author,description,genre,title,status,thumbnail_url,favorite,last_update,next_update,initialized,viewer,chapter_flags,cover_last_modified,date_added,update_strategy,calculate_interval,last_modified_at,favorite_modified_at,version,is_syncing,notes,memo) " +
                        "VALUES (1,100,'https://a',NULL,NULL,NULL,NULL,'Manga A',0,NULL,1,0,0,1,0,0,0,1000,0,0,0,NULL,0,0,'','{}')",
                    "INSERT INTO mangas_categories(_id,manga_id,category_id) VALUES (1,1,1)",
                ).forEach { driver.execute(null, it, 0).await() }

                Database.Schema.migrate(driver, 49L, Database.Schema.version).await()

                driver.columnCount("manga_category", "last_modified_at") shouldBe 1
                driver.scalarLong(
                    "SELECT count(*) FROM manga_category WHERE manga_id = 1 AND category_id = 1",
                ) shouldBe 1
                driver.scalarLong("SELECT last_modified_at FROM manga_category") shouldBe 0
            } finally {
                driver.close()
            }
        }
    }

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value

    private fun SqlDriver.columnCount(table: String, column: String): Long =
        scalarLong("SELECT count(*) FROM pragma_table_info('$table') WHERE name = '$column'") ?: -1
}
// KNS <--
