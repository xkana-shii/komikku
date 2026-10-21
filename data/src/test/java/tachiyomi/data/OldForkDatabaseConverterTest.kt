package tachiyomi.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.compat.FreshSchemaCapture
import tachiyomi.data.compat.OldForkDatabaseConverter
import tachiyomi.data.compat.RawSqlConnection
import tachiyomi.data.compat.SchemaObject
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.manga.model.Manga
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.time.Instant

// KNS -->
/**
 * An old personal-fork database (user_version 57, its own schema lineage) must open as a current
 * database with all user data, fork-owned state included, and with a schema identical to a fresh
 * install. Without the conversion the driver would see version 57 and run nothing.
 */
class OldForkDatabaseConverterTest {

    private val dbFile: Path = Files.createTempFile("kns-old-fork", ".db")
    private val opened = mutableListOf<AutoCloseable>()

    @AfterEach
    fun tearDown() {
        opened.asReversed().forEach { runCatching { it.close() } }
        Files.deleteIfExists(dbFile)
    }

    @Test
    fun `an old fork database is detected and converted to the current schema`() {
        val db = jdbc(dbFile)
        createOldForkDatabase(db)
        seed(db)
        val converter = OldForkDatabaseConverter(now = { NOW })

        converter.isOldForkLineage(db) shouldBe true

        val fresh = freshSchema()
        val report = converter.convert(db, fresh, Database.Schema.version)

        report.fromVersion shouldBe 57L
        report.toVersion shouldBe Database.Schema.version
        report.convertedFillermarks shouldBe 1
        report.foreignKeyViolations shouldBe 0
        report.droppedTables shouldContainExactlyInAnyOrder listOf(
            "library_visibility_generation",
            "library_visibility_revision",
            "manga_chapter_stats",
        )
        db.longValue("PRAGMA user_version") shouldBe Database.Schema.version
        converter.isOldForkLineage(db) shouldBe false

        // Same objects and column definitions as a fresh install.
        db.schemaSignature() shouldBe fresh.signature(freshConnection)
    }

    @Test
    fun `converted data reads back through the current repositories`() {
        runBlocking {
            val db = jdbc(dbFile)
            createOldForkDatabase(db)
            seed(db)
            // The old schema's own triggers moved these while seeding; the conversion must keep them.
            val oldMangaVersion = db.longValue("SELECT state_version FROM manga WHERE id = 1")
            val oldChapterVersion = db.longValue("SELECT state_version FROM chapter WHERE id = 1")
            val oldMangaModifiedAt = db.longValue("SELECT state_last_modified_at FROM manga WHERE id = 1")
            OldForkDatabaseConverter(now = { NOW }).convert(db, freshSchema(), Database.Schema.version)

            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbFile").also { opened += AutoCloseable(it::close) }
            val database = DatabaseBindings.providesDatabase(driver)

            val categories = CategoryRepositoryImpl(database).getAll().associateBy { it.id }
            categories.getValue(1).parentId shouldBe 0L
            categories.getValue(2).parentId shouldBe 1L
            categories.getValue(2).trackStatus shouldBe 1
            categories.getValue(2).updateInterval shouldBe 7
            categories.getValue(4).parentId shouldBe 3L
            categories.getValue(1).uid shouldBe Category.nameToUid("Shounen")
            categories.getValue(2).uid shouldBe Category.childUid(Category.nameToUid("Shounen"), "Reading")
            categories.getValue(4).uid shouldBe Category.childUid(Category.nameToUid("Josei"), "Reading")
            categories.getValue(2).uid shouldNotBe categories.getValue(4).uid
            categories.getValue(2).dateAdded shouldBe 1_650_000_000_000L
            categories.getValue(3).dateAdded shouldBe NOW

            val mangaRepository = MangaRepositoryImpl(database)
            val manga = mangaRepository.getMangaById(1)
            manga.favorite shouldBe true
            manga.dateAdded shouldBe Instant.fromEpochMilliseconds(1_600_000_000_000)
            manga.favoriteModifiedAt shouldBe 1_700_000_000L
            manga.version shouldBe oldMangaVersion
            manga.lastModifiedAt shouldBe oldMangaModifiedAt
            // Old upload-date sort (0x400) + ascending, plus a Fillermark filter bit (0x080) and the
            // number display mode, become Mihon's upload-date sort (0x200) + ascending + display.
            manga.chapterFlags shouldBe (
                Manga.CHAPTER_SORTING_UPLOAD_DATE or Manga.CHAPTER_SORT_ASC or
                    Manga.CHAPTER_DISPLAY_NUMBER
                )
            manga.scanlatorPriority shouldBe listOf("Alpha", "Beta")
            manga.scanlatorRangeRules shouldBe listOf("1.0:10.0:Alpha")
            manga.blacklistedChapters shouldContainExactlyInAnyOrder listOf(
                "url:/existing",
                chapterUrlBlacklistKey("/c2,special"),
            )
            manga.smartMergeOverride shouldBe null

            val notFavorite = mangaRepository.getMangaById(2)
            notFavorite.favorite shouldBe false
            notFavorite.favoriteModifiedAt shouldBe null

            val chapters = ChapterRepositoryImpl(database).getChapterByMangaId(1).associateBy { it.url }
            chapters.getValue("/c1").bookmark shouldBe true
            chapters.getValue("/c1").bookmarkColor shouldBe 3
            chapters.getValue("/c1").version shouldBe oldChapterVersion
            chapters.getValue("/c3").read shouldBe true
            chapters.size shouldBe 3

            driver.longValue("SELECT count(*) FROM manga_category") shouldBe 2L
            driver.longValue("SELECT last_modified_at FROM manga_category WHERE category_id = 2") shouldBe 77L
            driver.longValue("SELECT count(*) FROM history") shouldBe 1L
            driver.longValue("SELECT last_chapter_read FROM manga_track WHERE manga_id = 1") shouldBe 12L
            driver.longValue("SELECT count(*) FROM excluded_scanlator WHERE manga_id = 1") shouldBe 1L
            driver.longValue("SELECT is_syncing FROM manga WHERE id = 1") shouldBe 0L

            // The current triggers work on the converted data.
            driver.execute(null, "UPDATE chapter SET user_bookmark_color = 5 WHERE remote_url = '/c1'", 0)
            driver.longValue("SELECT version FROM chapter WHERE remote_url = '/c1'") shouldBe oldChapterVersion!! + 1
        }
    }

    @Test
    fun `a current database is not treated as an old fork database`() {
        val db = jdbc(dbFile)
        freshSchema(db)
        OldForkDatabaseConverter().isOldForkLineage(db) shouldBe false
    }

    @Test
    fun `a failed conversion leaves the old database untouched`() {
        val db = jdbc(dbFile)
        createOldForkDatabase(db)
        seed(db)
        // A target column that is required but has no old counterpart makes the rebuild fail
        // after several tables were already rebuilt in the same transaction.
        val broken = freshSchema().map {
            if (it.type == "table" && it.name == "history") {
                it.copy(sql = "CREATE TABLE history(id INTEGER NOT NULL PRIMARY KEY, required_without_source TEXT NOT NULL)")
            } else {
                it
            }
        }

        shouldThrow<Exception> {
            OldForkDatabaseConverter().convert(db, broken, Database.Schema.version)
        }

        db.longValue("PRAGMA user_version") shouldBe 57L
        OldForkDatabaseConverter().isOldForkLineage(db) shouldBe true
        db.longValue("SELECT count(*) FROM chapter") shouldBe 3L
        db.longValue("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name LIKE 'kns_old_%'") shouldBe 0L
    }

    // ---------------------------------------------------------------------------------------------

    private lateinit var freshConnection: RawSqlConnection

    private fun freshSchema(into: RawSqlConnection = jdbcMemory()): List<SchemaObject> {
        freshConnection = into
        return FreshSchemaCapture.capture(Database.Schema, into)
    }

    private fun createOldForkDatabase(db: RawSqlConnection) {
        val script = Files.readString(Path.of("src/test/resources/oldfork/old-fork-schema-57.sql"))
        script.split(Regex("\\n;;\\n"))
            .map { block -> block.lines().filterNot { it.startsWith("--") }.joinToString("\n").trim() }
            .filter { it.isNotEmpty() }
            .forEach { db.execute(it) }
    }

    private fun seed(db: RawSqlConnection) {
        listOf(
            "INSERT INTO category(id, name, `order`, flags, manga_order, hidden, parent_id, date_added, date_modified, track_status, update_interval) VALUES " +
                "(1, 'Shounen', 0, 0, '', 0, 0, 1650000000000, 0, NULL, NULL)," +
                "(2, 'Reading', 1, 0, '', 0, 1, 1650000000000, 0, 1, 7)," +
                "(3, 'Josei', 2, 0, '', 0, 0, 0, 0, NULL, NULL)," +
                "(4, 'Reading', 3, 0, '', 0, 3, 0, 0, 1, NULL)",
            "INSERT INTO manga(id, source_id, remote_url, remote_title, remote_status, user_favorite_at, state_initialized, " +
                "user_reader_flags, user_chapter_flags, state_cover_last_modified, state_date_added, state_last_modified_at, " +
                "state_version, user_blacklisted_chapters, user_scanlator_priority, user_scanlator_range_rules) VALUES " +
                "(1, 100, '/m1', 'Manga One', 1, 1700000000000, 1, 0, ${0x401 or 0x080 or 0x100000}, 0, 1600000000000, 1234, 5, " +
                "'url:/existing', 'Alpha, Beta', '1.0:10.0:Alpha')," +
                "(2, 100, '/m2', 'Manga Two', 1, NULL, 0, 0, 0, 0, 0, 0, 0, '', '', '')",
            "INSERT INTO chapter(id, manga_id, remote_url, remote_name, remote_scanlator, user_read, user_bookmark, user_fillermark, " +
                "user_last_page_read, remote_chapter_number, remote_order, state_date_fetch, remote_date_upload, state_last_modified_at, " +
                "state_version, user_bookmark_color) VALUES " +
                "(1, 1, '/c1', 'Chapter 1', 'Alpha', 0, 1, 0, 0, 1.0, 2, 1000, 900, 50, 9, 3)," +
                "(2, 1, '/c2,special', 'Extra', 'Alpha', 0, 0, 1, 0, 1.5, 1, 1000, 900, 50, 1, 0)," +
                "(3, 1, '/c3', 'Chapter 2', 'Beta', 1, 0, 0, 0, 2.0, 0, 1000, 900, 50, 1, 0)",
            "INSERT INTO manga_category(manga_id, category_id, state_last_modified_at) VALUES (1, 1, 0), (1, 2, 77)",
            "INSERT INTO history(id, manga_id, chapter_id, read_at, read_duration) VALUES (1, 1, 3, 5000, 60)",
            "INSERT INTO manga_track(id, manga_id, tracker_id, remote_id, library_id, title, last_chapter_read, total_chapters, " +
                "status, score, remote_url, start_date, finish_date) VALUES (1, 1, 2, 42, NULL, 'Manga One', 12, 100, 1, 8.0, 'u', 0, 0)",
            "INSERT INTO excluded_scanlator(manga_id, scanlator) VALUES (1, 'Gamma')",
        ).forEach { db.execute(it) }
    }

    private fun RawSqlConnection.schemaSignature(): Map<String, Any> {
        val objects = query(
            "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'",
        )
        return objects.associate { row ->
            val type = row[0] as String
            val name = row[1] as String
            "$type $name" to if (type == "table") {
                query("SELECT name, type, \"notnull\", dflt_value, pk FROM pragma_table_info('$name')")
            } else {
                (row[2] as String).replace(Regex("\\s+"), " ").trim()
            }
        }
    }

    private fun List<SchemaObject>.signature(connection: RawSqlConnection): Map<String, Any> =
        connection.schemaSignature().also { it.keys shouldBe map { o -> "${o.type} ${o.name}" }.toSet() }

    private fun jdbc(path: Path): RawSqlConnection =
        JdbcRawSql(DriverManager.getConnection("jdbc:sqlite:$path").also { opened += it })

    private fun jdbcMemory(): RawSqlConnection =
        JdbcRawSql(DriverManager.getConnection("jdbc:sqlite::memory:").also { opened += it })

    private fun RawSqlConnection.longValue(sql: String): Long? = query(sql).firstOrNull()?.firstOrNull() as Long?

    private fun JdbcSqliteDriver.longValue(sql: String): Long? =
        executeQuery(null, sql, { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value

    private class JdbcRawSql(private val connection: Connection) : RawSqlConnection {
        override fun execute(sql: String) {
            connection.createStatement().use { it.execute(sql) }
        }

        override fun query(sql: String): List<List<Any?>> {
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    val count = rs.metaData.columnCount
                    val rows = mutableListOf<List<Any?>>()
                    while (rs.next()) {
                        rows += (1..count).map { index ->
                            when (val value = rs.getObject(index)) {
                                is Int -> value.toLong()
                                is Short -> value.toLong()
                                is Float -> value.toDouble()
                                else -> value
                            }
                        }
                    }
                    return rows
                }
            }
        }
    }

    private companion object {
        const val NOW = 1_760_000_000_000L
    }
}
// KNS <--
