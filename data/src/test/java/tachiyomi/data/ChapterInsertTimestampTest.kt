// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.domain.chapter.model.Chapter
import kotlin.time.Instant

/**
 * Guards the insert-time stamp: a chapter the source discovers has no timestamp of its own, and
 * `SyncDelta.changedSince` would filter it out of the next upload, so the insert has to date it.
 * A restore is the opposite case and passes its own timestamp (covered in
 * `RestoreRepositoryChapterTombstoneTest`).
 */
class ChapterInsertTimestampTest {

    @Test
    fun `a chapter added by a library update is stamped so the delta carries it`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val chapterRepository = ChapterRepositoryImpl(database)
                driver.insertManga()

                // What a source fetch produces: no local edit, so no timestamp.
                chapterRepository.addAll(listOf(chapter("/c1", lastModifiedAt = 0L)))

                chapterRepository.getChapterByMangaId(1).single().lastModifiedAt shouldBeGreaterThan 0L
            } finally {
                driver.close()
            }
        }
    }

    private fun chapter(url: String, lastModifiedAt: Long) = Chapter.create().copy(
        mangaId = 1L,
        url = url,
        name = url,
        dateFetch = Instant.fromEpochMilliseconds(1_000),
        lastModifiedAt = lastModifiedAt,
    )

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.insertManga() {
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
                1, 1, '/manga/1', 'Title', 0, 0,
                '{}', '', 0, 0,
                0, 1, 'https://example.com/cover.jpg',
                123, 0, 1, 0,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }
}
// KUN <--
