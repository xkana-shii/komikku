package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.updates.UpdatesRepositoryImpl
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.updates.model.UpdatesWithRelations
import kotlin.time.Instant

// KNS -->
/** The Updates rows carry each chapter's saved bookmark color, straight from updatesView. */
class UpdatesBookmarkColorTest {

    private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val database = DatabaseBindings.providesDatabase(driver)
    private val updates = UpdatesRepositoryImpl(database)
    private val chapters = ChapterRepositoryImpl(database)
    private val epoch = Instant.fromEpochMilliseconds(0)

    init {
        runBlocking { Database.Schema.create(driver).await() }
    }

    @AfterEach
    fun tearDown() = driver.close()

    @Test
    fun `every palette color reaches the Updates rows, unbookmarked chapters keep theirs unused`() = runBlocking<Unit> {
        seed()

        val rows = all().first().associateBy { it.chapterUrl }
        (0..6).forEach { color ->
            rows.getValue("/c$color").bookmark shouldBe true
            rows.getValue("/c$color").bookmarkColor shouldBe color
        }
        rows.getValue("/plain").bookmark shouldBe false
        rows.getValue("/plain").bookmarkColor shouldBe 0

        val unread = updates.subscribeWithRead(read = false, after = epoch, limit = 100).first()
        unread.associate { it.chapterUrl to it.bookmarkColor } shouldBe rows.mapValues { it.value.bookmarkColor }
    }

    @Test
    fun `existing filters still select the same rows`() = runBlocking<Unit> {
        seed()

        all(bookmarked = true).first().map { it.chapterUrl }.toSet() shouldBe (0..6).map { "/c$it" }.toSet()
        all(bookmarked = false).first().map { it.chapterUrl } shouldBe listOf("/plain")
        // Newest fetch first, as before the view gained the color.
        all().first().map { it.chapterUrl } shouldBe listOf("/plain") + (6 downTo 0).map { "/c$it" }
    }

    @Test
    fun `changing a bookmark color updates the observed Updates rows`() = runBlocking<Unit> {
        seed()
        val emissions = Channel<List<UpdatesWithRelations>>(Channel.UNLIMITED)
        val job = launch { all().collect { emissions.send(it) } }
        try {
            withTimeout(10_000) {
                emissions.receive().first { it.chapterUrl == "/c2" }.bookmarkColor shouldBe 2
                chapters.update(ChapterUpdate(chapterId("/c2")) { bookmarkColor = 5 })
                var latest = emissions.receive()
                while (latest.first { it.chapterUrl == "/c2" }.bookmarkColor != 5) latest = emissions.receive()
                latest.first { it.chapterUrl == "/c2" }.bookmarkColor shouldBe 5
            }
        } finally {
            job.cancel()
        }
    }

    private fun all(bookmarked: Boolean? = null) = updates.subscribeAll(
        after = epoch,
        limit = 100,
        unread = null,
        started = null,
        bookmarked = bookmarked,
        hideExcludedScanlators = false,
        includedCategories = emptyList(),
        excludedCategories = emptyList(),
    )

    private suspend fun seed() {
        driver.execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                remote_memo, user_favorite_at, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized
            ) VALUES (1, 1, '/manga/1', 'Title', 0, 0, X'7B7D', 1000, '', 0, 0, 0, 1)
            """.trimIndent(),
            0,
        ).await()
        (0..6).forEach { color -> insertChapter("/c$color", bookmark = true, color = color, fetch = 2000L + color) }
        insertChapter("/plain", bookmark = false, color = 0, fetch = 3000)
    }

    private suspend fun insertChapter(url: String, bookmark: Boolean, color: Int, fetch: Long) {
        driver.execute(
            null,
            """
            INSERT INTO chapter(
                manga_id, remote_url, remote_name, remote_chapter_number, remote_order, remote_memo,
                user_read, user_bookmark, user_last_page_read, remote_date_upload, state_date_fetch,
                user_bookmark_color
            ) VALUES (1, '$url', '$url', 1.0, 0, X'7B7D', 0, ${if (bookmark) 1 else 0}, 0, 1500, $fetch, $color)
            """.trimIndent(),
            0,
        ).await()
    }

    private fun chapterId(url: String): Long =
        driver.executeQuery(
            null,
            "SELECT id FROM chapter WHERE remote_url = '$url'",
            { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            },
            0,
        ).value
}
// KNS <--
