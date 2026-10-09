package tachiyomi.data.manga

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Chapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Manga
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter

// KMK -->
class LibraryChapterFlagsTest {
    private lateinit var driver: SqlDriver
    private lateinit var db: Database
    private lateinit var repository: MangaRepositoryImpl

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver).await()
        db = Database(
            driver,
            historyAdapter = History.Adapter(read_atAdapter = DateColumnAdapter),
            mangaAdapter = Manga.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
            chapterAdapter = Chapter.Adapter(remote_memoAdapter = MemoColumnAdapter),
        )
        repository = MangaRepositoryImpl(db)
    }

    @AfterEach
    fun tearDown() = driver.close()

    @Test
    fun `bulk update changes favorites with different flags and preserves non library rows and triggers`() = runBlocking<Unit> {
        insert(1, favorite = true, flags = 7)
        insert(2, favorite = false, flags = 123)
        insert(3, favorite = true, flags = 0)
        val outsideBefore = row(2)
        var invalidations = 0
        val favorites = db.mangaQueries.getFavorites()
        val listener = Query.Listener { invalidations++ }
        favorites.addListener(listener)
        try {
            repository.updateLibraryChapterFlags(1049257) shouldBe true
        } finally {
            favorites.removeListener(listener)
        }
        for (id in listOf(1L, 3L)) {
            val updated = row(id)
            updated[0] shouldBe 1049257L
            updated[1] shouldBe 1L
            (updated[2] > 0L) shouldBe true // Existing last_modified_at trigger still runs.
            updated[3] shouldBe 23L // Viewer settings are unrelated.
            updated[4] shouldBe 0L // Chapter settings must not change the sync version.
        }
        row(2) shouldBe outsideBefore
        invalidations shouldBe 1
    }

    @Test
    fun `empty library is a successful no op`() = runBlocking<Unit> {
        insert(1, favorite = false, flags = 9)
        val before = row(1)
        repository.updateLibraryChapterFlags(42) shouldBe true
        row(1) shouldBe before
    }

    @Test
    fun `database failure returns false and rolls back the whole bulk update`() = runBlocking<Unit> {
        insert(1, favorite = true, flags = 7)
        insert(2, favorite = true, flags = 9)
        val before = listOf(row(1), row(2))
        driver.execute(
            null,
            """
            CREATE TRIGGER reject_chapter_flags BEFORE UPDATE OF user_chapter_flags ON manga
            WHEN new.id = 2
            BEGIN SELECT RAISE(ABORT, 'test failure'); END;
            """.trimIndent(),
            0,
        )
        repository.updateLibraryChapterFlags(42) shouldBe false
        listOf(row(1), row(2)) shouldBe before
    }

    private fun insert(id: Long, favorite: Boolean, flags: Long) {
        // Deliberately undecodable memo: updating flags must never materialize Manga objects.
        driver.execute(
            null,
            """
            INSERT INTO manga(id, source_id, remote_url, remote_title, remote_status, user_favorite_at, state_initialized,
                user_reader_flags, user_chapter_flags, state_cover_last_modified, state_date_added, remote_memo)
            VALUES ($id, 1, '/$id', 'Series', 0, ${if (favorite) 0 else "NULL"}, 1,
                23, $flags, 0, 0, CAST('not JSON' AS BLOB));
            """.trimIndent(),
            0,
        )
    }

    private fun row(id: Long): List<Long> = driver.executeQuery(
        null,
        "SELECT user_chapter_flags, user_favorite_at IS NOT NULL, state_last_modified_at, user_reader_flags, state_version FROM manga WHERE id = $id",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(List(5) { cursor.getLong(it)!! })
        },
        parameters = 0,
    ).value
}
// KMK <--
