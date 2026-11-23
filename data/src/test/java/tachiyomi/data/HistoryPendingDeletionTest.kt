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
 * The database-backed history deletion queue (`pending_history_deletion`).
 *
 * A removal is a soft delete: the row loses its read date, which is what hides it from the History
 * tab, and the tombstone the server still owes goes into a table of its own, keyed by the server's
 * key parts. That table has no foreign key, so the removal outlives the history row, its chapter and
 * its manga. The three removal paths, the re-read, the restore and the "clear database" cleanup run
 * against a real in-memory database here, so the queries themselves are the subject rather than a
 * mock of them.
 */
class HistoryPendingDeletionTest {

    @Test
    fun `a per-entry removal queues that entry and leaves the rest of the history readable`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = 6_000L)

            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            // the removed row is kept but loses its read date, and exactly its entry is queued
            fixture.readAtOf(101L) shouldBe null
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1")
            // the entry the user did not select keeps its read date and is never queued
            fixture.readAtOf(102L) shouldBe 6_000L
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
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1", "7|/m|/m/ch2")
            // catches a dropped `removed_at_ms` bind in this statement, or a 0 stamped there
            fixture.queuedTimes().forEach { (it in before..after) shouldBe true }
            // the other manga is untouched
            fixture.readAtOf(201L) shouldBe 7_000L
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
            // catches a dropped `removed_at_ms` bind in "clear all", which has its own statement
            fixture.queuedTimes().single().let { (it in before..after) shouldBe true }
            // the row is still there (a delete would have removed it), so the history is unchanged
            fixture.historyIds() shouldBe listOf(101L, 102L)
            // an already-reset row is not retroactively queued: that removal predates the queue and
            // tombstoning it would delete entries the user removed on another protocol
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the three removal paths queue one row each`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            listOf(1L to "/m", 2L to "/n", 3L to "/o").forEach { (id, url) ->
                fixture.insertManga(id = id, sourceId = 7L, url = url)
                fixture.insertChapter(mangaId = id, chapterId = id * 100 + 1, url = "$url/ch1")
                fixture.insertHistory(
                    mangaId = id,
                    chapterId = id * 100 + 1,
                    historyId = id * 1000,
                    readAt = 5_000L,
                )
            }

            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(1_000L))
            fixture.repository.removeHistoryByMangaIds(queueForSync = true, mangaIds = listOf(2L))
            fixture.repository.removeAllHistory(queueForSync = true) shouldBe true

            // one key per removed entry, none doubled, whichever path queued it
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1", "7|/n|/n/ch1", "7|/o|/o/ch1")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `an entry a remote tombstone reset is not queued by a later clear all`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = 6_000L)
            // the sync applied a peer's `history` tombstone: a plain reset, never one of our removals
            fixture.repository.resetHistory(listOf(102L))

            fixture.repository.removeAllHistory(queueForSync = true) shouldBe true

            // catches an enqueue that copies every row instead of the ones this removal still resets:
            // it would echo the peer's removal back to the server as a tombstone this device made
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a queued tombstone survives the chapter it came from being deleted`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            // what a source dropping the chapter, or a library prune, does to it
            fixture.deleteChapter(11L)

            // the history row cascades with its chapter, the tombstone does not
            fixture.historyIds().shouldBeEmpty()
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1")
            fixture.repository.getPendingHistoryDeletions(limit = 10).single().let {
                it.sourceId shouldBe 7L
                it.mangaUrl shouldBe "/m"
                it.chapterUrl shouldBe "/m/ch1"
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a queued tombstone survives its manga being deleted`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))

            // the manga delete takes its chapters and their history with it (both FKs cascade)
            fixture.deleteManga(1L)

            fixture.historyIds().shouldBeEmpty()
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1")
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
            val removedAtMs = fixture.queuedTimes().single()
            (removedAtMs in before..after) shouldBe true
            // the deletions request must hand that same value to the sync
            fixture.repository.getPendingHistoryDeletions(limit = 10).single().removedAtMs shouldBe removedAtMs
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `reading the chapter again drops the queued tombstone`(): Unit = runBlocking {
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

            // a new read supersedes the removal: the row comes back and its tombstone is not sent
            fixture.readAtOf(101L) shouldBe 9_000L
            fixture.queuedKeys().shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a re-read in a batch drops the tombstone of its own chapter only`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            listOf(11L, 12L).forEach { chapterId ->
                fixture.insertChapter(mangaId = 1L, chapterId = chapterId, url = "/m/ch$chapterId")
                fixture.insertHistory(mangaId = 1L, chapterId = chapterId, historyId = 100L + chapterId, readAt = 5_000L)
            }
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(111L, 112L))

            fixture.repository.upsertHistory(
                listOf(
                    HistoryUpdate(
                        chapterId = 11L,
                        readAt = Instant.fromEpochMilliseconds(9_000L),
                        sessionReadDuration = 30L,
                    ),
                ),
            )

            // catches a batch upsert that skipped the queue write for its entries
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch12")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a restore cannot resurrect a queued row but still writes an ordinary one`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = 6_000L)
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(101L))
            fixture.repository.removeHistory(queueForSync = false, historyIds = listOf(102L))

            // a payload carrying both entries' reads, e.g. a peer that had not learned of the removal
            fixture.insertRestoredRead(chapterId = 11L, readAt = 8_000L)
            fixture.insertRestoredRead(chapterId = 12L, readAt = 8_000L)

            // the queued removal is waiting to be sent, so the payload must not undo it...
            fixture.readAtOf(101L) shouldBe null
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1")
            // ...while a removal with no tombstone left to send is an ordinary reset row
            fixture.readAtOf(102L) shouldBe 8_000L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `clear database drops a reset row while its tombstone stays queued`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            fixture.insertChapter(mangaId = 1L, chapterId = 11L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 1L, chapterId = 12L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 1L, chapterId = 11L, historyId = 101L, readAt = 5_000L)
            fixture.insertHistory(mangaId = 1L, chapterId = 12L, historyId = 102L, readAt = 6_000L)

            fixture.repository.removeAllHistory(queueForSync = true) shouldBe true

            fixture.repository.deleteResetHistory()

            // every reset row goes, the ones whose tombstone was never sent included: the queue does
            // not need the row, and a tombstone would otherwise keep it out of the cleanup
            fixture.historyIds().shouldBeEmpty()
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch1", "7|/m|/m/ch2")
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

            // exactly the queued entry, with the source, manga url and chapter url a key is made of;
            // swapping the manga and chapter urls here would tombstone the wrong server entry
            pending.map { it.sourceId to (it.mangaUrl to it.chapterUrl) } shouldBe listOf(7L to ("/m" to "/m/ch2"))
            // the id is the queue row's own: no history row id can be relied on once it is deleted
            pending.single().id shouldBe fixture.queuedIds().single()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the queue is paged by its own row id so a clear all is never held in memory`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            listOf(11L, 12L, 13L).forEach { chapterId ->
                fixture.insertChapter(mangaId = 1L, chapterId = chapterId, url = "/m/ch$chapterId")
                fixture.insertHistory(mangaId = 1L, chapterId = chapterId, historyId = 100L + chapterId, readAt = 5_000L)
            }
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(111L, 112L, 113L))

            val first = fixture.repository.getPendingHistoryDeletions(limit = 2)
            val second = fixture.repository.getPendingHistoryDeletions(limit = 2, afterId = first.last().id)
            val third = fixture.repository.getPendingHistoryDeletions(limit = 2, afterId = second.last().id)

            first.map { it.chapterUrl } shouldBe listOf("/m/ch11", "/m/ch12")
            // the cursor, not the queue's content, decides the next page: a server that did not apply
            // the first batch leaves those rows queued and the next page must still move past them
            second.map { it.chapterUrl } shouldBe listOf("/m/ch13")
            third.shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `clearing an acknowledged page drops those queue rows and keeps the removal`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            listOf(11L, 12L).forEach { chapterId ->
                fixture.insertChapter(mangaId = 1L, chapterId = chapterId, url = "/m/ch$chapterId")
                fixture.insertHistory(mangaId = 1L, chapterId = chapterId, historyId = 100L + chapterId, readAt = 5_000L)
            }
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(111L, 112L))

            fixture.repository.clearPendingHistoryDeletion(fixture.repository.getPendingHistoryDeletions(1).map { it.id })

            // the page the server confirmed is gone, the rest stays queued for the next page
            fixture.queuedKeys() shouldBe listOf("7|/m|/m/ch12")
            // and the removal stands: a tombstone is not a reason to show the entry again
            fixture.readAtOf(111L) shouldBe null
            fixture.readAtOf(112L) shouldBe null
            // nothing left to send once the last page is confirmed
            fixture.repository.clearPendingHistoryDeletion(fixture.repository.getPendingHistoryDeletions(10).map { it.id })
            fixture.queuedKeys().shouldBeEmpty()
            fixture.readAtOf(112L) shouldBe null
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a stranded queue is dropped without undoing the removals`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 1L, sourceId = 7L, url = "/m")
            listOf(11L, 12L).forEach { chapterId ->
                fixture.insertChapter(mangaId = 1L, chapterId = chapterId, url = "/m/ch$chapterId")
                fixture.insertHistory(mangaId = 1L, chapterId = chapterId, historyId = 100L + chapterId, readAt = 5_000L)
            }
            // queued while the configuration could still deliver it, then the user configured it away
            fixture.repository.removeHistory(queueForSync = true, historyIds = listOf(111L, 112L))

            fixture.repository.clearAllPendingHistoryDeletions()

            // catches a sweep that restored the rows instead of just dropping the tombstones
            fixture.queuedKeys().shouldBeEmpty()
            fixture.readAtOf(111L) shouldBe null
            fixture.readAtOf(112L) shouldBe null
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
            fixture.queuedKeys().shouldBeEmpty()
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
            fixture.queuedKeys().shouldBeEmpty()
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
            runBlocking {
                // the app's driver enables them, and the tombstone has to survive the cascades they run
                driver.execute(null, "PRAGMA foreign_keys = ON", 0).await()
                Database.Schema.create(driver).await()
            }
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

        suspend fun deleteChapter(chapterId: Long) {
            driver.execute(null, "DELETE FROM chapter WHERE id = $chapterId", 0).await()
        }

        suspend fun deleteManga(mangaId: Long) {
            driver.execute(null, "DELETE FROM manga WHERE id = $mangaId", 0).await()
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

        fun queuedIds(): List<Long> = longs("SELECT id FROM pending_history_deletion ORDER BY id")

        /** `<source id>|<manga url>|<chapter url>` per queue row, i.e. the server's key in test form. */
        fun queuedKeys(): List<String> = strings(
            "SELECT source_id || '|' || manga_url || '|' || chapter_url " +
                "FROM pending_history_deletion ORDER BY id",
        )

        fun queuedTimes(): List<Long> = longs("SELECT removed_at_ms FROM pending_history_deletion ORDER BY id")

        fun readAtOf(historyId: Long): Long? =
            nullableLongs("SELECT read_at FROM history WHERE id = $historyId").firstOrNull()

        private fun longs(sql: String): List<Long> = nullableLongs(sql).filterNotNull()

        private fun strings(sql: String): List<String> = driver.executeQuery(
            null,
            sql,
            { cursor ->
                val values = mutableListOf<String>()
                while (cursor.next().value) values += cursor.getString(0)!!
                QueryResult.Value<List<String>>(values.toList())
            },
            0,
        ).value

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
