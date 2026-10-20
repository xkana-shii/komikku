// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.sync.SyncRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import kotlin.time.Instant

/**
 * Guards scanlator filters in sync: the restore was insert-only (removing an exclusion never
 * propagated, and an empty incoming list could not clear the local set), and editing the child table
 * did not make the manga visible to delta sync.
 */
class RestoreRepositoryExcludedScanlatorsTest {

    @Test
    fun `a sync restore replaces add remove and clear the local exclusions`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga()

                restore(repository, listOf("A", "B"), isSync = true)
                mangaRepository.getExcludedScanlators(1L) shouldBe setOf("A", "B")

                restore(repository, listOf("B", "C"), isSync = true)
                mangaRepository.getExcludedScanlators(1L) shouldBe setOf("B", "C")

                // Clearing requires a strictly newer payload.
                restore(repository, emptyList(), isSync = true, version = 2L)
                mangaRepository.getExcludedScanlators(1L) shouldBe emptySet()
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a losing sync payload cannot delete local exclusions`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 6L, lastModifiedAt = 20L)
                driver.insertExcludedScanlator("A")

                // The incoming manga lost the version comparison, so its empty filter must not
                // erase the local edit.
                restore(repository, emptyList(), isSync = true, version = 5L, lastModifiedAt = 10L)

                mangaRepository.getExcludedScanlators(1L) shouldBe setOf("A")
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a winning empty sync payload still clears local exclusions`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)
                driver.insertExcludedScanlator("A")

                restore(repository, emptyList(), isSync = true, version = 6L, lastModifiedAt = 20L)

                mangaRepository.getExcludedScanlators(1L) shouldBe emptySet()
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a losing non-empty sync payload unions instead of replacing`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 6L, lastModifiedAt = 20L)
                driver.insertExcludedScanlator("A")

                restore(repository, listOf("B"), isSync = true, version = 5L, lastModifiedAt = 10L)

                // A stale payload may add an exclusion but never removes the local one.
                mangaRepository.getExcludedScanlators(1L) shouldBe setOf("A", "B")
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an ordinary restore still unions exclusions`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga()
                driver.insertExcludedScanlator("Local")

                restore(repository, listOf("Backup"), isSync = false)
                mangaRepository.getExcludedScanlators(1L) shouldBe setOf("Local", "Backup")

                // An older backup predates the local exclusion, so an empty list cannot delete it.
                restore(repository, emptyList(), isSync = false)
                mangaRepository.getExcludedScanlators(1L) shouldBe setOf("Local", "Backup")
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a user facing exclusion change bumps the manga sync state`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = MangaRepositoryImpl(database)
                driver.insertManga(version = 5L, lastModifiedAt = 100L)

                mangaRepository.setExcludedScanlators(1L, setOf("A"))

                mangaRepository.getExcludedScanlators(1L) shouldBe setOf("A")
                val updated = mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1L)!!
                updated.version shouldBe 6L
                updated.lastModifiedAt shouldBeGreaterThan 100L
                SyncRepositoryImpl(database)
                    .getMangasModifiedSince(since = 101L)
                    .map { it.id }
                    .shouldContainExactlyInAnyOrder(1L)

                // Re-applying the same filter is not a change and must not inflate the version.
                mangaRepository.setExcludedScanlators(1L, setOf("A"))
                mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1L)!!.version shouldBe 6L
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun restore(
        repository: RestoreRepositoryImpl,
        excludedScanlators: List<String>,
        isSync: Boolean,
        version: Long = 1L,
        lastModifiedAt: Long = 10L,
    ) {
        repository.restoreManga(
            entries = listOf(
                RestoredManga(
                    manga = manga(version = version, lastModifiedAt = lastModifiedAt),
                    chapters = emptyList(),
                    categoryIds = null,
                    history = emptyList(),
                    tracks = emptyList(),
                    excludedScanlators = excludedScanlators,
                ),
            ),
            isSync = isSync,
            restoreCategories = false,
            removeMissingChaptersBefore = null,
            update = { it.toMangaUpdate() },
        )
    }

    private fun restoreRepository(database: Database, mangaRepository: MangaRepositoryImpl) =
        RestoreRepositoryImpl(
            database = database,
            mangaRepository = mangaRepository,
            chapterRepository = ChapterRepositoryImpl(database),
            trackRepository = TrackRepositoryImpl(database),
            mangaMetadataRepository = mockk(relaxed = true),
            customMangaRepository = mockk(relaxed = true),
        )

    private fun manga(version: Long = 1L, lastModifiedAt: Long = 10L) = Manga.create().copy(
        id = 1L,
        source = 1L,
        favorite = true,
        dateAdded = Instant.fromEpochMilliseconds(1_000),
        url = "/manga/1",
        ogTitle = "Title",
        ogArtist = null,
        ogAuthor = null,
        ogThumbnailUrl = null,
        ogDescription = null,
        ogGenre = null,
        ogStatus = 0L,
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

    private suspend fun SqlDriver.insertManga(version: Long = 1L, lastModifiedAt: Long = 10L) {
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
                123, $lastModifiedAt, $version, 0,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.insertExcludedScanlator(scanlator: String) {
        execute(
            null,
            "INSERT INTO excluded_scanlator(manga_id, scanlator) VALUES (1, '$scanlator')",
            0,
        ).await()
    }
}
// KUN <--
