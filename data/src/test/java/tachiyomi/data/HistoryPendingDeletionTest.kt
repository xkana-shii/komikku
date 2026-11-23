// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.domain.history.model.HistoryUpdate
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The database-backed history deletion queue (`history.pending_deletion`).
 *
 * A removal is a soft delete: the row keeps the chapter identity the server's `history` tombstone is
 * keyed by, loses its read date (which is what hides it from the History tab) and remembers that the
 * server still owes a tombstone. The three removal paths, the re-read, the restore and the
 * "clear database" cleanup run against a real in-memory database here, so the queries themselves are
 * the subject rather than a mock of them.
 */
class HistoryPendingDeletionTest {

    @Test
    fun `a per-entry removal queues that row and leaves the rest of the history readable`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = 6_000L)

            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            // the removed row is kept (its chapter is the tombstone key) but loses its read date
            fixture.readAtOf(101L) shouldBe null
            fixture.pendingDeletionOf(101L) shouldBe 1L
            // the entry the user did not select keeps its read date and is never queued
            fixture.readAtOf(102L) shouldBe 6_000L
            fixture.pendingDeletionOf(102L) shouldBe 0L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a per-manga removal queues every row of that manga only`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = 6_000L)
            fixture.insertManga(id = 2L, sourceId = 7L, url = "/n")
            fixture.insertChapter(mangaId = 2L, chapterId = 21L, url = "/n/ch1")
            fixture.insertHistory(mangaId = 2L, chapterId = 21L, historyId = 201L, readAt = 7_000L)

            val before = Clock.System.now().toEpochMilliseconds()
            fixture.repository.removeHistoryByMangaIds(queueForSync = true, mangaIds = listOf(1L))
            val after = Clock.System.now().toEpochMilliseconds()

            // both entries of the manga are queued, not just the latest one the History tab showed
            fixture.readAtOf(101L) shouldBe null
            fixture.readAtOf(102L) shouldBe null
            fixture.pendingDeletionOf(101L) shouldBe 1L
            fixture.pendingDeletionOf(102L) shouldBe 1L
            // catches a dropped `removed_at_ms` bind in this statement, or a 0 stamped there
            val firstRemovedAtMs = fixture.removedAtMsOf(101L) ?: 0L
            val secondRemovedAtMs = fixture.removedAtMsOf(102L) ?: 0L
            (firstRemovedAtMs in before..after) shouldBe true
            (secondRemovedAtMs in before..after) shouldBe true
            // the other manga is untouched
            fixture.readAtOf(201L) shouldBe 7_000L
            fixture.pendingDeletionOf(201L) shouldBe 0L
            fixture.removedAtMsOf(201L) shouldBe 0L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `clear all queues every entry that still has a read date`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            // an entry removed by an older build: reset, but never queued for a tombstone
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = null)

            val before = Clock.System.now().toEpochMilliseconds()
            fixture.repository.removeAllHistory(queueForSync = true) shouldBe true
            val after = Clock.System.now().toEpochMilliseconds()

            fixture.readAtOf(101L) shouldBe null
            fixture.pendingDeletionOf(101L) shouldBe 1L
            // catches a dropped `removed_at_ms` bind in "clear all", which has its own statement
            val removedAtMs = fixture.removedAtMsOf(101L) ?: 0L
            (removedAtMs in before..after) shouldBe true
            // the row is still there (a delete would have removed it), so the tombstone can be sent
            fixture.historyIds() shouldBe listOf(101L, 102L)
            // an already-reset row is not retroactively queued: that removal predates the queue and
            // tombstoning it would delete entries the user removed on another protocol
            fixture.pendingDeletionOf(102L) shouldBe 0L
            fixture.removedAtMsOf(102L) shouldBe 0L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `clear database keeps an unsent tombstone and drops a plain reset row`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = null)
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            fixture.repository.deleteResetHistory()

            // the queued row's read date is NULL too, so only the flag tells it apart
            fixture.historyIds() shouldBe listOf(101L)
            fixture.pendingDeletionOf(101L) shouldBe 1L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a queued removal records when it happened`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)

            val before = Clock.System.now().toEpochMilliseconds()
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))
            val after = Clock.System.now().toEpochMilliseconds()

            // catches the milliseconds/seconds unit slip: a seconds value sits far below `before`
            val removedAtMs = fixture.removedAtMsOf(101L) ?: 0L
            (removedAtMs in before..after) shouldBe true
            // the deletions request must hand that same value to the sync
            fixture.repository.getPendingHistoryDeletions(limit = 10).single().removedAtMs shouldBe removedAtMs
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `clearing the queue drops the removal time with the marker`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            listOf(11L, 12L).forEachIndexed { index, chapterId ->
                fixture.insertChapter(mangaId = 1L, chapterId = chapterId, url = "/m/ch$chapterId")
                fixture.insertHistory(
                    mangaId = 1L,
                    chapterId = chapterId,
                    historyId = 100L + index,
                    readAt = 5_000L,
                )
            }
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(100L, 101L))

            // Asserted before the sweep below: it clears both rows, so an ack that left the time
            // behind would pass if this waited until after it.
            fixture.repository.clearPendingHistoryDeletion(listOf(100L))
            fixture.removedAtMsOf(100L) shouldBe 0L
            fixture.pendingDeletionOf(101L) shouldBe 1L
            val stillQueued = fixture.removedAtMsOf(101L) ?: 0L
            (stillQueued > 0L) shouldBe true

            fixture.repository.clearAllPendingHistoryDeletions()

            fixture.removedAtMsOf(101L) shouldBe 0L
            // the removals themselves stand: the rows are still hidden
            fixture.readAtOf(100L) shouldBe null
            fixture.readAtOf(101L) shouldBe null
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `reading the chapter again clears the queue flag`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            fixture.repository.upsertHistory(
                HistoryUpdate(
                    chapterId = 11L,
                    readAt = Instant.fromEpochMilliseconds(9_000L),
                    sessionReadDuration = 30L,
                ),
            )

            // a new read supersedes the removal: the row comes back and is no longer a tombstone
            fixture.readAtOf(101L) shouldBe 9_000L
            fixture.pendingDeletionOf(101L) shouldBe 0L
            // nor does it keep the time of a tombstone it no longer owes
            fixture.removedAtMsOf(101L) shouldBe 0L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a restore cannot resurrect a queued row or clear its flag`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            // a payload carrying this entry's read, e.g. a peer that had not learned of the removal
            fixture.insertRestoredRead(chapterId = 11L, readAt = 8_000L)

            fixture.readAtOf(101L) shouldBe null
            fixture.pendingDeletionOf(101L) shouldBe 1L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the sync reads the queued rows with the urls its tombstone key is built from`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = 6_000L)
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(102L))

            val pending = fixture.repository.getPendingHistoryDeletions(limit = 10)

            // exactly the queued row, with the source, manga url and chapter url a key is made of;
            // swapping the manga and chapter urls here would tombstone the wrong server entry
            pending.map { it.historyId to (it.sourceId to (it.mangaUrl to it.chapterUrl)) } shouldBe
                listOf(102L to (7L to ("/m" to "/m/ch2")))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the queue is paged by row id so a clear all is never held in memory`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            listOf(11L, 12L, 13L).forEachIndexed { index, chapterId ->
                fixture.insertChapter(mangaId = 1L, chapterId = chapterId, url = "/m/ch$chapterId")
                fixture.insertHistory(
                    mangaId = 1L,
                    chapterId = chapterId,
                    historyId = 100L + index,
                    readAt = 5_000L,
                )
            }
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(100L, 101L, 102L))

            val first = fixture.repository.getPendingHistoryDeletions(limit = 2)
            val second = fixture.repository.getPendingHistoryDeletions(limit = 2, afterId = first.last().historyId)
            val third = fixture.repository.getPendingHistoryDeletions(limit = 2, afterId = second.last().historyId)

            first.map { it.historyId } shouldBe listOf(100L, 101L)
            // the cursor, not the flag, decides the next page: a server that did not apply the first
            // batch leaves those rows queued and the next page must still move past them
            second.map { it.historyId } shouldBe listOf(102L)
            third.shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `clearing an acknowledged batch leaves the row for the cleanup`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            fixture.repository.clearPendingHistoryDeletion(listOf(101L))

            // the removal stands (no read date), it is just no longer a tombstone the server owes
            fixture.pendingDeletionOf(101L) shouldBe 0L
            fixture.readAtOf(101L) shouldBe null
            fixture.repository.getPendingHistoryDeletions(limit = 10).shouldBeEmpty()
            // and now "clear database" may take it
            fixture.repository.deleteResetHistory()
            fixture.historyIds().shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a removal no sync can deliver is reset without being queued`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = 6_000L)

            fixture.repository.removeHistory(queueForSync = false, historyIds = listOf(101L))
            fixture.repository.removeHistoryByMangaIds(queueForSync = false, mangaIds = listOf(1L))

            // the removal still hides both rows, but nothing is left for a request that will never run
            fixture.readAtOf(101L) shouldBe null
            fixture.readAtOf(102L) shouldBe null
            fixture.pendingDeletionOf(101L) shouldBe 0L
            fixture.pendingDeletionOf(102L) shouldBe 0L
            // catches a time reference left behind for a tombstone that will never be built
            fixture.removedAtMsOf(101L) shouldBe 0L
            fixture.removedAtMsOf(102L) shouldBe 0L
            fixture.repository.getPendingHistoryDeletions(limit = 10).shouldBeEmpty()
            // an unqueued removal is an ordinary reset row: "clear database" takes it, and a restore
            // payload may write over it again
            fixture.repository.deleteResetHistory()
            fixture.historyIds().shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `clear all without a deliverable tombstone queues nothing`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)

            fixture.repository.removeAllHistory(queueForSync = false) shouldBe true

            fixture.readAtOf(101L) shouldBe null
            fixture.pendingDeletionOf(101L) shouldBe 0L
            fixture.repository.deleteResetHistory()
            fixture.historyIds().shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a removal that cannot be delivered drops a marker an earlier one left`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            // queued while the configuration could still deliver it, then the user configured it away
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            fixture.repository.clearAllPendingHistoryDeletions()

            fixture.pendingDeletionOf(101L) shouldBe 0L
            // the removal itself stands: the row is still hidden
            fixture.readAtOf(101L) shouldBe null
            fixture.repository.deleteResetHistory()
            fixture.historyIds().shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val database = DatabaseBindings.providesDatabase(driver)
        val repository = HistoryRepositoryImpl(database)

        init {
            runBlocking { Database.Schema.create(driver).await() }
        }

        fun close() = driver.close()

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
                    1000, 500
                )
                """.trimIndent(),
                0,
            ).await()
        }

        suspend fun insertChapter(mangaId: Long, chapterId: Long, url: String) {
            driver.execute(
                null,
                """
                INSERT INTO chapter(
                    id, manga_id, remote_url, remote_name, remote_chapter_number,
                    remote_order, remote_memo, user_read, user_bookmark,
                    user_last_page_read, state_date_fetch,
                    remote_date_upload, last_modified_at, version, is_syncing
                ) VALUES (
                    $chapterId, $mangaId, '$url', 'Chapter', 1.0,
                    1, '{}', 0, 0,
                    0, 2000,
                    2000, 10, 1, 0
                )
                """.trimIndent(),
                0,
            ).await()
        }

        suspend fun insertHistory(mangaId: Long, chapterId: Long, historyId: Long, readAt: Long?) {
            driver.execute(
                null,
                "INSERT INTO history(id, manga_id, chapter_id, read_at, read_duration) " +
                    "VALUES ($historyId, $mangaId, $chapterId, ${readAt ?: "NULL"}, 60)",
                0,
            ).await()
        }

        /** The write the restore path makes for one entry of a payload. */
        suspend fun insertRestoredRead(chapterId: Long, readAt: Long) {
            database.historyQueries.setHistoryFromRestore(
                readAt = Instant.fromEpochMilliseconds(readAt),
                readDuration = 90L,
                chapterId = chapterId,
            )
        }

        fun historyIds(): List<Long> = longs("SELECT id FROM history ORDER BY id")

        fun pendingDeletionOf(historyId: Long): Long? =
            nullableLongs("SELECT pending_deletion FROM history WHERE id = $historyId").firstOrNull()

        fun removedAtMsOf(historyId: Long): Long? =
            nullableLongs("SELECT removed_at_ms FROM history WHERE id = $historyId").firstOrNull()

        fun readAtOf(historyId: Long): Long? =
            nullableLongs("SELECT read_at FROM history WHERE id = $historyId").firstOrNull()

        private fun longs(sql: String): List<Long> = nullableLongs(sql).filterNotNull()

        private fun nullableLongs(sql: String): List<Long?> = driver.executeQuery(
            null,
            sql,
            { cursor ->
                val values = mutableListOf<Long?>()
                while (cursor.next().value) values += cursor.getLong(0)
                QueryResult.Value<List<Long?>>(values.toList())
            },
            0,
        ).value
    }
}
// KUN <--
