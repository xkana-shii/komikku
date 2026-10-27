// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.domain.manga.model.MergedEntry

/**
 * A merge entry lives in the `merged_entry` table and the merge-level settings in `merge`, not in the
 * merge parent's manga row, so inserting, editing or deleting one fires no manga trigger. Without an
 * explicit bump of the merge parent, the delta sync would never upload the change. The restore path
 * writes the merged tables directly and must not go through this repository (the `is_syncing` guard
 * on `bumpSyncVersion` backs that up).
 */
class MangaMergeRepositorySyncTest {

    @Test
    fun `inserting an entry bumps the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertMerge(mergeId = 1L)

                repository.insert(entry())

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe 6L
                (parent.lastModifiedAt > 10L) shouldBe true
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `inserting many entries bumps each merge parent once`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertMerge(mergeId = 1L)

                repository.insertAll(
                    listOf(
                        entry(mangaId = 2L),
                        entry(mangaId = 3L),
                    ),
                )

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                // Both entries belong to the same merge, so it is bumped once, not twice.
                parent.version shouldBe 6L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `updating entry settings bumps the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertMerge(mergeId = 1L, sortMode = 1)
                val entryId = driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.resetManga(version = 5L, lastModifiedAt = 10L)

                val updated = repository.updateAllEntries(
                    listOf(
                        entry(id = entryId, fetchChapterUpdates = false, downloadChapters = true, priority = 5),
                    ),
                )

                updated shouldBe true
                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe 6L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `saving unchanged entry settings does not bump the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertMerge(mergeId = 1L)
                val entryId = driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.resetManga(version = 5L, lastModifiedAt = 10L)
                val before = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()

                // Exactly the settings the entry already has (see `entry` and `insertEntry`).
                val updated = repository.updateAllEntries(
                    listOf(
                        entry(id = entryId, fetchChapterUpdates = true, downloadChapters = false, priority = 1),
                    ),
                )

                updated shouldBe true
                // A no-op save must not raise the parent version: every peer would re-merge for
                // nothing and the delta would carry an unchanged entry.
                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe before.version
                parent.lastModifiedAt shouldBe before.lastModifiedAt
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `saving merge-level settings bumps the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertMerge(mergeId = 1L, sortMode = 0)
                driver.resetManga(version = 5L, lastModifiedAt = 10L)

                repository.updateMergeSettings(mergeId = 1L, chapterSortMode = 2, infoMangaId = null) shouldBe true

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe 6L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `saving unchanged merge-level settings does not bump the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertMerge(mergeId = 1L, sortMode = 0)
                driver.resetManga(version = 5L, lastModifiedAt = 10L)
                val before = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()

                repository.updateMergeSettings(mergeId = 1L, chapterSortMode = 0, infoMangaId = null) shouldBe true

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe before.version
                parent.lastModifiedAt shouldBe before.lastModifiedAt
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `deleting an entry bumps the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertMerge(mergeId = 1L)
                val entryId = driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.resetManga(version = 5L, lastModifiedAt = 10L)

                repository.deleteById(entryId)

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe 6L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `deleting a whole merge bumps the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertMerge(mergeId = 1L)
                driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.resetManga(version = 5L, lastModifiedAt = 10L)

                repository.deleteByMergeId(1L)

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe 6L
            } finally {
                driver.close()
            }
        }
    }

    private fun entry(
        id: Long = 0L,
        mangaId: Long = 2L,
        fetchChapterUpdates: Boolean = true,
        downloadChapters: Boolean = false,
        priority: Int = 1,
    ) = MergedEntry(
        id = id,
        mergeId = 1L,
        mangaId = mangaId,
        fetchChapterUpdates = fetchChapterUpdates,
        downloadChapters = downloadChapters,
        chapterPriority = priority,
    )

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.resetManga(version: Long, lastModifiedAt: Long) {
        execute(
            null,
            "UPDATE manga SET version = $version, last_modified_at = $lastModifiedAt WHERE id = 1",
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertManga(
        id: Long,
        sourceId: Long,
        url: String,
        version: Long = 1L,
        lastModifiedAt: Long = 1L,
    ) {
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
                123, $lastModifiedAt, $version, 0,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertMerge(mergeId: Long, sortMode: Int = 0) {
        execute(
            null,
            "INSERT INTO merge(manga_id, chapter_sort_mode, info_manga_id) VALUES ($mergeId, $sortMode, NULL)",
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertEntry(mergeId: Long, mangaId: Long, priority: Long): Long {
        execute(
            null,
            """
            INSERT INTO merged_entry(
                merge_id, manga_id, fetch_chapter_updates, download_chapters, chapter_priority
            ) VALUES (
                $mergeId, $mangaId, 1, 0, $priority
            )
            """.trimIndent(),
            0,
        ).await()
        return executeQuery(
            null,
            "SELECT last_insert_rowid()",
            { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
            },
            0,
        ).value!!
    }

    private companion object {
        const val SOURCE = 10L
        const val MERGED_SOURCE = 100L
    }
}
// KUN <--
