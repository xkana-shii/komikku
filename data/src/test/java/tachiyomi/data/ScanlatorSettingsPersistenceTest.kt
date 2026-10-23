package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.domain.manga.model.MangaUpdate
import kotlin.time.Instant

// KNS -->
/** The scanlator settings on the manga row: every Smart Merge choice persists, and refresh writes leave them. */
class ScanlatorSettingsPersistenceTest {

    private fun withRepository(block: suspend (MangaRepositoryImpl) -> Unit) = runBlocking {
        val driver = newDriver()
        try {
            driver.insertManga()
            block(MangaRepositoryImpl(DatabaseBindings.providesDatabase(driver)))
        } finally {
            driver.close()
        }
    }

    @Test
    fun `global, enabled and disabled all persist, in any order`() = withRepository { repository ->
        listOf(true, null, false, null, true, false, null).forEach { choice ->
            repository.update(MangaUpdate(1) { smartMergeOverride = choice }) shouldBe true
            repository.getMangaById(1).smartMergeOverride shouldBe choice
        }
    }

    @Test
    fun `other writes keep the Smart Merge choice, priority and range rules`() = withRepository { repository ->
        repository.update(
            MangaUpdate(1) {
                smartMergeOverride = null
                scanlatorPriority = listOf("Kaizen", "bloomingdale", "desire scans")
                scanlatorRangeRules = listOf("6.0:8.0:desire scans")
            },
        )
        // What a manga refresh, a library update and the reader write: none of the scanlator settings.
        repository.update(MangaUpdate(1) { lastUpdate = Instant.fromEpochMilliseconds(5_000) })
        repository.update(MangaUpdate(1) { chapterFlags = 3 })
        repository.update(MangaUpdate(1) { favorite = true })

        val manga = repository.getMangaById(1)
        manga.smartMergeOverride shouldBe null
        manga.scanlatorPriority shouldBe listOf("Kaizen", "bloomingdale", "desire scans")
        manga.scanlatorRangeRules shouldBe listOf("6.0:8.0:desire scans")
    }

    @Test
    fun `an explicit choice is not replaced by a write that does not set it`() = withRepository { repository ->
        repository.update(MangaUpdate(1) { smartMergeOverride = false })
        repository.update(MangaUpdate(1) { scanlatorPriority = listOf("A") })
        repository.getMangaById(1).smartMergeOverride shouldBe false
    }

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.insertManga(isSyncing: Int = 0) {
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
                0, 1, 'https://old.example/cover.jpg',
                123, 0, 1, $isSyncing,
                1000, 500
            )
            """.trimIndent(),
            0,
        ).await()
    }

    private fun SqlDriver.scalarLong(sql: String): Long? =
        executeQuery(
            null,
            sql,
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
            0,
        ).value

    private fun SqlDriver.scalarString(sql: String): String? =
        executeQuery(
            null,
            sql,
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            0,
        ).value
}
// KNS <--
