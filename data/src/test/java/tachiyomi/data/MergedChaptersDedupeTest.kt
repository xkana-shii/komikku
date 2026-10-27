// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.chapter.ChapterMapper
import tachiyomi.domain.manga.model.MergeSettings

/**
 * Guards the merge dedupe that lives in the queries: `getDedupedMergedChapters` and the library
 * view's merged branch must return, per `chapter_sort_mode`, the same chapters the chapter list
 * shows. A merge whose children both publish chapter 1 must lose one of them, and the chapter counts
 * on the library grid must agree with the list.
 *
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY "Merge dedupe in database"), with this fork's
 * sort-mode numbering (`PRIORITY` is 1 here) because the constants differ.
 */
class MergedChaptersDedupeTest {

    @Test
    fun `no dedupe returns every chapter of every child`() {
        runBlocking {
            val driver = seededDriver(MergeSettings.CHAPTER_SORT_NONE)
            try {
                val database = DatabaseBindings.providesDatabase(driver)

                chapters(database, MergeSettings.CHAPTER_SORT_NONE) shouldContainExactly listOf("/a1", "/a2", "/b1", "/b3")
                libraryTotal(driver) shouldBe 4L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `priority dedupe keeps the chapter of the higher priority source`() {
        runBlocking {
            val driver = seededDriver(MergeSettings.CHAPTER_SORT_PRIORITY)
            try {
                val database = DatabaseBindings.providesDatabase(driver)

                // Child A has priority 1, child B priority 2, so the shared chapter 1 is A's.
                chapters(database, MergeSettings.CHAPTER_SORT_PRIORITY) shouldContainExactly listOf("/a1", "/a2", "/b3")
                libraryTotal(driver) shouldBe 3L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `most chapters mode keeps the source with the most chapters`() {
        runBlocking {
            val driver = seededDriver(MergeSettings.CHAPTER_SORT_MOST_CHAPTERS)
            try {
                val database = DatabaseBindings.providesDatabase(driver)

                // A tie on the count is broken by the lower manga id, so child A wins.
                chapters(database, MergeSettings.CHAPTER_SORT_MOST_CHAPTERS) shouldContainExactly listOf("/a1", "/a2")
                libraryTotal(driver) shouldBe 2L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `highest chapter number mode keeps the source that reaches furthest`() {
        runBlocking {
            val driver = seededDriver(MergeSettings.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER)
            try {
                val database = DatabaseBindings.providesDatabase(driver)

                // Child B reaches chapter 3, child A only chapter 2.
                chapters(database, MergeSettings.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER) shouldContainExactly listOf("/b1", "/b3")
                libraryTotal(driver) shouldBe 2L
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun chapters(database: Database, sortMode: Int): List<String> = database.chapterQueries
        .getDedupedMergedChapters(
            mangaId = MERGE_ID,
            sortMode = sortMode.toLong(),
            applyFilter = 0,
            bookmarkUnmask = 0,
            bookmarkMask = 0,
            mapper = ChapterMapper::mapChapter,
        )
        .awaitAsList()
        .map { it.url }

    private suspend fun libraryTotal(driver: SqlDriver): Long =
        driver.scalarLong("SELECT totalCount FROM libraryView WHERE id = $MERGE_ID") ?: -1L

    private suspend fun seededDriver(sortMode: Int): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
        // KUN -->
        // The sort mode lives on the merge row now; the entries only carry their priority. The
        // `manga_source = 6969` self-reference that used to hold it is gone.
        // KUN <--
        driver.insertManga(id = MERGE_ID, sourceId = MERGED_SOURCE_ID, url = "/merge")
        driver.insertManga(id = 1L, sourceId = 1L, url = "/a")
        driver.insertManga(id = 2L, sourceId = 1L, url = "/b")
        driver.insertMerge(mergeId = MERGE_ID, sortMode = sortMode)
        driver.insertReference(mergeId = MERGE_ID, mangaId = 1L, priority = 1)
        driver.insertReference(mergeId = MERGE_ID, mangaId = 2L, priority = 2)
        driver.insertChapter(mangaId = 1L, url = "/a1", number = "1")
        driver.insertChapter(mangaId = 1L, url = "/a2", number = "2")
        driver.insertChapter(mangaId = 2L, url = "/b1", number = "1")
        driver.insertChapter(mangaId = 2L, url = "/b3", number = "3")
        return driver
    }

    private suspend fun SqlDriver.execute(sql: String) {
        execute(null, sql, 0).await()
    }

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(null, sql, { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) }, 0)
            .value

    private suspend fun SqlDriver.insertManga(id: Long, sourceId: Long, url: String) {
        execute(
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
        )
    }

    private suspend fun SqlDriver.insertMerge(mergeId: Long, sortMode: Int) {
        execute(
            """
            INSERT INTO merge(manga_id, chapter_sort_mode, info_manga_id)
            VALUES ($mergeId, $sortMode, 1)
            """.trimIndent(),
        )
    }

    private suspend fun SqlDriver.insertReference(mergeId: Long, mangaId: Long, priority: Long) {
        execute(
            """
            INSERT INTO merged_entry(
                merge_id, manga_id, fetch_chapter_updates, download_chapters, chapter_priority
            ) VALUES (
                $mergeId, $mangaId, 1, 0, $priority
            )
            """.trimIndent(),
        )
    }

    private suspend fun SqlDriver.insertChapter(mangaId: Long, url: String, number: String) {
        execute(
            """
            INSERT INTO chapter(
                manga_id, remote_url, remote_name, remote_chapter_number, remote_order,
                remote_memo, user_read, user_bookmark, user_last_page_read, state_date_fetch,
                last_modified_at, version, is_syncing
            ) VALUES (
                $mangaId, '$url', '$url', $number, 0,
                X'7B7D', 0, 0, 0, 1000,
                10, 1, 0
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val MERGE_ID = 7L
        const val MERGED_SOURCE_ID = 6969L
    }
}
// KUN <--
