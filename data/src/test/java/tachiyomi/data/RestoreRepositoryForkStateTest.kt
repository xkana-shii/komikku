package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.backup.model.RestoredForkState
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import kotlin.time.Instant

// KNS -->
/**
 * Restore of the fork-owned state (chapter blacklist, scanlator rules, Smart Merge override, bookmark
 * colors). It follows Komi-kun's rules for excluded scanlators: a winning sync payload is
 * authoritative, a stale one can only add, an ordinary backup only adds blacklist entries, and a
 * payload that does not carry the state never touches it.
 */
class RestoreRepositoryForkStateTest {

    private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val database = DatabaseBindings.providesDatabase(driver)
    private val mangaRepository = MangaRepositoryImpl(database)
    private val chapterRepository = ChapterRepositoryImpl(database)
    private val repository = RestoreRepositoryImpl(
        database = database,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        trackRepository = TrackRepositoryImpl(database),
        mangaMetadataRepository = mockk(relaxed = true),
    )

    init {
        runBlocking { Database.Schema.create(driver).await() }
    }

    @AfterEach
    fun tearDown() = driver.close()

    @Test
    fun `a winning sync payload replaces the fork state, including clearing the override`() = runBlocking<Unit> {
        insertManga(version = 1, blacklist = "url:/local", priority = "Local", override = 1)

        restore(
            isSync = true,
            version = 2,
            state = state(blacklist = listOf("url:/remote"), priority = listOf("Remote"), override = null),
        )

        val manga = mangaRepository.getMangaById(1)
        manga.blacklistedChapters shouldBe listOf("url:/remote")
        manga.scanlatorPriority shouldBe listOf("Remote")
        manga.smartMergeOverride shouldBe null
    }

    @Test
    fun `a stale sync payload only adds blacklist entries and keeps local rules and override`() = runBlocking<Unit> {
        insertManga(version = 6, lastModifiedAt = 20, blacklist = "url:/local", priority = "Local", override = 0)

        restore(
            isSync = true,
            version = 5,
            lastModifiedAt = 10,
            state = state(blacklist = listOf("url:/remote"), priority = listOf("Remote"), override = true),
        )

        val manga = mangaRepository.getMangaById(1)
        manga.blacklistedChapters shouldContainExactlyInAnyOrder listOf("url:/local", "url:/remote")
        manga.scanlatorPriority shouldBe listOf("Local")
        manga.smartMergeOverride shouldBe false
    }

    @Test
    fun `an empty sync payload clears only when strictly newer`() = runBlocking<Unit> {
        insertManga(version = 1, blacklist = "url:/local", priority = "Local")

        restore(isSync = true, version = 1, state = state())
        mangaRepository.getMangaById(1).blacklistedChapters shouldBe listOf("url:/local")

        restore(isSync = true, version = 2, state = state())
        mangaRepository.getMangaById(1).blacklistedChapters shouldBe emptyList()
        mangaRepository.getMangaById(1).scanlatorPriority shouldBe emptyList()
    }

    @Test
    fun `an ordinary restore adds blacklist entries and applies the rules it carries`() = runBlocking<Unit> {
        insertManga(version = 9, blacklist = "url:/local", priority = "Local")

        restore(
            isSync = false,
            version = 1,
            state = state(blacklist = listOf("url:/backup"), priority = listOf("Backup"), override = true),
        )

        val manga = mangaRepository.getMangaById(1)
        manga.blacklistedChapters shouldContainExactlyInAnyOrder listOf("url:/local", "url:/backup")
        manga.scanlatorPriority shouldBe listOf("Backup")
        manga.smartMergeOverride shouldBe true
    }

    @Test
    fun `a payload without fork state leaves everything untouched`() = runBlocking<Unit> {
        insertManga(version = 1, blacklist = "url:/local", priority = "Local", override = 1)

        restore(isSync = true, version = 2, state = null)

        val manga = mangaRepository.getMangaById(1)
        manga.blacklistedChapters shouldBe listOf("url:/local")
        manga.scanlatorPriority shouldBe listOf("Local")
        manga.smartMergeOverride shouldBe true
    }

    @Test
    fun `chapter colors follow the newer writer and never fall back to unknown`() = runBlocking<Unit> {
        insertManga(version = 1)
        insertChapter(url = "/a", bookmark = true, color = 2, version = 1)
        insertChapter(url = "/b", bookmark = true, color = 3, version = 5)
        insertChapter(url = "/c", bookmark = true, color = 4, version = 1)
        insertChapter(url = "/d", bookmark = true, color = 5, version = 1)

        restore(
            isSync = true,
            version = 1,
            chapters = listOf(
                // incoming newer: takes the incoming color
                chapter("/a", bookmark = true, color = 6, version = 2),
                // local newer: keeps the local color
                chapter("/b", bookmark = true, color = 1, version = 2),
                // incoming newer but color unknown (stripped): keeps the local color
                chapter("/c", bookmark = true, color = Chapter.BOOKMARK_COLOR_UNKNOWN, version = 2),
                // incoming newer and the bookmark was removed: no color
                chapter("/d", bookmark = false, color = Chapter.BOOKMARK_COLOR_UNKNOWN, version = 2),
                // a new chapter whose color is unknown is stored as an ordinary color
                chapter("/e", bookmark = true, color = Chapter.BOOKMARK_COLOR_UNKNOWN, version = 1),
            ),
        )

        colors() shouldBe mapOf("/a" to 6L, "/b" to 3L, "/c" to 4L, "/d" to 0L, "/e" to 0L)
    }

    @Test
    fun `an ordinary restore brings colors in but never resets a local one`() = runBlocking<Unit> {
        insertManga(version = 1)
        insertChapter(url = "/a", bookmark = true, color = 2, version = 1)
        insertChapter(url = "/b", bookmark = true, color = 0, version = 1)
        insertChapter(url = "/c", bookmark = true, color = 3, version = 1)

        restore(
            isSync = false,
            version = 1,
            chapters = listOf(
                chapter("/a", bookmark = true, color = 0, version = 1),
                chapter("/b", bookmark = true, color = 5, version = 1),
                chapter("/c", bookmark = true, color = Chapter.BOOKMARK_COLOR_UNKNOWN, version = 1),
            ),
        )

        colors() shouldBe mapOf("/a" to 2L, "/b" to 5L, "/c" to 3L)
    }

    // ---------------------------------------------------------------------------------------------

    private fun state(
        blacklist: List<String> = emptyList(),
        priority: List<String> = emptyList(),
        override: Boolean? = null,
        overridePresent: Boolean = true,
    ) = RestoredForkState(
        scanlatorSettingsPresent = true,
        blacklistedChapters = blacklist,
        scanlatorPriority = priority,
        scanlatorRangeRules = emptyList(),
        smartMergeOverridePresent = overridePresent,
        smartMergeOverride = override,
    )

    private suspend fun restore(
        isSync: Boolean,
        version: Long,
        lastModifiedAt: Long = 10L,
        state: RestoredForkState? = null,
        chapters: List<Chapter> = emptyList(),
    ) {
        repository.restoreManga(
            entries = listOf(
                RestoredManga(
                    manga = manga(version, lastModifiedAt),
                    chapters = chapters,
                    categoryIds = null,
                    history = emptyList(),
                    tracks = emptyList(),
                    excludedScanlators = emptyList(),
                    forkState = state,
                ),
            ),
            isSync = isSync,
            restoreCategories = false,
            removeMissingChaptersBefore = null,
            update = { it.toMangaUpdate() },
        )
    }

    private fun manga(version: Long, lastModifiedAt: Long) = Manga.create().copy(
        id = 1L,
        source = 1L,
        favorite = true,
        dateAdded = Instant.fromEpochMilliseconds(1_000),
        url = "/manga/1",
        ogTitle = "Title",
        updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        initialized = true,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = 500L,
        version = version,
        memo = JsonObject(emptyMap()),
    )

    private fun chapter(url: String, bookmark: Boolean, color: Int, version: Long) = Chapter.create().copy(
        url = url,
        name = url,
        chapterNumber = 1.0,
        bookmark = bookmark,
        bookmarkColor = color,
        version = version,
        lastModifiedAt = 10L,
        dateFetch = Instant.fromEpochMilliseconds(1),
    )

    private suspend fun insertManga(
        version: Long,
        lastModifiedAt: Long = 10L,
        blacklist: String = "",
        priority: String = "",
        override: Int? = null,
    ) {
        driver.execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                remote_memo, user_notes, user_reader_flags, user_chapter_flags,
                state_chapter_fetch_interval, state_initialized, last_modified_at, version, is_syncing,
                user_favorite_at, favorite_modified_at,
                user_blacklisted_chapters, user_scanlator_priority, user_smart_merge_override
            ) VALUES (
                1, 1, '/manga/1', 'Title', 0, 0,
                X'7B7D', '', 0, 0,
                0, 1, $lastModifiedAt, $version, 0,
                1000, 500,
                '$blacklist', '$priority', ${override ?: "NULL"}
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun insertChapter(url: String, bookmark: Boolean, color: Int, version: Long) {
        driver.execute(
            null,
            """
            INSERT INTO chapter(
                manga_id, remote_url, remote_name, remote_chapter_number, remote_order, remote_memo,
                user_read, user_bookmark, user_last_page_read, state_date_fetch, last_modified_at,
                version, user_bookmark_color
            ) VALUES (1, '$url', '$url', 1.0, 0, X'7B7D', 0, ${if (bookmark) 1 else 0}, 0, 1, 10, $version, $color)
            """.trimIndent(),
            0,
        ).await()
    }

    private fun colors(): Map<String, Long> = driver.executeQuery(
        null,
        "SELECT remote_url, user_bookmark_color FROM chapter ORDER BY remote_url",
        { cursor ->
            val result = mutableMapOf<String, Long>()
            while (cursor.next().value) result[cursor.getString(0)!!] = cursor.getLong(1)!!
            QueryResult.Value(result)
        },
        0,
    ).value
}
// KNS <--
