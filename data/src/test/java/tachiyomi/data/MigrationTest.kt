package tachiyomi.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Tests the legacy-to-current database migration.
 *
 * The repository contains a version-28 database snapshot.
 * The duplicate-manga consolidation happens in migration 47.
 *
 * To exercise that consolidation, migrate the snapshot up to
 * version 47, insert representative legacy records, and then
 * run the remaining migrations.
 *
 * The migration runs inside a transaction so SQLite TEMP
 * tables remain available throughout the migration.
 */
class MigrationTest {

    @Test
    fun `migration from legacy schema preserves SyncYomi columns triggers and rows`() = runBlocking<Unit> {
        val dbFile = Files.createTempFile(
            "komikku-migration-test",
            ".db",
        )

        try {
            Files.copy(
                Path.of("src/main/sqldelight/28.db"),
                dbFile,
                REPLACE_EXISTING,
            )

            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile")

            driver.use { driver ->
                // Build the schema immediately before 47.sqm,
                // which performs duplicate-manga consolidation.
                migrateInTransaction(
                    driver = driver,
                    oldVersion = 28L,
                    newVersion = 47L,
                )

                // Insert legacy rows before the consolidation.
                populateOldSchema(driver)

                // Migration 47 uses temporary tables such as
                // duplicate_mangas. The enclosing transaction
                // keeps the JDBC connection consistent.
                migrateInTransaction(
                    driver = driver,
                    oldVersion = 47L,
                    newVersion = Database.Schema.version,
                )

                // -------------------------------------------------
                // SyncYomi columns
                // -------------------------------------------------

                driver.columnCount(
                    "manga",
                    "version",
                ) shouldBe 1L

                driver.columnCount(
                    "manga",
                    "is_syncing",
                ) shouldBe 1L

                driver.columnCount(
                    "manga",
                    "last_modified_at",
                ) shouldBe 1L

                driver.columnCount(
                    "chapter",
                    "version",
                ) shouldBe 1L

                driver.columnCount(
                    "chapter",
                    "is_syncing",
                ) shouldBe 1L

                driver.columnCount(
                    "chapter",
                    "last_modified_at",
                ) shouldBe 1L

                driver.columnCount(
                    "category",
                    "version",
                ) shouldBe 1L

                driver.columnCount(
                    "category",
                    "is_syncing",
                ) shouldBe 1L

                driver.columnCount(
                    "category",
                    "last_modified_at",
                ) shouldBe 1L

                driver.columnCount(
                    "manga_category",
                    "last_modified_at",
                ) shouldBe 1L

                // -------------------------------------------------
                // SyncYomi triggers
                // -------------------------------------------------

                listOf(
                    "update_last_modified_at_mangas",
                    "update_manga_version",
                    "update_last_modified_at_chapters",
                    "update_chapter_and_manga_version",
                    "update_category_version",
                    "insert_category_uid",
                ).forEach { trigger ->
                    driver.triggerExists(trigger) shouldBe true
                }

                // -------------------------------------------------
                // Komikku and TachiyomiSY tables
                // -------------------------------------------------

                listOf(
                    "merged",
                    "search_metadata",
                    "search_tags",
                    "search_titles",
                    "libraryUpdateError",
                    "saved_search",
                    "feed_saved_search",
                ).forEach { table ->
                    driver.tableExists(table) shouldBe true
                }

                // The temporary merge tables must not remain
                // after migration 47 finishes.
                listOf(
                    "duplicate_mangas",
                    "duplicate_chapters",
                    "duplicate_tracks",
                ).forEach { table ->
                    driver.tableExists(table) shouldBe false
                }

                // -------------------------------------------------
                // Database views
                // -------------------------------------------------

                listOf(
                    "historyView",
                    "libraryView",
                    "updatesView",
                    "libraryUpdateErrorView",
                ).forEach { view ->
                    driver.viewExists(view) shouldBe true
                }

                // -------------------------------------------------
                // Manga duplicate consolidation
                // -------------------------------------------------

                // Four original rows:
                // 1: favorite manga A
                // 2: distinct manga B
                // 3: duplicate manga A
                // 4: duplicate manga A
                //
                // After migration, rows 1 and 2 remain.
                driver.scalarLong(
                    "SELECT count(*) FROM manga",
                ) shouldBe 2L

                driver.scalarLong(
                    "SELECT count(*) FROM manga WHERE id = 1",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT count(*) FROM manga WHERE id = 2",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT count(*) FROM manga WHERE id IN (3, 4)",
                ) shouldBe 0L

                // favorite_modified_at takes precedence over
                // date_added when creating user_favorite_at.
                driver.scalarLong(
                    "SELECT user_favorite_at FROM manga WHERE id = 1",
                ) shouldBe 500L

                driver.scalarLong(
                    "SELECT version FROM manga WHERE id = 1",
                ) shouldBe 3L

                driver.scalarLong(
                    "SELECT last_modified_at FROM manga WHERE id = 1",
                ) shouldBe 700L

                // -------------------------------------------------
                // Chapter preservation
                // -------------------------------------------------

                // Chapters from deleted duplicates are assigned
                // to the surviving manga.
                driver.scalarLong(
                    "SELECT count(*) FROM chapter",
                ) shouldBe 3L

                driver.scalarLong(
                    "SELECT count(*) FROM chapter WHERE manga_id = 1",
                ) shouldBe 3L

                driver.scalarLong(
                    "SELECT count(*) FROM chapter WHERE manga_id != 1",
                ) shouldBe 0L

                driver.scalarLong(
                    "SELECT version FROM chapter WHERE id = 1",
                ) shouldBe 2L

                // -------------------------------------------------
                // Reading history
                // -------------------------------------------------

                driver.scalarLong(
                    "SELECT count(*) FROM history",
                ) shouldBe 3L

                driver.scalarLong(
                    "SELECT count(*) FROM history WHERE manga_id != 1",
                ) shouldBe 0L

                driver.scalarLong(
                    "SELECT read_at FROM history WHERE id = 1",
                ) shouldBe 111L

                driver.scalarLong(
                    "SELECT read_duration FROM history WHERE id = 1",
                ) shouldBe 222L

                driver.scalarLong(
                    "SELECT read_at FROM history WHERE id = 2",
                ) shouldBe 333L

                // -------------------------------------------------
                // Tracker preservation
                // -------------------------------------------------

                driver.scalarLong(
                    "SELECT count(*) FROM manga_track",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT tracker_id FROM manga_track WHERE id = 1",
                ) shouldBe 2L

                driver.scalarLong(
                    "SELECT manga_id FROM manga_track WHERE id = 1",
                ) shouldBe 1L

                // -------------------------------------------------
                // Category preservation
                // -------------------------------------------------

                driver.scalarLong(
                    "SELECT count(*) FROM category",
                ) shouldBe 2L

                driver.scalarLong(
                    "SELECT count(*) FROM category WHERE id = 1",
                ) shouldBe 1L

                // SyncYomi category metadata is introduced by
                // migration 57. The old category fixture does
                // not contain a historical version counter.
                driver.scalarLong(
                    "SELECT version FROM category WHERE id = 1",
                ) shouldBe 0L

                (
                    (
                        driver.scalarLong(
                            "SELECT last_modified_at FROM category WHERE id = 1",
                        ) ?: 0L
                        ) > 0L
                    ) shouldBe true

                // Multiple assignments to the same category
                // collapse to one assignment on the survivor.
                driver.scalarLong(
                    "SELECT count(*) FROM manga_category",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT manga_id FROM manga_category",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT category_id FROM manga_category",
                ) shouldBe 1L

                // Keep the latest modification timestamp among
                // the duplicate assignments.
                driver.scalarLong(
                    "SELECT last_modified_at FROM manga_category",
                ) shouldBe 999L

                // -------------------------------------------------
                // Merged manga references
                // -------------------------------------------------

                driver.scalarLong(
                    "SELECT count(*) FROM merged",
                ) shouldBe 2L

                driver.scalarLong(
                    "SELECT manga_id FROM merged WHERE _id = 1",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT merge_id FROM merged WHERE _id = 1",
                ) shouldBe 2L

                driver.scalarLong(
                    "SELECT manga_id FROM merged WHERE _id = 2",
                ) shouldBe 2L

                driver.scalarLong(
                    "SELECT merge_id FROM merged WHERE _id = 2",
                ) shouldBe 1L

                // -------------------------------------------------
                // Search metadata
                // -------------------------------------------------

                driver.scalarLong(
                    "SELECT count(*) FROM search_metadata",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT manga_id FROM search_metadata",
                ) shouldBe 1L

                driver.scalarLong(
                    """
                    SELECT count(*)
                    FROM search_metadata
                    WHERE uploader = 'up3'
                    """.trimIndent(),
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT count(*) FROM search_tags",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT manga_id FROM search_tags",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT count(*) FROM search_titles",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT manga_id FROM search_titles",
                ) shouldBe 1L

                // -------------------------------------------------
                // Library update errors
                // -------------------------------------------------

                driver.scalarLong(
                    "SELECT count(*) FROM libraryUpdateError",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT manga_id FROM libraryUpdateError",
                ) shouldBe 1L

                // -------------------------------------------------
                // Other references
                // -------------------------------------------------

                driver.scalarLong(
                    "SELECT count(*) FROM excluded_scanlator",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT manga_id FROM excluded_scanlator",
                ) shouldBe 1L

                driver.scalarLong(
                    "SELECT count(*) FROM source",
                ) shouldBe 1L

                // -------------------------------------------------
                // Foreign-key integrity
                // -------------------------------------------------

                driver.foreignKeyViolations() shouldBe 0L

                driver.scalarLong(
                    """
                    SELECT count(*)
                    FROM libraryUpdateError
                    WHERE manga_id NOT IN (
                        SELECT id FROM manga
                    )
                    """.trimIndent(),
                ) shouldBe 0L

                driver.scalarLong(
                    """
                    SELECT count(*)
                    FROM search_metadata
                    WHERE manga_id NOT IN (
                        SELECT id FROM manga
                    )
                    """.trimIndent(),
                ) shouldBe 0L

                // -------------------------------------------------
                // Trigger behavior after migration
                // -------------------------------------------------

                driver.execute(
                    null,
                    """
                    UPDATE manga
                    SET remote_description = 'changed'
                    WHERE id = 1
                    """.trimIndent(),
                    0,
                ).await()

                // The timestamp trigger should still work.
                driver.scalarLong(
                    "SELECT last_modified_at FROM manga WHERE id = 1",
                ) shouldBe driver.scalarLong(
                    "SELECT strftime('%s', 'now')",
                )

                // The version counter should increment once.
                driver.scalarLong(
                    "SELECT version FROM manga WHERE id = 1",
                ) shouldBe 4L
            }
        } finally {
            Files.deleteIfExists(dbFile)
        }
    }

    /**
     * SQLDelight migration 47 creates TEMP tables and refers to
     * them in subsequent statements. They must exist on the
     * same SQLite connection until migration completes.
     */
    private suspend fun migrateInTransaction(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
    ) {
        // SQLDelight manages commit, rollback, and the JDBC connection.
        testDatabase(driver).transaction {
            Database.Schema.migrate(
                driver = driver,
                oldVersion = oldVersion,
                newVersion = newVersion,
            ).await()
        }
    }

    /**
     * Seeds the old plural-table schema before migration 47.
     */

    private suspend fun populateOldSchema(driver: SqlDriver) {
        // Disable legacy triggers while inserting the fixtures
        // so historical timestamps and versions stay unchanged.
        listOf(
            "update_last_modified_at_mangas",
            "update_manga_version",
            "update_last_modified_at_chapters",
            "update_chapter_and_manga_version",
            "update_category_version",
            "insert_category_uid",
            "system_category_delete_trigger",
            "update_last_modified_at_mangas_categories",
            "insert_manga_category_update_version",
            "delete_manga_category_update_version",
            "update_last_favorited_at_mangas",
        ).forEach { trigger ->
            driver.execute(
                null,
                "DROP TRIGGER IF EXISTS $trigger",
                0,
            ).await()
        }

        val statements = listOf(
            // -------------------------------------------------
            // Legacy categories
            // -------------------------------------------------
            """
        INSERT OR REPLACE INTO categories(
            _id, name, sort, flags, manga_order, hidden
        )
        VALUES
            (0, '', -1, 0, '', 0),
            (1, 'Reading', 0, 0, '', 0)
            """.trimIndent(),

            // -------------------------------------------------
            // Legacy manga entries
            // -------------------------------------------------
            """
        INSERT INTO mangas(
            _id, source, url, artist, author,
            description, genre, title, status,
            thumbnail_url, favorite,
            last_update, next_update, initialized,
            viewer, chapter_flags, cover_last_modified,
            date_added, update_strategy,
            calculate_interval, last_modified_at,
            favorite_modified_at, version,
            is_syncing, notes, memo
        )
        VALUES
            (
                1, 100, 'https://a', 'art',
                'auth', 'desc', '["g"]', 'Manga A',
                1, 'https://cover', 1, 10, 20,
                1, 0, 0, 30, 1000, 1, 0,
                700, 500, 3, 0, 'note1', '{}'
            ),
            (
                2, 100, 'https://b', NULL,
                NULL, NULL, NULL, 'Manga B',
                0, NULL, 0, NULL, NULL,
                0, 0, 0, 0, 0, 0, 0,
                0, NULL, 0, 0, '', '{}'
            ),
            (
                3, 100, 'https://a', NULL,
                NULL, NULL, NULL, 'Manga A dup',
                0, NULL, 0, NULL, NULL,
                0, 0, 0, 0, 0, 0, 0,
                0, NULL, 0, 0, '', '{}'
            ),
            (
                4, 100, 'https://a', NULL,
                NULL, NULL, NULL, 'Manga A dup 2',
                0, NULL, 0, NULL, NULL,
                0, 0, 0, 0, 0, 0, 0,
                0, NULL, 0, 0, '', '{}'
            )
            """.trimIndent(),

            // -------------------------------------------------
            // Chapters owned by duplicate entries
            //
            // The legacy schema requires fillermark.
            // Set it to 0 for these ordinary chapters.
            // -------------------------------------------------
            """
        INSERT INTO chapters(
            _id, manga_id, url, name, scanlator,
            read, bookmark, fillermark, last_page_read,
            chapter_number, source_order, date_fetch,
            date_upload, last_modified_at,
            version, is_syncing, memo
        )
        VALUES
            (
                1, 1, 'https://c1', 'Ch 1',
                NULL, 1, 0, 0, 5, 1.0, 1,
                100, 200, 800, 2, 0, '{}'
            ),
            (
                2, 3, 'https://c3', 'Ch 3',
                NULL, 0, 0, 0, 0, 1.0, 1,
                101, 201, 801, 1, 0, '{}'
            ),
            (
                3, 4, 'https://c4', 'Ch 4',
                NULL, 0, 0, 0, 0, 1.0, 1,
                102, 202, 802, 1, 0, '{}'
            )
            """.trimIndent(),

            // -------------------------------------------------
            // Category assignments
            // -------------------------------------------------
            """
        INSERT INTO mangas_categories(
            _id, manga_id, category_id, last_modified_at
        )
        VALUES
            (1, 1, 1, 999),
            (2, 3, 1, 555),
            (3, 4, 1, 777)
            """.trimIndent(),

            // -------------------------------------------------
            // Tracker binding
            // -------------------------------------------------
            """
        INSERT INTO manga_sync(
            _id, manga_id, sync_id, remote_id,
            library_id, title, last_chapter_read,
            total_chapters, status, score,
            remote_url, start_date, finish_date,
            private
        )
        VALUES (
            1, 1, 2, 4242, 99, 'Tracked',
            5.0, 10, 1, 8.0,
            'https://t', 1, 2, 0
        )
            """.trimIndent(),

            // -------------------------------------------------
            // Reading history
            // -------------------------------------------------
            """
        INSERT INTO history(
            _id, chapter_id, last_read, time_read
        )
        VALUES
            (1, 1, 111, 222),
            (2, 2, 333, 444),
            (3, 3, 555, 666)
            """.trimIndent(),

            // -------------------------------------------------
            // Excluded scanlators
            // -------------------------------------------------
            """
        INSERT INTO excluded_scanlators(
            manga_id, scanlator
        )
        VALUES
            (1, 'excl'),
            (3, 'excl')
            """.trimIndent(),

            // -------------------------------------------------
            // Search metadata
            // -------------------------------------------------
            """
        INSERT INTO search_metadata(
            manga_id, uploader, extra,
            indexed_extra, extra_version
        )
        VALUES
            (3, 'up3', '{}', 'idx3', 1),
            (4, 'up4', '{}', 'idx4', 1)
            """.trimIndent(),

            """
        INSERT INTO search_tags(
            _id, manga_id, namespace, name, type
        )
        VALUES (
            1, 3, 'ns', 'tag', 1
        )
            """.trimIndent(),

            """
        INSERT INTO search_titles(
            _id, manga_id, title, type
        )
        VALUES (
            1, 3, 'Alt', 1
        )
            """.trimIndent(),

            // -------------------------------------------------
            // Library update errors
            // -------------------------------------------------
            """
        INSERT INTO libraryUpdateErrorMessage(
            _id, message
        )
        VALUES (
            1, 'err'
        )
            """.trimIndent(),

            """
        INSERT INTO libraryUpdateError(
            _id, manga_id, message_id, last_update
        )
        VALUES
            (1, 3, 1, 5),
            (2, 4, 1, 6)
            """.trimIndent(),

            // -------------------------------------------------
            // Merged manga references
            // -------------------------------------------------
            """
        INSERT INTO merged(
            _id, info_manga, get_chapter_updates,
            chapter_sort_mode, chapter_priority,
            download_chapters, merge_id, merge_url,
            manga_id, manga_url, manga_source
        )
        VALUES
            (
                1, 0, 0, 0, 0, 0,
                2, 'https://b',
                3, 'https://a', 100
            ),
            (
                2, 0, 0, 0, 0, 0,
                3, 'https://a',
                2, 'https://b', 100
            )
            """.trimIndent(),

            // -------------------------------------------------
            // Sources
            // -------------------------------------------------
            """
        INSERT INTO sources(
            _id, lang, name
        )
        VALUES (
            100, 'en', 'Src'
        )
            """.trimIndent(),

            // -------------------------------------------------
            // Saved searches
            // -------------------------------------------------
            """
        INSERT INTO saved_search(
            _id, source, name, query, filters_json
        )
        VALUES (
            1, 100, 's', 'q', NULL
        )
            """.trimIndent(),

            // -------------------------------------------------
            // Saved-search feed
            // -------------------------------------------------
            """
        INSERT INTO feed_saved_search(
            _id, source, saved_search,
            global, feed_order
        )
        VALUES (
            1, 100, 1, 0, 1
        )
            """.trimIndent(),
        )

        // Identify the exact failing fixture if another SQL
        // constraint or schema mismatch appears.
        statements.forEachIndexed { index, statement ->
            try {
                driver.execute(
                    identifier = null,
                    sql = statement,
                    parameters = 0,
                ).await()
            } catch (e: Exception) {
                throw IllegalStateException(
                    buildString {
                        appendLine("Legacy migration fixture failed.")
                        appendLine("Statement: ${index + 1}/${statements.size}")
                        appendLine("SQL:")
                        appendLine(statement)
                    },
                    e,
                )
            }
        }
    }

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                QueryResult.Value(
                    if (cursor.next().value) {
                        cursor.getLong(0)
                    } else {
                        null
                    },
                )
            },
            parameters = 0,
        ).value

    private fun SqlDriver.columnCount(
        table: String,
        column: String,
    ): Long = scalarLong(
        """
        SELECT count(*)
        FROM pragma_table_info('$table')
        WHERE name = '$column'
        """.trimIndent(),
    ) ?: -1L

    private fun SqlDriver.triggerExists(
        name: String,
    ): Boolean = (
        scalarLong(
            """
            SELECT count(*)
            FROM sqlite_master
            WHERE type = 'trigger'
              AND name = '$name'
            """.trimIndent(),
        ) ?: 0L
        ) > 0L

    private fun SqlDriver.tableExists(
        name: String,
    ): Boolean = (
        scalarLong(
            """
            SELECT count(*)
            FROM sqlite_master
            WHERE type = 'table'
              AND name = '$name'
            """.trimIndent(),
        ) ?: 0L
        ) > 0L

    private fun SqlDriver.viewExists(
        name: String,
    ): Boolean = (
        scalarLong(
            """
            SELECT count(*)
            FROM sqlite_master
            WHERE type = 'view'
              AND name = '$name'
            """.trimIndent(),
        ) ?: 0L
        ) > 0L

    private fun SqlDriver.foreignKeyViolations(): Long =
        executeQuery(
            identifier = null,
            sql = "PRAGMA foreign_key_check",
            mapper = { cursor ->
                var count = 0L

                while (cursor.next().value) {
                    count++
                }

                QueryResult.Value(count)
            },
            parameters = 0,
        ).value
}
