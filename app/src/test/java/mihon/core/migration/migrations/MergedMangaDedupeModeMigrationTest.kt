// KUN -->
package mihon.core.migration.migrations

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.core.migration.MigrationContext
import org.junit.jupiter.api.Test
import tachiyomi.data.DatabaseBindings
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.model.MergeSettings

/**
 * This fork dropped upstream's "No dedupe" mode (Komikku #1194), so every stored sort mode has to
 * move down by one: the old `2` (PRIORITY) means MOST_CHAPTERS today, `1` (NO_DEDUPE) means
 * PRIORITY, and `0` stays NONE.
 *
 * The mode lives on the merge parent's `merge` row. The entries' children are ordinary mangas with
 * no settings row at all, so iterating them — upstream SY's `getMergedManga()` lists exactly those —
 * silently skipped every merge and shipped the shift as a no-op. The migration runs against a real
 * database here so the parent/child distinction is the one the SQL makes.
 */
class MergedMangaDedupeModeMigrationTest {

    @Test
    fun `the migration renumbers every merge parent's sort mode and keeps its info entry`() {
        runBlocking {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                tachiyomi.data.Database.Schema.create(driver).await()
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge/a")
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = MERGED_SOURCE, url = "/merge/b")
                driver.insertManga(id = 4L, sourceId = SOURCE, url = "/child/b")
                driver.insertManga(id = 5L, sourceId = MERGED_SOURCE, url = "/merge/c")
                driver.insertManga(id = 6L, sourceId = SOURCE, url = "/child/c")
                // The pre-shift numbering: 2 = PRIORITY, 4 = HIGHEST, 0 = NONE.
                driver.insertMerge(mergeId = 1L, sortMode = 2, infoMangaId = 2L)
                driver.insertMerge(mergeId = 3L, sortMode = 4, infoMangaId = 4L)
                driver.insertMerge(mergeId = 5L, sortMode = 0, infoMangaId = null)
                driver.insertEntry(mergeId = 1L, mangaId = 2L)
                driver.insertEntry(mergeId = 3L, mangaId = 4L)
                driver.insertEntry(mergeId = 5L, mangaId = 6L)

                val migration = MergedMangaDedupeModeMigration(
                    mangaMergeRepository = MangaMergeRepositoryImpl(database),
                    updateMergedSettings = UpdateMergedSettings(MangaMergeRepositoryImpl(database)),
                )
                migration.invoke(MigrationContext(dryrun = false, previousVersion = 76)) shouldBe true

                driver.sortMode(1L) shouldBe MergeSettings.CHAPTER_SORT_PRIORITY
                driver.sortMode(3L) shouldBe MergeSettings.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER
                // NONE has nothing above it to drop, and must not underflow.
                driver.sortMode(5L) shouldBe MergeSettings.CHAPTER_SORT_NONE
                // The info entry is a child row and is carried over untouched.
                driver.scalarLong("SELECT info_manga_id FROM merge WHERE manga_id = 1") shouldBe 2L
                driver.scalarLong("SELECT info_manga_id FROM merge WHERE manga_id = 3") shouldBe 4L
                // Only the settings move: no merge or entry is created or removed.
                driver.scalarLong("SELECT count(*) FROM merge") shouldBe 3L
                driver.scalarLong("SELECT count(*) FROM merged_entry") shouldBe 3L
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun SqlDriver.insertManga(id: Long, sourceId: Long, url: String) {
        execute(
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
                123, 1, 1, 0,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertMerge(mergeId: Long, sortMode: Int, infoMangaId: Long?) {
        execute(
            null,
            "INSERT INTO merge(manga_id, chapter_sort_mode, info_manga_id) " +
                "VALUES ($mergeId, $sortMode, ${infoMangaId ?: "NULL"})",
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertEntry(mergeId: Long, mangaId: Long) {
        execute(
            null,
            "INSERT INTO merged_entry(merge_id, manga_id, fetch_chapter_updates, download_chapters, chapter_priority) " +
                "VALUES ($mergeId, $mangaId, 1, 0, 1)",
            0,
        ).await()
    }

    private fun SqlDriver.sortMode(mergeId: Long): Long? =
        scalarLong("SELECT chapter_sort_mode FROM merge WHERE manga_id = $mergeId")

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        }, 0).value

    private companion object {
        const val SOURCE = 10L
        const val MERGED_SOURCE = 6969L
    }
}
// KUN <--
