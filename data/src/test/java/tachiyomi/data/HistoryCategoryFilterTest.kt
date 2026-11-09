// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.history.HistoryRepositoryImpl

/**
 * The History tab's category filter, exercised through the real `history` query instead of a
 * re-implementation of its predicate.
 *
 * Fixture: manga 1 is in category 1, manga 2 in category 2, manga 3 in both and manga 4 in none,
 * each with one chapter and one history entry. Assertions compare manga ids, so forwarding the
 * include and exclude lists to the wrong query parameter (or deriving `includedEmpty` /
 * `excludedEmpty` from the other list) changes the result instead of only its size.
 */
class HistoryCategoryFilterTest {

    @Test
    fun `include-only keeps the manga in at least one selected category`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertHistoryFixture()

                // Manga 3 is in two categories; manga 4 is in none.
                historyMangaIds(database, included = listOf(1L)) shouldBe listOf(1L, 3L)
                // ANY, not ALL: a two-category include still shows the manga in only one of them.
                historyMangaIds(database, included = listOf(1L, 2L)) shouldBe listOf(1L, 2L, 3L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `exclude-only hides the manga in an excluded category`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertHistoryFixture()

                historyMangaIds(database, excluded = listOf(2L)) shouldBe listOf(1L, 4L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `exclusion wins over inclusion`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertHistoryFixture()

                // Manga 3 is in both an included and an excluded category, so it is hidden.
                historyMangaIds(database, included = listOf(1L), excluded = listOf(2L)) shouldBe listOf(1L)
                historyMangaIds(database, included = listOf(1L, 2L), excluded = listOf(1L)) shouldBe listOf(2L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `empty category filters keep every entry`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertHistoryFixture()

                historyMangaIds(database) shouldBe listOf(1L, 2L, 3L, 4L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `inclusion wins shows a manga in both categories and still hides excluded-only ones`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertHistoryFixture()

                // Default: exclusion wins, so manga 3 (in both) is hidden.
                historyMangaIds(database, included = listOf(1L), excluded = listOf(2L)) shouldBe listOf(1L)
                // With inclusion winning, manga 3 is shown; manga 2 (excluded only) and manga 4
                // (uncategorised) stay hidden.
                historyMangaIds(
                    database,
                    included = listOf(1L),
                    excluded = listOf(2L),
                    inclusionWins = true,
                ) shouldBe listOf(1L, 3L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `inclusion wins with an empty include list still applies exclusions`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertHistoryFixture()

                // Nothing is explicitly included, so the include-over-exclude rescue cannot fire.
                historyMangaIds(database, excluded = listOf(2L), inclusionWins = true) shouldBe listOf(1L, 4L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `the system category selects uncategorised manga`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                driver.insertHistoryFixture()

                historyMangaIds(database, included = listOf(0L)) shouldBe listOf(4L)
                historyMangaIds(database, excluded = listOf(0L)) shouldBe listOf(1L, 2L, 3L)
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun historyMangaIds(
        database: Database,
        included: List<Long> = emptyList(),
        excluded: List<Long> = emptyList(),
        inclusionWins: Boolean = false,
    ): List<Long> {
        return HistoryRepositoryImpl(database)
            .getHistory(
                "",
                unfinishedManga = null,
                unfinishedChapter = null,
                nonLibraryEntries = null,
                includedCategories = included,
                excludedCategories = excluded,
                inclusionWins = inclusionWins,
            )
            .first()
            .map { it.mangaId }
            .sorted()
    }

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.insertHistoryFixture() {
        for (id in 1L..4L) {
            insertManga(id)
            insertChapter(mangaId = id)
            insertHistory(mangaId = id)
        }

        insertCategory(id = 1L, name = "Category 1", order = 0L)
        insertCategory(id = 2L, name = "Category 2", order = 1L)
        insertMangaCategory(mangaId = 1L, categoryId = 1L)
        insertMangaCategory(mangaId = 2L, categoryId = 2L)
        insertMangaCategory(mangaId = 3L, categoryId = 1L)
        insertMangaCategory(mangaId = 3L, categoryId = 2L)
    }

    private suspend fun SqlDriver.insertManga(id: Long) {
        execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title, remote_author, remote_genre,
                remote_status, remote_update_strategy, remote_memo, user_notes,
                user_reader_flags, user_chapter_flags, state_chapter_fetch_interval,
                state_initialized, remote_cover, state_cover_last_modified,
                last_modified_at, version, is_syncing, user_favorite_at, favorite_modified_at
            ) VALUES (
                $id, 1, '/manga/$id', 'Manga $id', 'Author', 'Genre',
                0, 0, '{}', '',
                0, 0, 0,
                1, 'https://source.example/$id.jpg', 123,
                10, 1, 0, 1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertCategory(id: Long, name: String, order: Long) {
        execute(
            null,
            """
            INSERT INTO category(id, name, `order`, flags, hidden, version, uid, last_modified_at, is_syncing)
            VALUES ($id, '$name', $order, 0, 0, 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertMangaCategory(mangaId: Long, categoryId: Long) {
        execute(
            null,
            "INSERT INTO manga_category(manga_id, category_id) VALUES ($mangaId, $categoryId)",
            0,
        ).await()
    }

    /**
     * Chapter and history row ids are offset from the manga id so a predicate that joins on the
     * wrong column (chapter/history id instead of `mangaId`) cannot produce the expected numbers.
     */
    private fun chapterIdFor(mangaId: Long): Long = 100L + mangaId

    private fun historyIdFor(mangaId: Long): Long = 1000L + mangaId

    private suspend fun SqlDriver.insertChapter(mangaId: Long) {
        val chapterId = chapterIdFor(mangaId)
        execute(
            null,
            """
            INSERT INTO chapter(
                id, manga_id, remote_url, remote_name, remote_chapter_number,
                remote_order, remote_memo, user_read, user_bookmark,
                user_last_page_read, state_date_fetch,
                remote_date_upload, last_modified_at, version, is_syncing
            ) VALUES (
                $chapterId, $mangaId, '/manga/$mangaId/1', 'Chapter 1', 1.0,
                1, '{}', 0, 0,
                0, 2000,
                2000, 10, 1, 0
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertHistory(mangaId: Long) {
        execute(
            null,
            "INSERT INTO history(id, chapter_id, manga_id, read_at, read_duration) " +
                "VALUES (${historyIdFor(mangaId)}, ${chapterIdFor(mangaId)}, $mangaId, 3000, 60)",
            0,
        ).await()
    }
}
// KUN <--
