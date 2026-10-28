package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.manga.MangaRepositoryImpl
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Guards the destructive schema rework: the migration from the pre-rework schema (version 49, snapshot
 * in `src/main/sqldelight/49.db`) must keep SyncYomi's version/is_syncing/last_modified_at columns and
 * triggers, keep Komikku's extra tables, and carry representative rows across.
 *
 * The seed deliberately contains the shapes that used to break the duplicate-manga merge: a removed
 * member that owns a chapter with history, a `merged` row that references the removed member, and
 * members of the same group sharing a category with different timestamps.
 */
class MigrationTest {

    @Test
    fun `migration from version 49 preserves SyncYomi columns, triggers and rows`() {
        runBlocking {
            val dbFile = Files.createTempFile("komikku-migration-test", ".db")
            Files.copy(Path.of("src/main/sqldelight/49.db"), dbFile, REPLACE_EXISTING)
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
            try {
                populateOldSchema(driver)

                Database.Schema.migrate(driver, 49L, Database.Schema.version).await()

                // SyncYomi columns survive on the renamed tables
                driver.columnCount("manga", "version") shouldBe 1
                driver.columnCount("manga", "is_syncing") shouldBe 1
                driver.columnCount("manga", "last_modified_at") shouldBe 1
                driver.columnCount("chapter", "version") shouldBe 1
                driver.columnCount("chapter", "is_syncing") shouldBe 1
                driver.columnCount("chapter", "last_modified_at") shouldBe 1
                driver.columnCount("category", "version") shouldBe 1
                driver.columnCount("category", "is_syncing") shouldBe 1
                driver.columnCount("category", "last_modified_at") shouldBe 1
                driver.columnCount("manga_category", "last_modified_at") shouldBe 1

                // SyncYomi triggers survive
                driver.triggerExists("update_last_modified_at_mangas") shouldBe true
                driver.triggerExists("update_manga_version") shouldBe true
                val versionTrigger = driver.scalarString(
                    "SELECT sql FROM sqlite_master WHERE type = 'trigger' AND name = 'update_manga_version'",
                )
                versionTrigger?.contains("remote_cover") shouldBe false
                versionTrigger?.contains("new.is_syncing = 0") shouldBe true
                driver.triggerExists("update_last_modified_at_chapters") shouldBe true
                driver.triggerExists("update_chapter_and_manga_version") shouldBe true
                driver.triggerExists("update_category_version") shouldBe true
                driver.triggerExists("insert_category_uid") shouldBe true

                // Komikku/SY extra tables survive
                // KUN -->
                // `merged` was split into `merge` + `merged_entry` by 61.sqm.
                driver.tableExists("merged") shouldBe false
                driver.tableExists("merge") shouldBe true
                driver.tableExists("merged_entry") shouldBe true
                // KUN <--
                driver.tableExists("search_metadata") shouldBe true
                driver.tableExists("search_tags") shouldBe true
                driver.tableExists("search_titles") shouldBe true
                driver.tableExists("libraryUpdateError") shouldBe true
                driver.tableExists("saved_search") shouldBe true
                driver.tableExists("feed_saved_search") shouldBe true

                // The merge scratch tables are TEMP and never leak into the persisted schema
                driver.tableExists("duplicate_mangas") shouldBe false
                driver.tableExists("duplicate_chapters") shouldBe false
                driver.tableExists("duplicate_tracks") shouldBe false

                // The rebuilt views survive
                driver.viewExists("historyView") shouldBe true
                driver.viewExists("libraryView") shouldBe true
                driver.viewExists("updatesView") shouldBe true
                driver.viewExists("libraryUpdateErrorView") shouldBe true

                // The user's edited details live on the manga row (SY 992d7972f7)
                driver.columnCount("manga", "user_title") shouldBe 1
                driver.columnCount("manga", "user_author") shouldBe 1
                driver.columnCount("manga", "user_artist") shouldBe 1
                driver.columnCount("manga", "user_thumbnail_url") shouldBe 1
                driver.columnCount("manga", "user_description") shouldBe 1
                driver.columnCount("manga", "user_genre") shouldBe 1
                driver.columnCount("manga", "user_status") shouldBe 1

                // The explicitly-columned views were recreated with the new columns
                driver.columnCount("updatesView", "userTitle") shouldBe 1
                driver.columnCount("updatesView", "userThumbnailUrl") shouldBe 1
                driver.columnCount("historyView", "userTitle") shouldBe 1
                driver.columnCount("historyView", "userThumbnailUrl") shouldBe 1
                driver.columnCount("libraryView", "user_title") shouldBe 1

                // Manga rows survive (the triplicate group merged into the favorite entry, Manga B kept)
                driver.scalarLong("SELECT count(*) FROM manga") shouldBe 2
                driver.scalarLong("SELECT user_favorite_at FROM manga WHERE id = 1") shouldBe 1000
                driver.scalarLong("SELECT version FROM manga WHERE id = 1") shouldBe 3
                driver.scalarLong("SELECT last_modified_at FROM manga WHERE id = 1") shouldBe 700
                driver.scalarLong("SELECT count(*) FROM manga WHERE id = 2") shouldBe 1

                // The removed members' chapters were re-pointed onto the survivor before the copy
                driver.scalarLong("SELECT count(*) FROM chapter") shouldBe 3
                driver.scalarLong("SELECT count(*) FROM chapter WHERE manga_id != 1") shouldBe 0
                driver.scalarLong("SELECT version FROM chapter WHERE id = 1") shouldBe 2

                // History follows its chapter, so the removed members' history survives too
                driver.scalarLong("SELECT count(*) FROM history") shouldBe 3
                driver.scalarLong("SELECT count(*) FROM history WHERE manga_id != 1") shouldBe 0
                driver.scalarLong("SELECT read_at FROM history WHERE id = 1") shouldBe 111
                driver.scalarLong("SELECT read_duration FROM history WHERE id = 1") shouldBe 222
                driver.scalarLong("SELECT read_at FROM history WHERE id = 2") shouldBe 333

                // Track, category and extras carry over
                driver.scalarLong("SELECT count(*) FROM manga_track") shouldBe 1
                driver.scalarLong("SELECT tracker_id FROM manga_track WHERE id = 1") shouldBe 2
                driver.scalarLong("SELECT count(*) FROM category") shouldBe 2
                driver.scalarLong("SELECT last_modified_at FROM category WHERE id = 1") shouldBe 1234

                // Members sharing a category collapse into one row; the newest timestamp wins
                driver.scalarLong("SELECT count(*) FROM manga_category") shouldBe 1
                driver.scalarLong("SELECT manga_id FROM manga_category") shouldBe 1
                driver.scalarLong("SELECT category_id FROM manga_category") shouldBe 1
                driver.scalarLong("SELECT last_modified_at FROM manga_category") shouldBe 999

                // The entry that referenced a removed member follows it onto the survivor
                // KUN -->
                // The split keeps one `merge` row per merge and one `merged_entry` row per child.
                driver.scalarLong("SELECT count(*) FROM merge") shouldBe 2
                driver.scalarLong("SELECT count(*) FROM merged_entry") shouldBe 2
                driver.scalarLong("SELECT manga_id FROM merged_entry WHERE _id = 1") shouldBe 1
                driver.scalarLong("SELECT merge_id FROM merged_entry WHERE _id = 1") shouldBe 2
                driver.scalarLong("SELECT manga_id FROM merged_entry WHERE _id = 2") shouldBe 2
                driver.scalarLong("SELECT merge_id FROM merged_entry WHERE _id = 2") shouldBe 1
                // No `manga_source = 6969` self-reference in this seed, so there is no sort mode and
                // no info entry to carry over.
                driver.scalarLong("SELECT chapter_sort_mode FROM merge WHERE manga_id = 2") shouldBe 0
                driver.scalarLong("SELECT count(*) FROM merge WHERE info_manga_id IS NOT NULL") shouldBe 0
                // KUN <--

                // The unique-keyed extras collapse to one row per surviving entry before the re-point
                driver.scalarLong("SELECT count(*) FROM search_metadata") shouldBe 1
                driver.scalarLong("SELECT manga_id FROM search_metadata") shouldBe 1
                // The best-ranked member that had a row (manga 3) is the one that survives
                driver.scalarLong("SELECT count(*) FROM search_metadata WHERE uploader = 'up3'") shouldBe 1
                driver.scalarLong("SELECT count(*) FROM search_tags") shouldBe 1
                driver.scalarLong("SELECT manga_id FROM search_tags") shouldBe 1
                driver.scalarLong("SELECT count(*) FROM search_titles") shouldBe 1
                driver.scalarLong("SELECT manga_id FROM search_titles") shouldBe 1
                driver.scalarLong("SELECT count(*) FROM libraryUpdateError") shouldBe 1
                driver.scalarLong("SELECT manga_id FROM libraryUpdateError") shouldBe 1

                // The non-unique extras are re-pointed rather than duplicated
                driver.scalarLong("SELECT count(*) FROM excluded_scanlator") shouldBe 1
                driver.scalarLong("SELECT manga_id FROM excluded_scanlator") shouldBe 1
                driver.scalarLong("SELECT count(*) FROM source") shouldBe 1

                // Nothing points at a row that the merge removed
                driver.foreignKeyViolations() shouldBe 0
                driver.scalarLong(
                    "SELECT count(*) FROM libraryUpdateError WHERE manga_id NOT IN (SELECT id FROM manga)",
                ) shouldBe 0
                driver.scalarLong(
                    "SELECT count(*) FROM search_metadata WHERE manga_id NOT IN (SELECT id FROM manga)",
                ) shouldBe 0

                // The recreated SyncYomi trigger still stamps last_modified_at on update
                driver.execute(null, "UPDATE manga SET remote_description = 'changed' WHERE id = 1", 0).await()
                ((driver.scalarLong("SELECT last_modified_at FROM manga WHERE id = 1") ?: 0) > 0) shouldBe true
                (driver.scalarLong("SELECT version FROM manga WHERE id = 1") ?: 0) shouldBe 4

                // A raw cover-only change must NOT bump version: routine CDN URL churn would
                // otherwise inflate versions and overwrite genuine edits on other devices.
                val coverLastModifiedBefore =
                    driver.scalarLong("SELECT state_cover_last_modified FROM manga WHERE id = 1")
                driver.execute(null, "UPDATE manga SET remote_cover = 'https://new-cover' WHERE id = 1", 0).await()
                (driver.scalarLong("SELECT version FROM manga WHERE id = 1") ?: 0) shouldBe 4
                driver.scalarLong("SELECT state_cover_last_modified FROM manga WHERE id = 1") shouldBe
                    coverLastModifiedBefore

                // The stale-cover recovery path bumps version explicitly exactly once, so the new
                // URL still wins version-based reconciliation, while the local Coil timestamp
                // stays untouched.
                val currentCover = driver.scalarString("SELECT remote_cover FROM manga WHERE id = 1")!!
                MangaRepositoryImpl(DatabaseBindings.providesDatabase(driver))
                    .updateCover(1, currentCover, "https://recovered-cover") shouldBe true
                (driver.scalarLong("SELECT version FROM manga WHERE id = 1") ?: 0) shouldBe 5
                driver.scalarString("SELECT remote_cover FROM manga WHERE id = 1") shouldBe
                    "https://recovered-cover"
                driver.scalarLong("SELECT state_cover_last_modified FROM manga WHERE id = 1") shouldBe
                    coverLastModifiedBefore

                // An unrelated update keeps the version unchanged.
                driver.execute(null, "UPDATE manga SET remote_title = 'renamed' WHERE id = 1", 0).await()
                (driver.scalarLong("SELECT version FROM manga WHERE id = 1") ?: 0) shouldBe 5

                // The upgraded rows can hold the user's edited details, and the recreated views
                // expose them next to the source values.
                driver.execute(null, "UPDATE manga SET user_title = 'Edited' WHERE id = 1", 0).await()
                driver.scalarString("SELECT user_title FROM manga WHERE id = 1") shouldBe "Edited"
                driver.scalarString("SELECT remote_title FROM manga WHERE id = 1") shouldBe "renamed"
                driver.scalarString("SELECT userTitle FROM historyView WHERE mangaId = 1 LIMIT 1") shouldBe
                    "Edited"
                // A custom-info edit is not a whole-row edit: the version must not move.
                (driver.scalarLong("SELECT version FROM manga WHERE id = 1") ?: 0) shouldBe 5
            } finally {
                driver.close()
            }
        }
    }

    /**
     * The pre-split `merged` table was keyed on `_id` alone, so one child could be listed twice under
     * the same merge entry. The split's `merged_entry` has `UNIQUE(merge_id, manga_id)`, which turned
     * such a pair into an aborted upgrade (SY 9955747216).
     */
    @Test
    fun `migration from version 49 keeps one merged_entry per merge and manga pair`() {
        runBlocking {
            val dbFile = Files.createTempFile("komikku-migration-merged-entry-test", ".db")
            Files.copy(Path.of("src/main/sqldelight/49.db"), dbFile, REPLACE_EXISTING)
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
            try {
                populateOldSchema(driver)
                // A merge parent and its child, neither a duplicate of anything in the seed, so the
                // group merge cannot collapse the pair on its own.
                driver.execute(
                    null,
                    "INSERT INTO mangas(_id,source,url,title,status,favorite,initialized,viewer,chapter_flags,cover_last_modified,date_added) " +
                        "VALUES (50,6969,'https://merge50','Merge 50',0,1,1,0,0,0,1100)," +
                        "(51,100,'https://child51','Child 51',0,1,1,0,0,0,1101)",
                    0,
                ).await()
                // The same pair twice, with different entry settings: `_id` 10 is the older row.
                driver.execute(
                    null,
                    "INSERT INTO merged(_id,info_manga,get_chapter_updates,chapter_sort_mode,chapter_priority,download_chapters,merge_id,merge_url,manga_id,manga_url,manga_source) " +
                        "VALUES (10,0,1,0,5,0,50,'https://merge50',51,'https://child51',100)," +
                        "(11,0,0,0,9,1,50,'https://merge50',51,'https://child51',100)",
                    0,
                ).await()

                Database.Schema.migrate(driver, 49L, Database.Schema.version).await()

                // The upgrade completes and keeps exactly one row for the duplicated pair
                driver.scalarLong("SELECT count(*) FROM merged_entry WHERE merge_id = 50 AND manga_id = 51") shouldBe 1
                // ... the one from the lowest `_id`, carrying that row's own entry settings (the new
                // table allocates fresh ids, so `_id` itself cannot be checked here)
                driver.scalarLong("SELECT chapter_priority FROM merged_entry WHERE merge_id = 50 AND manga_id = 51") shouldBe 5
                driver.scalarLong("SELECT download_chapters FROM merged_entry WHERE merge_id = 50 AND manga_id = 51") shouldBe 0
                // The merge row itself is still built from the pair's merge id
                driver.scalarLong("SELECT count(*) FROM merge WHERE manga_id = 50") shouldBe 1
                driver.foreignKeyViolations() shouldBe 0
            } finally {
                driver.close()
            }
        }
    }

    // KUN -->
    /**
     * Migration from version 49 for a pre-split merge.
     *
     * The `merged` self-reference (`manga_source = 6969`) carried the merge-level chapter-sort mode
     * and the info flag; `61.sqm` moves both to the `merge` row and must not keep the self-reference
     * as a child entry — the new `merged_entry` has no such row on a live database either, and the
     * restore path refuses to create one.
     */
    @Test
    fun `migration from version 49 carries the self-reference settings to the merge row`() {
        runBlocking {
            val dbFile = Files.createTempFile("komikku-migration-merge-settings-test", ".db")
            Files.copy(Path.of("src/main/sqldelight/49.db"), dbFile, REPLACE_EXISTING)
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
            try {
                populateOldSchema(driver)
                // A merge parent with two children and the legacy self-reference row.
                driver.execute(
                    null,
                    "INSERT INTO mangas(_id,source,url,title,status,favorite,initialized,viewer,chapter_flags,cover_last_modified,date_added) " +
                        "VALUES (60,6969,'https://merge60','Merge 60',0,1,1,0,0,0,1200)," +
                        "(61,100,'https://child61','Child 61',0,1,1,0,0,0,1201)," +
                        "(62,100,'https://child62','Child 62',0,1,1,0,0,0,1202)",
                    0,
                ).await()
                driver.execute(
                    null,
                    "INSERT INTO merged(_id,info_manga,get_chapter_updates,chapter_sort_mode,chapter_priority,download_chapters,merge_id,merge_url,manga_id,manga_url,manga_source) " +
                        // The self-reference: the only row that carries the sort mode.
                        "VALUES (20,0,0,2,0,0,60,'https://merge60',60,'https://merge60',6969)," +
                        // The info entry is a child row, flagged here.
                        "(21,1,1,0,1,0,60,'https://merge60',61,'https://child61',100)," +
                        "(22,0,1,0,2,0,60,'https://merge60',62,'https://child62',100)",
                    0,
                ).await()

                Database.Schema.migrate(driver, 49L, Database.Schema.version).await()

                // The merge-level settings moved off the self-reference onto the merge row.
                driver.scalarLong("SELECT chapter_sort_mode FROM merge WHERE manga_id = 60") shouldBe 2
                driver.scalarLong("SELECT info_manga_id FROM merge WHERE manga_id = 60") shouldBe 61
                // Both children survive as entries...
                driver.scalarLong("SELECT count(*) FROM merged_entry WHERE merge_id = 60") shouldBe 2
                driver.scalarLong(
                    "SELECT count(*) FROM merged_entry WHERE merge_id = 60 AND manga_id IN (61, 62)",
                ) shouldBe 2
                // ...and the self-reference is not one of them: no merge lists itself.
                driver.scalarLong("SELECT count(*) FROM merged_entry WHERE merge_id = 60 AND manga_id = 60") shouldBe 0
                driver.scalarLong("SELECT count(*) FROM merged_entry WHERE merge_id = manga_id") shouldBe 0
                driver.foreignKeyViolations() shouldBe 0
            } finally {
                driver.close()
            }
        }
    }
    // KUN <--

    private fun populateOldSchema(driver: SqlDriver) = runBlocking {
        // The old schema's triggers would otherwise bump version/last_modified_at while seeding
        listOf(
            "update_last_modified_at_mangas", "update_manga_version", "update_last_modified_at_chapters",
            "update_chapter_and_manga_version", "update_category_version", "insert_category_uid",
            "system_category_delete_trigger", "update_last_modified_at_mangas_categories",
            "insert_manga_category_update_version", "delete_manga_category_update_version",
            "update_last_favorited_at_mangas",
        ).forEach { driver.execute(null, "DROP TRIGGER IF EXISTS $it", 0).await() }

        val statements = listOf(
            "INSERT OR REPLACE INTO categories(_id,name,sort,flags,manga_order,hidden,version,uid,last_modified_at,is_syncing) " +
                "VALUES (0,'',-1,0,'',0,0,0,0,0),(1,'Reading',0,0,'',0,5,777,1234,0)",
            // Three entries share (source, url): the favorite one is the keeper, the other two are removed.
            "INSERT INTO mangas(_id,source,url,artist,author,description,genre,title,status,thumbnail_url,favorite,last_update,next_update,initialized,viewer,chapter_flags,cover_last_modified,date_added,update_strategy,calculate_interval,last_modified_at,favorite_modified_at,version,is_syncing,notes,memo) " +
                "VALUES (1,100,'https://a','art','auth','desc','[\"g\"]','Manga A',1,'https://cover',1,10,20,1,0,0,30,1000,1,0,700,500,3,0,'note1','{}')," +
                "(2,100,'https://b',NULL,NULL,NULL,NULL,'Manga B',0,NULL,0,NULL,NULL,0,0,0,0,0,0,0,0,NULL,0,0,'','{}')," +
                "(3,100,'https://a',NULL,NULL,NULL,NULL,'Manga A dup',0,NULL,0,NULL,NULL,0,0,0,0,0,0,0,0,NULL,0,0,'','{}')," +
                "(4,100,'https://a',NULL,NULL,NULL,NULL,'Manga A dup 2',0,NULL,0,NULL,NULL,0,0,0,0,0,0,0,0,NULL,0,0,'','{}')",
            // The removed members own chapters (and history) that have to move onto the keeper.
            "INSERT INTO chapters(_id,manga_id,url,name,scanlator,read,bookmark,last_page_read,chapter_number,source_order,date_fetch,date_upload,last_modified_at,version,is_syncing,memo) " +
                "VALUES (1,1,'https://c1','Ch 1',NULL,1,0,5,1.0,1,100,200,800,2,0,'{}')," +
                "(2,3,'https://c3','Ch 3',NULL,0,0,0,1.0,1,101,201,801,1,0,'{}')," +
                "(3,4,'https://c4','Ch 4',NULL,0,0,0,1.0,1,102,202,802,1,0,'{}')",
            // Every member shares category 1 but with a different last_modified_at.
            "INSERT INTO mangas_categories(_id,manga_id,category_id,last_modified_at) " +
                "VALUES (1,1,1,999),(2,3,1,555),(3,4,1,777)",
            "INSERT INTO manga_sync(_id,manga_id,sync_id,remote_id,library_id,title,last_chapter_read,total_chapters,status,score,remote_url,start_date,finish_date,private) " +
                "VALUES (1,1,2,4242,99,'Tracked',5.0,10,1,8.0,'https://t',1,2,0)",
            "INSERT INTO history(_id,chapter_id,last_read,time_read) VALUES (1,1,111,222),(2,2,333,444),(3,3,555,666)",
            "INSERT INTO excluded_scanlators(manga_id,scanlator) VALUES (1,'excl'),(3,'excl')",
            // The unique-keyed extras only exist on the removed members, so one of them has to survive.
            "INSERT INTO search_metadata(manga_id,uploader,extra,indexed_extra,extra_version) " +
                "VALUES (3,'up3','{}','idx3',1),(4,'up4','{}','idx4',1)",
            "INSERT INTO search_tags(_id,manga_id,namespace,name,type) VALUES (1,3,'ns','tag',1)",
            "INSERT INTO search_titles(_id,manga_id,title,type) VALUES (1,3,'Alt',1)",
            "INSERT INTO libraryUpdateErrorMessage(_id,message) VALUES (1,'err')",
            "INSERT INTO libraryUpdateError(_id,manga_id,message_id,last_update) VALUES (1,3,1,5),(2,4,1,6)",
            // Both a child (manga_id) and a merged entry (merge_id) point at removed members.
            "INSERT INTO merged(_id,info_manga,get_chapter_updates,chapter_sort_mode,chapter_priority,download_chapters,merge_id,merge_url,manga_id,manga_url,manga_source) " +
                "VALUES (1,0,0,0,0,0,2,'https://b',3,'https://a',100)," +
                "(2,0,0,0,0,0,3,'https://a',2,'https://b',100)",
            "INSERT INTO sources(_id,lang,name) VALUES (100,'en','Src')",
            "INSERT INTO saved_search(_id,source,name,query,filters_json) VALUES (1,100,'s','q',NULL)",
            "INSERT INTO feed_saved_search(_id,source,saved_search,global,feed_order) VALUES (1,100,1,0,1)",
        )
        statements.forEach { driver.execute(null, it, 0).await() }
    }

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value

    private fun SqlDriver.scalarString(sql: String): String? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        }, 0).value

    private fun SqlDriver.columnCount(table: String, column: String): Long =
        scalarLong("SELECT count(*) FROM pragma_table_info('$table') WHERE name = '$column'") ?: -1

    private fun SqlDriver.triggerExists(name: String): Boolean =
        (scalarLong("SELECT count(*) FROM sqlite_master WHERE type = 'trigger' AND name = '$name'") ?: 0) > 0

    private fun SqlDriver.tableExists(name: String): Boolean =
        (scalarLong("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'") ?: 0) > 0

    private fun SqlDriver.viewExists(name: String): Boolean =
        (scalarLong("SELECT count(*) FROM sqlite_master WHERE type = 'view' AND name = '$name'") ?: 0) > 0

    private fun SqlDriver.foreignKeyViolations(): Long =
        executeQuery(null, "PRAGMA foreign_key_check", { cursor ->
            var count = 0L
            while (cursor.next().value) {
                count++
            }
            QueryResult.Value(count)
        }, 0).value
}
