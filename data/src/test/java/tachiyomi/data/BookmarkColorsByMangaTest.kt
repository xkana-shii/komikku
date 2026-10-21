package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.chapter.ChapterRepositoryImpl

// KNS -->
/** The per-manga bookmark colors behind the library's bookmark-color filter. */
class BookmarkColorsByMangaTest {

    private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val database = DatabaseBindings.providesDatabase(driver)
    private val repository = ChapterRepositoryImpl(database)

    init {
        runBlocking { Database.Schema.create(driver).await() }
    }

    @AfterEach
    fun tearDown() = driver.close()

    @Test
    fun `only bookmarked chapters count, and merged entries get their members' colors`() = runBlocking<Unit> {
        listOf(1L, 2L, 3L, 4L).forEach { insertManga(it) }
        insertChapter(mangaId = 1, url = "/1a", bookmark = true, color = 1)
        insertChapter(mangaId = 1, url = "/1b", bookmark = true, color = 0)
        insertChapter(mangaId = 1, url = "/1c", bookmark = false, color = 5)
        insertChapter(mangaId = 2, url = "/2a", bookmark = true, color = 4)
        insertChapter(mangaId = 4, url = "/4a", bookmark = false, color = 0)
        // 3 is a merged entry made of 1 and 2
        insertMerged(mergeId = 3, mangaId = 1)
        insertMerged(mergeId = 3, mangaId = 2)

        repository.getBookmarkColorsByMangaAsFlow().first() shouldBe mapOf(
            1L to setOf(1, 0),
            2L to setOf(4),
            3L to setOf(1, 0, 4),
        )
    }

    private suspend fun insertManga(id: Long) {
        driver.execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                remote_memo, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized
            ) VALUES ($id, 1, '/manga/$id', 'Title $id', 0, 0, X'7B7D', '', 0, 0, 0, 1)
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun insertChapter(mangaId: Long, url: String, bookmark: Boolean, color: Int) {
        driver.execute(
            null,
            """
            INSERT INTO chapter(
                manga_id, remote_url, remote_name, remote_chapter_number, remote_order, remote_memo,
                user_read, user_bookmark, user_last_page_read, state_date_fetch, user_bookmark_color
            ) VALUES ($mangaId, '$url', '$url', 1.0, 0, X'7B7D', 0, ${if (bookmark) 1 else 0}, 0, 1, $color)
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun insertMerged(mergeId: Long, mangaId: Long) {
        driver.execute(
            null,
            """
            INSERT INTO merged(
                info_manga, get_chapter_updates, chapter_sort_mode, chapter_priority, download_chapters,
                merge_id, merge_url, manga_id, manga_url, manga_source
            ) VALUES (0, 1, 0, 0, 1, $mergeId, '/manga/$mergeId', $mangaId, '/manga/$mangaId', 1)
            """.trimIndent(),
            0,
        ).await()
    }
}
// KNS <--
