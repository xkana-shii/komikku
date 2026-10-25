// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.model.toMangaUpdate
import kotlin.time.Instant

/**
 * Merged-entry reference reconciliation.
 *
 * The `merged` table is shared by every merge entry and lives outside the manga row, so a restore
 * must only read and reconcile the references of the entry it is restoring. A manual restore or a
 * losing sync payload only adds missing references. A winning sync payload replaces the entry's
 * references, but only when every incoming reference resolves to a local child: an unresolvable
 * reference means the payload was built against another library and must not delete anything.
 */
class RestoreRepositoryMergedReferencesTest {

    @Test
    fun `a winning payload updates, removes and inserts only this entry's references`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertManga(id = 4L, sourceId = SOURCE, url = "/child/c")
                // A second merge entry whose references must not be touched.
                driver.insertManga(id = 5L, sourceId = MERGED_SOURCE, url = "/merge2", version = 5L)
                driver.insertManga(id = 6L, sourceId = SOURCE, url = "/child/d")
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 2L,
                    mangaUrl = "/child/a",
                    isInfoManga = false,
                    getChapterUpdates = true,
                    chapterSortMode = 0,
                    chapterPriority = 1,
                    downloadChapters = false,
                )
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 3L,
                    mangaUrl = "/child/b",
                    isInfoManga = false,
                    getChapterUpdates = true,
                    chapterSortMode = 0,
                    chapterPriority = 2,
                    downloadChapters = false,
                )
                driver.insertReference(
                    mergeId = 5L,
                    mergeUrl = "/merge2",
                    mangaId = 6L,
                    mangaUrl = "/child/d",
                    isInfoManga = true,
                    getChapterUpdates = true,
                    chapterSortMode = 0,
                    chapterPriority = 1,
                    downloadChapters = true,
                )

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = true,
                    references = listOf(
                        // Existing child, new settings.
                        reference(
                            mangaUrl = "/child/a",
                            isInfoManga = true,
                            getChapterUpdates = false,
                            chapterSortMode = 1,
                            chapterPriority = 5,
                            downloadChapters = true,
                        ),
                        // New child.
                        reference(mangaUrl = "/child/c"),
                    ),
                )

                val refs = references(database, mergeId = 1L).associateBy { it.mangaUrl }
                refs.keys shouldBe setOf("/child/a", "/child/c")
                refs.getValue("/child/a").run {
                    isInfoManga shouldBe true
                    getChapterUpdates shouldBe false
                    chapterSortMode shouldBe 1
                    chapterPriority shouldBe 5
                    downloadChapters shouldBe true
                }
                refs.getValue("/child/c").mangaId shouldBe 4L

                // The other merge keeps its reference even though the payload does not mention it.
                val otherMerge = references(database, mergeId = 5L)
                otherMerge.map { it.mangaUrl } shouldBe listOf("/child/d")
                otherMerge.single().downloadChapters shouldBe true
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a winning empty payload removes this entry's references only`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 5L, sourceId = MERGED_SOURCE, url = "/merge2", version = 5L)
                driver.insertManga(id = 6L, sourceId = SOURCE, url = "/child/d")
                driver.insertReference(mergeId = 1L, mergeUrl = "/merge", mangaId = 2L, mangaUrl = "/child/a")
                driver.insertReference(
                    mergeId = 5L,
                    mergeUrl = "/merge2",
                    mangaId = 6L,
                    mangaUrl = "/child/d",
                )

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = true,
                    references = emptyList(),
                )

                references(database, mergeId = 1L).shouldBeEmpty()
                references(database, mergeId = 5L).map { it.mangaUrl } shouldBe listOf("/child/d")
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an unmarked winning empty payload preserves this entry's references`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertReference(mergeId = 1L, mergeUrl = "/merge", mangaId = 2L, mangaUrl = "/child/a")

                // A legacy payload omits the merged-reference marker (proto 10001) entirely: an
                // empty list is "unknown", not "no children", so the local reference survives.
                restore(repository = repository, mergedMangaReferencesIncluded = null, references = emptyList())

                references(database, mergeId = 1L).map { it.mangaUrl } shouldBe listOf("/child/a")
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
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 2L,
                    mangaUrl = "/child/a",
                    chapterPriority = 1,
                )
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 3L,
                    mangaUrl = "/child/b",
                    chapterPriority = 2,
                )

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = null,
                    references = listOf(
                        reference(mangaUrl = "/child/a", chapterPriority = 9),
                        reference(mangaUrl = "/child/c"),
                    ),
                )

                val refs = references(database, mergeId = 1L).associateBy { it.mangaUrl }
                // The unmarked payload may refresh settings and add a child, but it cannot delete the
                // child it never knew about.
                refs.keys shouldBe setOf("/child/a", "/child/b", "/child/c")
                refs.getValue("/child/a").chapterPriority shouldBe 9
                refs.getValue("/child/b").chapterPriority shouldBe 2
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a losing payload only adds new resolvable references`() {
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
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 2L,
                    mangaUrl = "/child/a",
                    chapterPriority = 1,
                )
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 3L,
                    mangaUrl = "/child/b",
                    chapterPriority = 2,
                )

                restore(
                    repository = repository,
                    version = 5L,
                    lastModifiedAt = 10L,
                    references = listOf(
                        reference(mangaUrl = "/child/a", chapterPriority = 9),
                        reference(mangaUrl = "/child/c"),
                    ),
                )

                val refs = references(database, mergeId = 1L).associateBy { it.mangaUrl }
                refs.keys shouldBe setOf("/child/a", "/child/b", "/child/c")
                // A stale payload must not rewrite the settings the user has since edited.
                refs.getValue("/child/a").chapterPriority shouldBe 1
                refs.getValue("/child/b").chapterPriority shouldBe 2
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a manual restore only adds new resolvable references`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertManga(id = 4L, sourceId = SOURCE, url = "/child/c")
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 2L,
                    mangaUrl = "/child/a",
                    chapterPriority = 1,
                )
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 3L,
                    mangaUrl = "/child/b",
                    chapterPriority = 2,
                )

                restore(
                    repository = repository,
                    isSync = false,
                    references = listOf(
                        reference(mangaUrl = "/child/a", chapterPriority = 9),
                        reference(mangaUrl = "/child/c"),
                    ),
                )

                val refs = references(database, mergeId = 1L).associateBy { it.mangaUrl }
                refs.keys shouldBe setOf("/child/a", "/child/b", "/child/c")
                refs.getValue("/child/a").chapterPriority shouldBe 1
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
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 2L,
                    mangaUrl = "/child/a",
                    chapterPriority = 1,
                )
                // The child was removed and added again as a new row; the FK nulled the old id.
                driver.execute(null, "DELETE FROM manga WHERE id = 2", 0).await()
                driver.execute(null, "UPDATE merged SET manga_id = NULL WHERE merge_id = 1", 0).await()
                driver.insertManga(id = 7L, sourceId = SOURCE, url = "/child/a")

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = true,
                    references = listOf(reference(mangaUrl = "/child/a", chapterPriority = 5)),
                )

                val ref = references(database, mergeId = 1L).single()
                // The reference now points at the re-added child, not at NULL or the stale id.
                ref.mangaId shouldBe 7L
                ref.chapterPriority shouldBe 5
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an unresolvable incoming reference prevents destructive changes`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = restoreRepository(database)
                driver.insertManga(id = 1L, sourceId = MERGED_SOURCE, url = "/merge", version = 5L)
                driver.insertManga(id = 2L, sourceId = SOURCE, url = "/child/a")
                driver.insertManga(id = 3L, sourceId = SOURCE, url = "/child/b")
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 2L,
                    mangaUrl = "/child/a",
                    chapterPriority = 1,
                )
                driver.insertReference(
                    mergeId = 1L,
                    mergeUrl = "/merge",
                    mangaId = 3L,
                    mangaUrl = "/child/b",
                    chapterPriority = 2,
                )

                restore(
                    repository = repository,
                    mergedMangaReferencesIncluded = true,
                    references = listOf(
                        reference(mangaUrl = "/child/a", chapterPriority = 9),
                        // This child does not exist locally, so the payload cannot authorise deletes.
                        reference(mangaUrl = "/child/missing"),
                    ),
                )

                val refs = references(database, mergeId = 1L).associateBy { it.mangaUrl }
                refs.keys shouldBe setOf("/child/a", "/child/b")
                refs.getValue("/child/a").chapterPriority shouldBe 1
                refs.getValue("/child/b").chapterPriority shouldBe 2
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
        references: List<MergedMangaReference>,
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
                    mergedMangaReferences = references,
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

    private suspend fun references(database: Database, mergeId: Long) = database.mergedQueries
        .selectByMergeId(mergeId, MergedMangaMapper::map)
        .awaitAsList()

    private fun reference(
        mangaUrl: String,
        isInfoManga: Boolean = false,
        getChapterUpdates: Boolean = true,
        chapterSortMode: Int = 0,
        chapterPriority: Int = 1,
        downloadChapters: Boolean = false,
    ) = MergedMangaReference(
        id = 0L,
        isInfoManga = isInfoManga,
        getChapterUpdates = getChapterUpdates,
        chapterSortMode = chapterSortMode,
        chapterPriority = chapterPriority,
        downloadChapters = downloadChapters,
        mergeId = 1L,
        mergeUrl = "/merge",
        mangaId = null,
        mangaUrl = mangaUrl,
        mangaSourceId = SOURCE,
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

    private suspend fun SqlDriver.insertReference(
        mergeId: Long,
        mergeUrl: String,
        mangaId: Long,
        mangaUrl: String,
        isInfoManga: Boolean = false,
        getChapterUpdates: Boolean = true,
        chapterSortMode: Int = 0,
        chapterPriority: Int = 1,
        downloadChapters: Boolean = false,
    ) {
        execute(
            null,
            """
            INSERT INTO merged(
                info_manga, get_chapter_updates, chapter_sort_mode, chapter_priority,
                download_chapters, merge_id, merge_url, manga_id, manga_url, manga_source
            ) VALUES (
                ${if (isInfoManga) 1 else 0}, ${if (getChapterUpdates) 1 else 0},
                $chapterSortMode, $chapterPriority, ${if (downloadChapters) 1 else 0},
                $mergeId, '$mergeUrl', $mangaId, '$mangaUrl', $SOURCE
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
