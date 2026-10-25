// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import io.mockk.mockk
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
 * Guards the clearable remote metadata on a restore. `updateFromBackup` used to write
 * `coalesce(:field, remote_field)` for artist/author/description/cover, so an incoming newer payload
 * with an explicit null (the source no longer reports the value) could never clear the stale local
 * value. The local-wins branch must still keep the local raw values.
 */
class RestoreRepositoryMangaFieldsTest {

    @Test
    fun `a newer payload can clear the remote artist`() {
        runBlocking {
            val restored = restoreAndLoad(incoming(version = 2, lastModifiedAt = 20, artist = null))

            restored.ogArtist shouldBe null
            restored.ogAuthor shouldBe LOCAL_AUTHOR
            restored.ogDescription shouldBe LOCAL_DESCRIPTION
            restored.ogThumbnailUrl shouldBe LOCAL_COVER
        }
    }

    @Test
    fun `a newer payload can clear the remote author`() {
        runBlocking {
            val restored = restoreAndLoad(incoming(version = 2, lastModifiedAt = 20, author = null))

            restored.ogArtist shouldBe LOCAL_ARTIST
            restored.ogAuthor shouldBe null
            restored.ogDescription shouldBe LOCAL_DESCRIPTION
            restored.ogThumbnailUrl shouldBe LOCAL_COVER
        }
    }

    @Test
    fun `a newer payload can clear the remote description`() {
        runBlocking {
            val restored = restoreAndLoad(incoming(version = 2, lastModifiedAt = 20, description = null))

            restored.ogArtist shouldBe LOCAL_ARTIST
            restored.ogAuthor shouldBe LOCAL_AUTHOR
            restored.ogDescription shouldBe null
            restored.ogThumbnailUrl shouldBe LOCAL_COVER
        }
    }

    @Test
    fun `a newer payload can clear the remote cover`() {
        runBlocking {
            val restored = restoreAndLoad(incoming(version = 2, lastModifiedAt = 20, cover = null))

            restored.ogArtist shouldBe LOCAL_ARTIST
            restored.ogAuthor shouldBe LOCAL_AUTHOR
            restored.ogDescription shouldBe LOCAL_DESCRIPTION
            restored.ogThumbnailUrl shouldBe null
        }
    }

    @Test
    fun `a newer local copy keeps its remote fields against an older payload`() {
        runBlocking {
            // The incoming payload is older, so the local-wins branch runs and copies the local raw
            // fields back into the update. Nulls in the older payload must not erase them.
            val restored = restoreAndLoad(
                incoming(
                    version = 1,
                    lastModifiedAt = 5,
                    artist = "Incoming artist",
                    author = "Incoming author",
                    description = "Incoming description",
                    cover = "https://example.com/incoming.jpg",
                ),
            )

            restored.ogArtist shouldBe LOCAL_ARTIST
            restored.ogAuthor shouldBe LOCAL_AUTHOR
            restored.ogDescription shouldBe LOCAL_DESCRIPTION
            restored.ogThumbnailUrl shouldBe LOCAL_COVER
        }
    }

    private suspend fun restoreAndLoad(manga: Manga): Manga {
        val driver = newDriver()
        return try {
            val database = DatabaseBindings.providesDatabase(driver)
            val mangaRepository = MangaRepositoryImpl(database)
            val repository = RestoreRepositoryImpl(
                database = database,
                mangaRepository = mangaRepository,
                chapterRepository = ChapterRepositoryImpl(database),
                trackRepository = TrackRepositoryImpl(database),
                mangaMetadataRepository = mockk(relaxed = true),
                customMangaRepository = mockk(relaxed = true),
            )

            driver.insertManga()
            repository.restoreManga(
                entries = listOf(
                    RestoredManga(
                        manga = manga,
                        chapters = emptyList(),
                        categoryIds = null,
                        history = emptyList(),
                        tracks = emptyList(),
                        excludedScanlators = emptyList(),
                    ),
                ),
                // The local-wins test needs an older incoming copy to lose on version; a sync restore
                // also covers the flags that guard trigger writes.
                isSync = true,
                restoreCategories = false,
                removeMissingChaptersBefore = null,
                update = { it.toMangaUpdate() },
            )

            mangaRepository.getMangaByUrlAndSourceId("/manga/1", 1)!!
        } finally {
            driver.close()
        }
    }

    private fun incoming(
        version: Long,
        lastModifiedAt: Long,
        artist: String? = LOCAL_ARTIST,
        author: String? = LOCAL_AUTHOR,
        description: String? = LOCAL_DESCRIPTION,
        cover: String? = LOCAL_COVER,
    ) = Manga.create().copy(
        id = 1L,
        source = 1L,
        favorite = true,
        dateAdded = Instant.fromEpochMilliseconds(1_000),
        url = "/manga/1",
        ogTitle = "Title",
        ogArtist = artist,
        ogAuthor = author,
        ogThumbnailUrl = cover,
        ogDescription = description,
        ogGenre = listOf("Drama"),
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

    private suspend fun SqlDriver.insertManga() {
        execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title, remote_author, remote_artist,
                remote_description, remote_status, remote_update_strategy, remote_memo,
                user_notes, user_reader_flags, user_chapter_flags, state_chapter_fetch_interval,
                state_initialized, remote_cover, state_cover_last_modified, last_modified_at,
                version, is_syncing, user_favorite_at, favorite_modified_at
            ) VALUES (
                1, 1, '/manga/1', 'Title', '$LOCAL_AUTHOR', '$LOCAL_ARTIST',
                '$LOCAL_DESCRIPTION', 0, 0, '{}',
                '', 0, 0, 0,
                1, '$LOCAL_COVER', 123, 10,
                1, 0, 1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private companion object {
        const val LOCAL_ARTIST = "Local artist"
        const val LOCAL_AUTHOR = "Local author"
        const val LOCAL_DESCRIPTION = "Local description"
        const val LOCAL_COVER = "https://example.com/local.jpg"
    }
}
// KUN <--
