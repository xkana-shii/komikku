// KUN -->
package tachiyomi.data.manga

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedEntry
import kotlin.time.Instant

/**
 * The production driver is asynchronous: every [QueryResult] it returns is a
 * [QueryResult.AsyncValue], whose `value` getter throws and must be `await`ed instead. The rest of
 * the suite runs on the synchronous [JdbcSqliteDriver], where a stray `executeAsOne()` silently
 * works, so a synchronous call in a repository only fails on a device.
 *
 * [AsyncSqlDriver] keeps the real SQLite driver underneath but hands back asynchronous results, so
 * the merge entry point runs under the same contract as on device.
 */
class MangaMergeAsyncDriverTest {

    @Test
    fun `creating a merge on an asynchronous driver stores its settings and entries`() {
        runBlocking {
            val delegate = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            Database.Schema.create(delegate).await()
            val driver = AsyncSqlDriver(delegate)
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                delegate.insertManga(id = CHILD_ID, sourceId = SOURCE, url = "/child")

                val merged = repository.applyMerge(
                    staleMangaId = null,
                    mergedManga = Manga.create().copy(
                        source = MERGED_SOURCE,
                        url = "/merge",
                        favorite = true,
                        ogTitle = "Merged",
                        dateAdded = Instant.fromEpochSeconds(1000),
                    ),
                    categoryIds = emptyList(),
                    chapterSortMode = 2,
                    infoMangaId = null,
                    entries = listOf(entry()),
                )

                // Before the fix the settings lookup ran through `executeAsOneOrNull()` and threw
                // here, so no merge could ever be created.
                val settings = database.mergedQueries
                    .selectMerge(mangaId = merged.id, mapper = MergedMangaMapper::mapMergeSettings)
                    .awaitAsOneOrNull()
                settings.shouldNotBeNull()
                settings.chapterSortMode shouldBe 2
                repository.getEntriesById(merged.id).map { it.mangaId } shouldContainExactly listOf(CHILD_ID)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `reusing an existing merge row keeps its settings on an asynchronous driver`() {
        runBlocking {
            val delegate = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            Database.Schema.create(delegate).await()
            val driver = AsyncSqlDriver(delegate)
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                delegate.insertManga(id = CHILD_ID, sourceId = SOURCE, url = "/child")
                delegate.insertManga(id = CHILD_ID + 1, sourceId = SOURCE, url = "/child2")
                val manga = Manga.create().copy(
                    source = MERGED_SOURCE,
                    url = "/merge",
                    ogTitle = "Merged",
                )

                val first = repository.applyMerge(
                    null,
                    manga,
                    emptyList(),
                    chapterSortMode = 2,
                    infoMangaId = null,
                    entries = listOf(entry(CHILD_ID)),
                )
                // Same source and url resolve to the same manga row, so the second call reuses it.
                val second = repository.applyMerge(
                    null,
                    manga,
                    emptyList(),
                    chapterSortMode = 0,
                    infoMangaId = null,
                    entries = listOf(entry(CHILD_ID + 1)),
                )

                second.id shouldBe first.id
                val settings = database.mergedQueries
                    .selectMerge(mangaId = first.id, mapper = MergedMangaMapper::mapMergeSettings)
                    .awaitAsOneOrNull()
                settings.shouldNotBeNull()
                // The settings of an existing merge must survive a re-merge.
                settings.chapterSortMode shouldBe 2
            } finally {
                driver.close()
            }
        }
    }

    private fun entry(mangaId: Long = CHILD_ID) = MergedEntry(
        id = 0L,
        mergeId = 0L,
        mangaId = mangaId,
        fetchChapterUpdates = true,
        downloadChapters = false,
        chapterPriority = 1,
    )

    private suspend fun SqlDriver.insertManga(id: Long, sourceId: Long, url: String) {
        execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                remote_memo, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized, last_modified_at, version, is_syncing,
                user_favorite_at, favorite_modified_at
            ) VALUES (
                $id, $sourceId, '$url', 'Title', 0, 0,
                '{}', '', 0, 0,
                0, 1, 1, 1, 0,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    /**
     * Wraps a synchronous driver so that every result has to be awaited, the way the production
     * driver's results do. Both carries of [QueryResult] are honoured by `awaitAsOneOrNull`; only
     * `value` (what the synchronous accessors use) rejects [QueryResult.AsyncValue].
     */
    private class AsyncSqlDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> = QueryResult.AsyncValue {
            delegate.executeQuery(identifier, sql, mapper, parameters, binders).await()
        }

        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> = QueryResult.AsyncValue {
            delegate.execute(identifier, sql, parameters, binders).await()
        }

        override fun newTransaction(): QueryResult<Transacter.Transaction> = QueryResult.AsyncValue {
            delegate.newTransaction().await()
        }
    }

    private companion object {
        const val CHILD_ID = 7L
        const val SOURCE = 10L
        const val MERGED_SOURCE = 100L
    }
}
// KUN <--
