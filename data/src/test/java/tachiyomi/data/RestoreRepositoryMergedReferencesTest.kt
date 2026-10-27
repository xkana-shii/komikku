// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.model.RestoredMergeSettings
import tachiyomi.domain.backup.model.RestoredMergedEntry
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergeSettings
import tachiyomi.domain.manga.model.MergedEntry
import tachiyomi.domain.manga.model.toMangaUpdate
import kotlin.time.Instant

/**
 * Merged-entry reconciliation.
 *
 * The `merge` and `merged_entry` tables are shared by every merge entry and live outside the manga
 * row, so a restore must only read and reconcile the entries of the merge it is restoring. A manual
 * restore or a losing sync payload only adds missing entries. A winning sync payload replaces the
 * merge's entries, but only when every incoming entry resolves to a local child: an unresolvable
 * entry means the payload was built against another library and must not delete anything.
 */
class RestoreRepositoryMergedReferencesTest {

    @Test
    fun `a winning payload updates, removes and inserts only this merge's entries`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertManga(id = 4L, sourceId = SOURCE, url = "/child/c")
                // A second merge whose entries must not be touched.
                driver.insertManga(id = 5L, sourceId = MERGED_SOURCE, url = "/merge2", version = 5L)
                driver.insertManga(id = 6L, sourceId = SOURCE, url = "/child/d")
                driver.insertMerge(mergeId = 1L, sortMode = MergeSettings.CHAPTER_SORT_NONE)
                driver.insertMerge(mergeId = 5L, sortMode = MergeSettings.CHAPTER_SORT_PRIORITY)
                driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.insertEntry(mergeId = 1L, mangaId = 3L, priority = 2)
                driver.insertEntry(mergeId = 5L, mangaId = 6L, priority = 1, downloadChapters = true)

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = true,
                    settings = RestoredMergeSettings(
                        chapterSortMode = MergeSettings.CHAPTER_SORT_PRIORITY,
                        infoMangaUrl = "/child/a",
                        infoMangaSourceId = SOURCE,
                    ),
                    entries = listOf(
                        // Existing child, new settings.
                        entry(mangaUrl = "/child/a", fetchChapterUpdates = false, priority = 5, downloadChapters = true),
                        // New child.
                        entry(mangaUrl = "/child/c"),
                    ),
                )

                val entries = entries(database, mergeId = 1L).associateBy { it.mangaId }
                entries.keys shouldBe setOf(2L, 4L)
                entries.getValue(2L).run {
                    fetchChapterUpdates shouldBe false
                    downloadChapters shouldBe true
                    chapterPriority shouldBe 5
                }
                entries.getValue(4L).chapterPriority shouldBe 1

                // The merge-level settings moved to the `merge` row.
                settings(database, mergeId = 1L).run {
                    chapterSortMode shouldBe MergeSettings.CHAPTER_SORT_PRIORITY
                    infoMangaId shouldBe 2L
                }

                // The other merge keeps its entry even though the payload does not mention it.
                entries(database, mergeId = 5L).map { it.mangaId } shouldBe listOf(6L)
                settings(database, mergeId = 5L).chapterSortMode shouldBe MergeSettings.CHAPTER_SORT_PRIORITY
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a winning empty payload removes this merge's entries only`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 5L, sourceId = MERGED_SOURCE, url = "/merge2", version = 5L)
                driver.insertManga(id = 6L, sourceId = SOURCE, url = "/child/d")
                driver.insertMerge(mergeId = 1L)
                driver.insertMerge(mergeId = 5L)
                driver.insertEntry(mergeId = 1L, mangaId = 2L)
                driver.insertEntry(mergeId = 5L, mangaId = 6L)

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = true,
                    entries = emptyList(),
                )

                entries(database, mergeId = 1L).shouldBeEmpty()
                entries(database, mergeId = 5L).map { it.mangaId } shouldBe listOf(6L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an unmarked winning empty payload preserves this merge's entries`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertMerge(mergeId = 1L)
                driver.insertEntry(mergeId = 1L, mangaId = 2L)

                // A legacy payload omits the merged-reference marker (proto 10001) entirely: an
                // empty list is "unknown", not "no children", so the local entry survives.
                restore(repository = repository, mergedMangaReferencesIncluded = null, entries = emptyList())

                entries(database, mergeId = 1L).map { it.mangaId } shouldBe listOf(2L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an unmarked winning payload updates and inserts but never deletes`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertManga(id = 4L, sourceId = SOURCE, url = "/child/c")
                driver.insertMerge(mergeId = 1L)
                driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.insertEntry(mergeId = 1L, mangaId = 3L, priority = 2)

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = null,
                    entries = listOf(
                        entry(mangaUrl = "/child/a", priority = 9),
                        entry(mangaUrl = "/child/c"),
                    ),
                )

                val restored = entries(database, mergeId = 1L).associateBy { it.mangaId }
                // The unmarked payload may refresh settings and add a child, but it cannot delete the
                // child it never knew about.
                restored.keys shouldBe setOf(2L, 3L, 4L)
                restored.getValue(2L).chapterPriority shouldBe 9
                restored.getValue(3L).chapterPriority shouldBe 2
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a losing payload only adds new resolvable entries`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                // The local manga is newer than the payload.
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 6L, lastModifiedAt = 20L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertManga(id = 4L, sourceId = SOURCE, url = "/child/c")
                driver.insertMerge(mergeId = 1L, sortMode = MergeSettings.CHAPTER_SORT_NONE)
                driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.insertEntry(mergeId = 1L, mangaId = 3L, priority = 2)

                restore(
                    repository = repository,
                    version = 5L,
                    lastModifiedAt = 10L,
                    settings = RestoredMergeSettings(
                        chapterSortMode = MergeSettings.CHAPTER_SORT_MOST_CHAPTERS,
                        infoMangaUrl = "/child/a",
                        infoMangaSourceId = SOURCE,
                    ),
                    entries = listOf(
                        entry(mangaUrl = "/child/a", priority = 9),
                        entry(mangaUrl = "/child/c"),
                    ),
                )

                val restored = entries(database, mergeId = 1L).associateBy { it.mangaId }
                restored.keys shouldBe setOf(2L, 3L, 4L)
                // A stale payload must not rewrite the settings the user has since edited.
                restored.getValue(2L).chapterPriority shouldBe 1
                restored.getValue(3L).chapterPriority shouldBe 2
                settings(database, mergeId = 1L).chapterSortMode shouldBe MergeSettings.CHAPTER_SORT_NONE
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a manual restore only adds new resolvable entries`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertManga(id = 4L, sourceId = SOURCE, url = "/child/c")
                driver.insertMerge(mergeId = 1L)
                driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.insertEntry(mergeId = 1L, mangaId = 3L, priority = 2)

                restore(
                    repository = repository,
                    isSync = false,
                    entries = listOf(
                        entry(mangaUrl = "/child/a", priority = 9),
                        entry(mangaUrl = "/child/c"),
                    ),
                )

                val restored = entries(database, mergeId = 1L).associateBy { it.mangaId }
                restored.keys shouldBe setOf(2L, 3L, 4L)
                restored.getValue(2L).chapterPriority shouldBe 1
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a winning payload refreshes the child id after the child was deleted and re-added`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertMerge(mergeId = 1L)
                driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                // The child was removed and added again as a new row.
                driver.execute(null, "DELETE FROM manga WHERE id = 2", 0).await()
                driver.insertManga(id = 7L, sourceId = SOURCE, url = "/child/a")

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = true,
                    entries = listOf(entry(mangaUrl = "/child/a", priority = 5)),
                )

                // The old entry went with its child, and the re-added child is the merge's only entry.
                val restored = entries(database, mergeId = 1L).single()
                restored.mangaId shouldBe 7L
                restored.chapterPriority shouldBe 5
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an unresolvable incoming entry prevents destructive changes`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertMerge(mergeId = 1L)
                driver.insertEntry(mergeId = 1L, mangaId = 2L, priority = 1)
                driver.insertEntry(mergeId = 1L, mangaId = 3L, priority = 2)

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = true,
                    entries = listOf(
                        entry(mangaUrl = "/child/a", priority = 9),
                        // This child does not exist locally, so the payload cannot authorise deletes.
                        entry(mangaUrl = "/child/missing"),
                    ),
                )

                val restored = entries(database, mergeId = 1L).associateBy { it.mangaId }
                restored.keys shouldBe setOf(2L, 3L)
                // Add-only: the resolvable entry keeps its local settings too.
                restored.getValue(2L).chapterPriority shouldBe 1
                restored.getValue(3L).chapterPriority shouldBe 2
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun restore(
        repository: RestoreRepositoryImpl,
        isSync: Boolean = true,
        version: Long = 5L,
        lastModifiedAt: Long = 10L,
        mergedMangaReferencesIncluded: Boolean? = null,
        settings: RestoredMergeSettings? = null,
        entries: List<RestoredMergedEntry>,
    ) {
        repository.restoreManga(
            entries = listOf(
                RestoredManga(
                    manga = manga(version = version, lastModifiedAt = lastModifiedAt),
                    chapters = emptyList(),
                    categoryIds = null,
                    history = emptyList(),
                    tracks = emptyList(),
                    excludedScanlators = emptyList(),
                    mergedEntries = entries,
                    mergeSettings = settings,
                    mergedMangaReferencesIncluded = mergedMangaReferencesIncluded,
                ),
            ),
            isSync = isSync,
            restoreCategories = false,
            restoreTracking = true,
            restoreHistory = true,
            restoreCustomInfo = true,
            removeMissingChaptersBefore = null,
            update = { it.toMangaUpdate() },
        )
    }

    private fun restoreRepository(database: Database) = RestoreRepositoryImpl(
        database = database,
        mangaRepository = MangaRepositoryImpl(database),
        chapterRepository = ChapterRepositoryImpl(database),
        trackRepository = TrackRepositoryImpl(database),
        mangaMetadataRepository = mockk(relaxed = true),
    )

    private suspend fun entries(database: Database, mergeId: Long): List<MergedEntry> = database.mergedQueries
        .selectEntriesByMergeId(mergeId, MergedMangaMapper::mapEntry)
        .awaitAsList()

    private suspend fun settings(database: Database, mergeId: Long): MergeSettings = database.mergedQueries
        .selectMerge(mergeId, MergedMangaMapper::mapMergeSettings)
        .awaitAsOneOrNull()!!

    private fun entry(
        mangaUrl: String,
        fetchChapterUpdates: Boolean = true,
        downloadChapters: Boolean = false,
        priority: Int = 1,
    ) = RestoredMergedEntry(
        mangaUrl = mangaUrl,
        mangaSourceId = SOURCE,
        fetchChapterUpdates = fetchChapterUpdates,
        downloadChapters = downloadChapters,
        chapterPriority = priority,
    )

    private fun manga(version: Long, lastModifiedAt: Long) = Manga.create().copy(
        id = 1L,
        source = MERGED_SOURCE,
        favorite = true,
        dateAdded = Instant.fromEpochMilliseconds(1_000),
        url = "/merge",
        ogTitle = "Merged",
        updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        initialized = true,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = 500L,
        version = version,
        notes = "",
        memo = JsonObject(emptyMap()),
    )

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
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
            "INSERT INTO merge(manga_id, chapter_sort_mode, info_manga_id) VALUES ($mergeId, $sortMode, 2)",
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertEntry(
        mergeId: Long,
        mangaId: Long,
        priority: Long = 1L,
        downloadChapters: Boolean = false,
    ) {
        execute(
            null,
            """
            INSERT INTO merged_entry(
                merge_id, manga_id, fetch_chapter_updates, download_chapters, chapter_priority
            ) VALUES (
                $mergeId, $mangaId, 1, ${if (downloadChapters) 1 else 0}, $priority
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private companion object {
        const val SOURCE = 10L
        const val MERGED_SOURCE = 100L
    }
}
// KUN <--
