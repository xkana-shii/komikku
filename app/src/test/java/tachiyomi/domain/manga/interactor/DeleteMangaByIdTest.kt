package tachiyomi.domain.manga.interactor

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.domain.sync.MangaTombstoneRecorder

/**
 * A local manga delete records the tombstone the next SyncYomi v2 upload sends. The row may already
 * be gone by the time the delete runs (two delete paths, a stale list), and a missing row has no key
 * left to record, so it must not fail the delete either.
 *
 * The repository is the real one over an in-memory database; only the recorder is a stand-in (a
 * production `fun interface`).
 */
class DeleteMangaByIdTest {

    @Test
    fun `a missing row is skipped instead of throwing`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            val interactor = DeleteMangaById(fixture.mangaRepository, fixture.recorder)

            // must not throw: the row was deleted by another path before this one ran
            interactor.await(404L)

            fixture.recorded.shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a live row is deleted and its key recorded`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 3L, url = "/m")
            val interactor = DeleteMangaById(fixture.mangaRepository, fixture.recorder)

            interactor.await(7L)

            fixture.mangaIds().shouldBeEmpty()
            fixture.recorded shouldBe listOf(3L to "/m")
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val database: Database = DatabaseBindings.providesDatabase(driver)

        val mangaRepository = MangaRepositoryImpl(database)
        val recorded = mutableListOf<Pair<Long, String>>()
        val recorder = MangaTombstoneRecorder { sourceId, url -> recorded += sourceId to url }

        init {
            runBlocking {
                driver.execute(null, "PRAGMA foreign_keys = ON", 0).await()
                Database.Schema.create(driver).await()
            }
        }

        fun close() = driver.close()

        fun mangaIds(): List<Long> = driver.executeQuery(
            null,
            "SELECT id FROM manga ORDER BY id",
            { cursor ->
                val values = mutableListOf<Long>()
                while (cursor.next().value) values += cursor.getLong(0)!!
                QueryResult.Value<List<Long>>(values.toList())
            },
            0,
        ).value

        suspend fun insertManga(id: Long, sourceId: Long, url: String) {
            driver.execute(
                null,
                """
                INSERT INTO manga(
                    id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                    remote_memo, user_notes, user_reader_flags, user_chapter_flags,
                    state_chapter_fetch_interval, state_initialized, remote_cover,
                    state_cover_last_modified, last_modified_at, version, is_syncing,
                    user_favorite_at, favorite_modified_at
                ) VALUES (
                    $id, $sourceId, '$url', 'Title', 0, 0,
                    '{}', '', 0, 0,
                    0, 1, NULL,
                    123, 10, 1, 0,
                    NULL, NULL
                )
                """.trimIndent(),
                0,
            ).await()
        }
    }
}
