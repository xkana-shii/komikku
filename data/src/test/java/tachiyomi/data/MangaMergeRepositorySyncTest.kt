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
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference

/**
 * A merged reference lives in the `merged` table, not in the merge parent's manga row, so inserting,
 * editing or deleting one fires no manga trigger. Without an explicit bump of the merge parent, the
 * delta sync would never upload the change. The restore path writes `merged` directly and must not
 * go through this repository (the `is_syncing` guard on `bumpSyncVersion` backs that up).
 */
class MangaMergeRepositorySyncTest {

    @Test
    fun `inserting a reference bumps the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")

                repository.insert(reference())

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe 6L
                (parent.lastModifiedAt > 10L) shouldBe true
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `inserting many references bumps each merge parent once`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")

                repository.insertAll(
                    listOf(
                        reference(mangaId = 2L, mangaUrl = "/child/a"),
                        reference(mangaId = 3L, mangaUrl = "/child/b"),
                    ),
                )

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                // Both references belong to the same merge, so it is bumped once, not twice.
                parent.version shouldBe 6L
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `updating reference settings bumps the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                val referenceId = driver.insertReference(mergeId = 1L, mangaId = 2L, mangaUrl = "/child/a")
                driver.resetManga(version = 5L, lastModifiedAt = 10L)

                val updated = repository.updateSettings(
                    MergeMangaSettingsUpdate(
                        id = referenceId,
                        isInfoManga = true,
                        getChapterUpdates = false,
                        chapterPriority = 5,
                        downloadChapters = true,
                        chapterSortMode = 1,
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
    fun `saving unchanged reference settings does not bump the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                val referenceId = driver.insertReference(mergeId = 1L, mangaId = 2L, mangaUrl = "/child/a")
                driver.resetManga(version = 5L, lastModifiedAt = 10L)
                val before = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()

                // Exactly the settings the reference already has.
                val updated = repository.updateSettings(
                    MergeMangaSettingsUpdate(
                        id = referenceId,
                        isInfoManga = false,
                        getChapterUpdates = true,
                        chapterPriority = 1,
                        downloadChapters = false,
                        chapterSortMode = 0,
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
    fun `deleting a reference bumps the merge parent`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = MangaMergeRepositoryImpl(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L, lastModifiedAt = 10L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                val referenceId = driver.insertReference(mergeId = 1L, mangaId = 2L, mangaUrl = "/child/a")
                driver.resetManga(version = 5L, lastModifiedAt = 10L)

                repository.deleteById(referenceId)

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
                driver.insertReference(mergeId = 1L, mangaId = 2L, mangaUrl = "/child/a")
                driver.resetManga(version = 5L, lastModifiedAt = 10L)

                repository.deleteByMergeId(1L)

                val parent = database.mangaQueries.getMangaById(1L, MangaMapper::mapManga).awaitAsOne()
                parent.version shouldBe 6L
            } finally {
                driver.close()
            }
        }
    }

    private fun reference(
        mangaId: Long? = 2L,
        mangaUrl: String = "/child/a",
    ) = MergedMangaReference(
        id = 0L,
        isInfoManga = false,
        getChapterUpdates = true,
        chapterSortMode = 0,
        chapterPriority = 1,
        downloadChapters = false,
        mergeId = 1L,
        mergeUrl = "/merge",
        mangaId = mangaId,
        mangaUrl = mangaUrl,
        mangaSourceId = SOURCE,
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

    private suspend fun SqlDriver.insertReference(
        mergeId: Long,
        mangaId: Long,
        mangaUrl: String,
    ): Long {
        execute(
            null,
            """
            INSERT INTO merged(
                info_manga, get_chapter_updates, chapter_sort_mode, chapter_priority,
                download_chapters, merge_id, merge_url, manga_id, manga_url, manga_source
            ) VALUES (
                0, 1, 0, 1, 0, $mergeId, '/merge', $mangaId, '$mangaUrl', $SOURCE
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
