// KUN -->
package eu.kanade.tachiyomi.data.backup.create

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.manga.interactor.GetFlatMetadataById

/**
 * The payload a backup carries for reading history.
 *
 * A local removal resets `read_at`, and the sync upload drops that row: the removal travels as an
 * explicit `history` tombstone instead, because a `lastRead = 0` row loses the server's
 * version-first merge and the live entry comes back. A manual or file backup has no such channel
 * and every restore path understands the `0` row as an explicit removal, so it must keep it.
 *
 * The repositories are the real ones over an in-memory database; only the source lookups that a
 * backup uses for metadata are stubs, and they play no part in the history section.
 */
class MangaBackupCreatorTest {

    @Test
    fun `a manual backup keeps the history rows a local removal reset`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 207L, url = "/m/ch2", index = 2)
            // read_at = NULL is the local removal; the other entry is a normal read
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = null)
            fixture.insertHistory(mangaId = 7L, chapterId = 207L, historyId = 2007L, readAt = 3000L)

            val manga = fixture.mangaRepository.getMangaById(7L)
            val entries = fixture.creator(listOf(manga), BackupOptions(history = true)).toList()

            val history = entries.single().history
            history.map { it.url } shouldBe listOf("/m/ch1", "/m/ch2")
            // the reset row survives as the explicit `lastRead = 0` removal a restore understands
            history.single { it.url == "/m/ch1" }.lastRead shouldBe 0L
            history.single { it.url == "/m/ch2" }.lastRead shouldBe 3000L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `history sync off exports no history at all`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 3000L)

            val manga = fixture.mangaRepository.getMangaById(7L)
            val entries = fixture.creator(listOf(manga), BackupOptions(history = false)).toList()

            entries.single().history.shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val database = DatabaseBindings.providesDatabase(driver)

        val mangaRepository = MangaRepositoryImpl(database)

        init {
            runBlocking { Database.Schema.create(driver).await() }
        }

        /** A source lookup a backup uses for flat metadata; these rows are on no source. */
        val creator = MangaBackupCreator(
            mangaRepository = mangaRepository,
            chapterRepository = ChapterRepositoryImpl(database),
            categoryRepository = CategoryRepositoryImpl(database),
            historyRepository = HistoryRepositoryImpl(database),
            trackRepository = TrackRepositoryImpl(database),
            mangaMergeRepository = MangaMergeRepositoryImpl(database),
            sourceManager = mockk(relaxed = true),
            getFlatMetadataById = mockk<GetFlatMetadataById>(relaxed = true),
        )

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
                    NULL, NULL
                )
                """.trimIndent(),
                0,
            ).await()
        }

        suspend fun insertChapter(mangaId: Long, chapterId: Long, url: String, index: Int = 1) {
            driver.execute(
                null,
                """
                INSERT INTO chapter(
                    id, manga_id, remote_url, remote_name, remote_chapter_number,
                    remote_order, remote_memo, user_read, user_bookmark,
                    user_last_page_read, state_date_fetch,
                    remote_date_upload, last_modified_at, version, is_syncing
                ) VALUES (
                    $chapterId, $mangaId, '$url', 'Chapter $index', 1.0,
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
                "INSERT INTO history(id, chapter_id, manga_id, read_at, read_duration) " +
                    "VALUES ($historyId, $chapterId, $mangaId, ${readAt ?: "NULL"}, 60)",
                0,
            ).await()
        }
    }
}
// KUN <--
