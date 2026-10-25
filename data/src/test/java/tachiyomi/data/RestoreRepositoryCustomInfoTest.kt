// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import kotlin.time.Instant

/**
 * Custom-info clearing semantics.
 *
 * The overrides live in the `user_*` columns of the manga row, next to the payload's per-manga
 * version. "Every override is null" therefore means two different things depending on whether the
 * sender included the section at all: an explicit clear (`customInfoIncluded = true`) or an older
 * peer that never spoke about custom info (marker absent). Only a marker-bearing payload that owns
 * the manga may remove the stored overrides; an unmarked, losing or disabled payload must never
 * clear them.
 */
class RestoreRepositoryCustomInfoTest {

    @Test
    fun `a marked winning all-null payload clears the stored override`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Stored")

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                )

                val stored = mangaRepository.getMangaById(1L)
                stored.userTitle shouldBe null
                stored.title shouldBe "Title"
                coVerify(exactly = 1) { mangaRepository.updateUserInfoAll(any()) }
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Old", userAuthor = "Stored author")

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    userTitle = "Custom",
                )

                val stored = mangaRepository.getMangaById(1L)
                stored.userTitle shouldBe "Custom"
                // A replace is not a merge: the omitted override is gone.
                stored.userAuthor shouldBe null
                stored.author shouldBe null
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a marked payload writes a blank title as no override`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Stored")

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    userTitle = "   ",
                )

                // A blank title must not blank the entry: it is not an override, so the source title
                // is shown again.
                val stored = mangaRepository.getMangaById(1L)
                stored.userTitle shouldBe null
                stored.title shouldBe "Title"
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Local title")

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = null,
                    userAuthor = "Incoming author",
                )

                // A partial legacy payload must add its field without dropping the local one.
                val stored = mangaRepository.getMangaById(1L)
                stored.userTitle shouldBe "Local title"
                stored.userAuthor shouldBe "Incoming author"
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(
                    version = 5L,
                    lastModifiedAt = 10L,
                    userTitle = "Local title",
                    userGenre = listOf("Action"),
                )

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = null,
                    userDescription = "Incoming description",
                )

                val stored = mangaRepository.getMangaById(1L)
                stored.userTitle shouldBe "Local title"
                stored.userDescription shouldBe "Incoming description"
                stored.userGenre shouldBe listOf("Action")
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Local title")

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = false,
                    userAuthor = "Incoming author",
                )

                val stored = mangaRepository.getMangaById(1L)
                stored.userTitle shouldBe "Local title"
                stored.userAuthor shouldBe "Incoming author"
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Local title")

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = null,
                )

                mangaRepository.getMangaById(1L).userTitle shouldBe "Local title"
                coVerify(exactly = 0) { mangaRepository.updateUserInfoAll(any()) }
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Local title")

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = false,
                )

                mangaRepository.getMangaById(1L).userTitle shouldBe "Local title"
                coVerify(exactly = 0) { mangaRepository.updateUserInfoAll(any()) }
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                // The local manga is newer, so the payload loses the comparison.
                driver.insertManga(version = 6L, lastModifiedAt = 20L, userTitle = "Stored")

                restore(
                    repository = repository,
                    isSync = true,
                    version = 5L,
                    lastModifiedAt = 10L,
                    customInfoIncluded = true,
                )

                mangaRepository.getMangaById(1L).userTitle shouldBe "Stored"
                coVerify(exactly = 0) { mangaRepository.updateUserInfoAll(any()) }
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Stored")

                restore(
                    repository = repository,
                    isSync = false,
                    customInfoIncluded = true,
                )

                mangaRepository.getMangaById(1L).userTitle shouldBe null
                coVerify(exactly = 1) { mangaRepository.updateUserInfoAll(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a marked but unchanged payload does not write the row again`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(
                    version = 5L,
                    lastModifiedAt = 10L,
                    userTitle = "Custom",
                    userAuthor = "Author",
                )

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    userTitle = "Custom",
                    userAuthor = "Author",
                )

                // Every synced manga carries the marker, so an unchanged value must not write (which
                // would bump the row's last_modified_at and re-upload it).
                coVerify(exactly = 0) { mangaRepository.updateUserInfoAll(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a marked blank value equal to no stored override does not write the row`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                // No stored override, and the incoming value normalizes to no override either.
                driver.insertManga(version = 5L, lastModifiedAt = 10L)

                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    userTitle = "  ",
                    userStatus = 0L,
                )

                coVerify(exactly = 0) { mangaRepository.updateUserInfoAll(any()) }
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a restored entry that is not in the database yet keeps the payload overrides`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)

                // Nothing inserted: the restore creates the row, so the overrides have to be written
                // after the insert assigns the id.
                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = true,
                    userTitle = "Custom",
                )

                val restored = mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1L)!!
                restored.userTitle shouldBe "Custom"
                restored.title shouldBe "Custom"
                restored.ogTitle shouldBe "Title"
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a restored entry that is not in the database yet takes an unmarked payload's overrides`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)

                // No stored row to merge with, so the partial payload's own overrides are all there is.
                restore(
                    repository = repository,
                    isSync = true,
                    customInfoIncluded = null,
                    userTitle = "Custom",
                )

                val restored = mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1L)!!
                restored.userTitle shouldBe "Custom"
                restored.ogTitle shouldBe "Title"
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
                val mangaRepository = spyk(MangaRepositoryImpl(database))
                val repository = restoreRepository(database, mangaRepository)
                driver.insertManga(version = 5L, lastModifiedAt = 10L, userTitle = "Stored")

                restore(
                    repository = repository,
                    isSync = true,
                    restoreCustomInfo = false,
                    customInfoIncluded = true,
                )

                mangaRepository.getMangaById(1L).userTitle shouldBe "Stored"
                coVerify(exactly = 0) { mangaRepository.updateUserInfoAll(any()) }
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
        userTitle: String? = null,
        userAuthor: String? = null,
        userStatus: Long? = null,
        userDescription: String? = null,
    ) {
        repository.restoreManga(
            entries = listOf(
                RestoredManga(
                    manga = manga(
                        version = version,
                        lastModifiedAt = lastModifiedAt,
                        userTitle = userTitle,
                        userAuthor = userAuthor,
                        userStatus = userStatus,
                        userDescription = userDescription,
                    ),
                    chapters = emptyList(),
                    categoryIds = null,
                    history = emptyList(),
                    tracks = emptyList(),
                    excludedScanlators = emptyList(),
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
        mangaRepository: MangaRepositoryImpl,
    ) = RestoreRepositoryImpl(
        database = database,
        mangaRepository = mangaRepository,
        chapterRepository = ChapterRepositoryImpl(database),
        trackRepository = TrackRepositoryImpl(database),
        mangaMetadataRepository = mockk(relaxed = true),
    )

    private fun manga(
        version: Long,
        lastModifiedAt: Long,
        userTitle: String? = null,
        userAuthor: String? = null,
        userStatus: Long? = null,
        userDescription: String? = null,
    ) = Manga.create().copy(
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
        userTitle = userTitle,
        userAuthor = userAuthor,
        userStatus = userStatus,
        userDescription = userDescription,
    )

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.insertManga(
        version: Long,
        lastModifiedAt: Long,
        userTitle: String? = null,
        userAuthor: String? = null,
        userGenre: List<String>? = null,
    ) {
        val title = userTitle?.let { "'$it'" } ?: "NULL"
        val author = userAuthor?.let { "'$it'" } ?: "NULL"
        val genre = userGenre?.let { "'${it.joinToString(", ")}'" } ?: "NULL"
        execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                remote_memo, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized, remote_cover,
                state_cover_last_modified, last_modified_at, version, is_syncing,
                user_favorite_at, favorite_modified_at,
                user_title, user_author, user_genre
            ) VALUES (
                1, 1, '/manga/1', 'Title', 0, 0,
                '{}', '', 0, 0,
                0, 1, 'https://example.com/cover.jpg',
                123, $lastModifiedAt, $version, 0,
                1000, 500,
                $title, $author, $genre
            )
            """.trimIndent(),
            0,
        ).await()
    }
}
// KUN <--
