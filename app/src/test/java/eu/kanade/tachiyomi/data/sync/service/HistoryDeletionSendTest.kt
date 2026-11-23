// KUN -->
package eu.kanade.tachiyomi.data.sync.service

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings
import tachiyomi.data.history.HistoryRepositoryImpl
import kotlin.time.Clock

/**
 * The send side of the history tombstone queue.
 *
 * `SyncYomiSyncService.submitDeletedItems` pages `pending_history_deletion` by queue row id, builds
 * the keys with [historyTombstones] and asks [historyRowsToClear] which rows of a page it may drop.
 * The requests themselves go over HTTP, which these tests do not touch: they drive the decision the
 * loop makes per page, and the repository it deletes through, over a real in-memory database.
 */
class HistoryDeletionSendTest {

    @Test
    fun `a page the server accounted for is cleared and leaves the removal standing`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.queueThreeEntries()

            val page = fixture.repository.getPendingHistoryDeletions(limit = 3)
            // every entry of the request is accounted for (`applied` counts changes, not entries)
            val acknowledged = acknowledgedDeletions(
                """{"acknowledged":3,"applied":2}""",
                emptySet(),
                emptySet(),
                historyTombstones(page).map { it.key },
            )
            val rowsToClear = historyRowsToClear(page, acknowledged)
            // a paging bug that returned an empty page would make the assertions below vacuous
            assertEquals(3, page.size)
            // catches a page cleared from `applied` alone, or one that drops fewer rows than it sent
            assertEquals(page.map { it.id }, rowsToClear.map { it.id })

            fixture.repository.clearPendingHistoryDeletion(rowsToClear.map { it.id })
            assertTrue(fixture.repository.getPendingHistoryDeletions(limit = 10).isEmpty())
            assertEquals(3L, fixture.count("SELECT count(*) FROM history WHERE read_at IS NULL"))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a page the server only partly applied clears nothing`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.queueThreeEntries()

            val page = fixture.repository.getPendingHistoryDeletions(limit = 3)
            // `applied` counts changed rows, so a page of three that changed two is not a
            // confirmation: clearing here would lose the tombstone the server never looked at
            val acknowledged = acknowledgedDeletions(
                """{"acknowledged":2,"applied":2}""",
                emptySet(),
                emptySet(),
                historyTombstones(page).map { it.key },
            )

            // a paging bug that returned an empty page would make the assertion below vacuous
            assertEquals(3, page.size)
            assertTrue(historyRowsToClear(page, acknowledged).isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a legacy acknowledgement clears nothing`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.queueThreeEntries()

            val page = fixture.repository.getPendingHistoryDeletions(limit = 3)
            // a pre-#335 server 2xxes and ignores `items`: it only read the legacy chapter list, so
            // clearing the history rows here would drop removals the server never applied
            val acknowledged = acknowledgedDeletions(
                """{"acknowledged":3}""",
                chapters = emptySet(),
                mangas = emptySet(),
                histories = historyTombstones(page).map { it.key },
            )

            // a paging bug that returned an empty page would make the assertion below vacuous
            assertEquals(3, page.size)
            assertTrue(historyRowsToClear(page, acknowledged).isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a page the acknowledgement only partly names is not cleared`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.queueThreeEntries()

            val page = fixture.repository.getPendingHistoryDeletions(limit = 3)
            // `acknowledgedDeletions` answers all-or-nothing today, so this set is built by hand: the
            // rule has to be "as many entries as the page", not "not empty", or a future partial
            // answer would drop tombstones the server never applied
            val partial = AcknowledgedDeletions(
                chapters = emptyList(),
                mangas = emptyList(),
                histories = listOf(historyTombstones(page).first().key),
            )

            assertEquals(3, page.size)
            assertEquals(emptyList<Long>(), historyRowsToClear(page, partial).map { it.id })
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

        /** One manga, three read chapters, all three removals queued while sync could deliver them. */
        suspend fun queueThreeEntries() {
            driver.execute(
                null,
                """
                INSERT INTO manga(
                    id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                    remote_memo, user_notes, user_reader_flags, user_chapter_flags,
                    state_chapter_fetch_interval, state_initialized, remote_cover,
                    state_cover_last_modified, last_modified_at, version, is_syncing,
                    user_favorite_at, favorite_modified_at
                ) VALUES (1, 7, '/m', 'Title', 0, 0, '{}', '', 0, 0, 0, 1, NULL, 123, 10, 1, 0, 1000, 500)
                """.trimIndent(),
                0,
            ).await()
            listOf(11L, 12L, 13L).forEach { chapterId ->
                driver.execute(
                    null,
                    """
                    INSERT INTO chapter(
                        id, manga_id, remote_url, remote_name, remote_chapter_number,
                        remote_order, remote_memo, user_read, user_bookmark,
                        user_last_page_read, state_date_fetch,
                        remote_date_upload, last_modified_at, version, is_syncing
                    ) VALUES ($chapterId, 1, '/m/ch${chapterId - 10}', 'Chapter', 1.0, 1, '{}', 1, 0, 0, 2000, 2000, 10, 1, 0)
                    """.trimIndent(),
                    0,
                ).await()
                driver.execute(
                    null,
                    "INSERT INTO history(id, manga_id, chapter_id, read_at, read_duration) " +
                        "VALUES (${chapterId + 90}, 1, $chapterId, ${Clock.System.now().toEpochMilliseconds()}, 60)",
                    0,
                ).await()
            }
            repository.removeHistory(queueForSync = true, historyIds = listOf(101L, 102L, 103L))
        }

        fun count(sql: String): Long = driver.executeQuery(
            null,
            sql,
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else 0L) },
            0,
        ).value!!
    }
}
// KUN <--
