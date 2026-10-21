// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.manga.repository.CustomMangaRepository
import kotlin.time.Instant

/**
 * Custom-info clearing semantics.
 *
 * Custom info lives in `edits.json`, not in the manga row, and the payload is versioned per manga.
 * "Every override is null" therefore means two different things depending on whether the sender
 * included the section at all: an explicit clear (`customInfoIncluded = true`) or an older peer
 * that never spoke about custom info (marker absent). Only a marker-bearing payload that owns the
 * manga may remove the stored entry; an unmarked, losing or disabled payload must never clear it.
 */
class RestoreRepositoryCustomInfoTest {

    @Test
    fun `a marked winning all-null payload clears the stored override`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(id = 1L, title = "Stored")
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null),
                )

                verify(exactly = 1) {
                    customMangaRepository.set(CustomMangaInfo(id = 1L, title = null))
                }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a marked winning payload replaces the overrides exactly`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(id = 1L, title = "Old")
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = "Custom"),
                )

                verify(exactly = 1) {
                    customMangaRepository.set(CustomMangaInfo(id = 1L, title = "Custom"))
                }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an unmarked payload merges with the stored overrides instead of replacing them`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(id = 1L, title = "Local title")
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = null,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null, author = "Incoming author"),
                )

                // A partial legacy payload must add its field without dropping the local one.
                verify(exactly = 1) {
                    customMangaRepository.set(
                        CustomMangaInfo(id = 1L, title = "Local title", author = "Incoming author"),
                    )
                }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an unmarked payload keeps the local genre when it omits the list`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(
                    id = 1L,
                    title = "Local title",
                    genre = listOf("Action"),
                )
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = null,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null, description = "Incoming description"),
                )

                verify(exactly = 1) {
                    customMangaRepository.set(
                        CustomMangaInfo(
                            id = 1L,
                            title = "Local title",
                            description = "Incoming description",
                            genre = listOf("Action"),
                        ),
                    )
                }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an explicitly excluded section merges into the stored overrides instead of replacing`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(id = 1L, title = "Local title")
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = false,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null, author = "Incoming author"),
                )

                verify(exactly = 1) {
                    customMangaRepository.set(
                        CustomMangaInfo(id = 1L, title = "Local title", author = "Incoming author"),
                    )
                }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an unmarked all-null payload never clears the stored override`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(id = 1L, title = "Local title")
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = null,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null),
                )

                verify(exactly = 0) { customMangaRepository.set(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an explicitly excluded section with all-null overrides never clears`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(id = 1L, title = "Local title")
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = false,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null),
                )

                verify(exactly = 0) { customMangaRepository.set(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a losing marked payload never clears the stored override`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                val repository = restoreRepository(database, customMangaRepository)
                // The local manga is newer, so the payload loses the comparison.
                driver.insertManga(version = 6L, lastModifiedAt = 20L)

                restore(
                    repository = repository,
                    isSync = true,
                    version = 5L,
                    lastModifiedAt = 10L,
                    customInfoIncluded = true,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null),
                )

                verify(exactly = 0) { customMangaRepository.set(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a marked manual restore applies the all-null clear`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(id = 1L, title = "Stored")
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = false,
                    customInfoIncluded = true,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null),
                )

                verify(exactly = 1) {
                    customMangaRepository.set(CustomMangaInfo(id = 1L, title = null))
                }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a marked but unchanged payload does not rewrite the custom info file`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                every { customMangaRepository.get(1L) } returns CustomMangaInfo(
                    id = 1L,
                    title = "Custom",
                    author = "Author",
                )
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = "Custom", author = "Author"),
                )

                // `set` rewrites the whole edits.json; every synced manga carries the marker, so an
                // unchanged value must not trigger a write.
                verify(exactly = 0) { customMangaRepository.set(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a marked blank value equal to no stored override does not rewrite the file`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                // No stored entry, and the incoming value normalizes to no override either.
                every { customMangaRepository.get(1L) } returns null
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = "  ", status = 0L),
                )

                verify(exactly = 0) { customMangaRepository.set(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `custom info off performs no write even when marked and winning`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val customMangaRepository = mockk<CustomMangaRepository>(relaxed = true)
                val repository = restoreRepository(database, customMangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    restoreCustomInfo = false,
                    customInfoIncluded = true,
                    customMangaInfo = CustomMangaInfo(id = 0L, title = null),
                )

                verify(exactly = 0) { customMangaRepository.set(any()) }
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun restore(
        repository: RestoreRepositoryImpl,
        isSync: Boolean,
        version: Long = 5L,
        lastModifiedAt: Long = 10L,
        restoreCustomInfo: Boolean = true,
        customInfoIncluded: Boolean? = null,
        customMangaInfo: CustomMangaInfo? = null,
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
                    customMangaInfo = customMangaInfo,
                    customInfoIncluded = customInfoIncluded,
                ),
            ),
            isSync = isSync,
            restoreCategories = false,
            restoreTracking = true,
            restoreHistory = true,
            restoreCustomInfo = restoreCustomInfo,
            removeMissingChaptersBefore = null,
            update = { it.toMangaUpdate() },
        )
    }

    private fun restoreRepository(
        database: Database,
        customMangaRepository: CustomMangaRepository,
    ) = RestoreRepositoryImpl(
        database = database,
        mangaRepository = MangaRepositoryImpl(database),
        chapterRepository = ChapterRepositoryImpl(database),
        trackRepository = TrackRepositoryImpl(database),
        mangaMetadataRepository = mockk(relaxed = true),
        customMangaRepository = customMangaRepository,
    )

    private fun manga(version: Long, lastModifiedAt: Long) = Manga.create().copy(
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

    private suspend fun SqlDriver.insertManga(version: Long, lastModifiedAt: Long) {
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
}
// KUN <--
