package tachiyomi.data.chapter

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.insertTestManga
import tachiyomi.data.testDatabase
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterRemoteUpdate
import java.util.Properties

// KMK -->
class ChapterRemoteSyncTest {
    @Test
    fun `failed insert rolls back chapter removal`() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("foreign_keys", "true") })
        try {
            Database.Schema.create(driver).await()
            val database = testDatabase(driver)
            val mangaId = insertTestManga(database, "/manga")
            val repository = ChapterRepositoryImpl(database)
            val old = repository.addAll(listOf(Chapter.create().copy(mangaId = mangaId, url = "/old", name = "Old"))).single()
            driver.execute(
                null,
                "CREATE TRIGGER fail_insert BEFORE INSERT ON chapters WHEN NEW.url = '/failure' BEGIN SELECT RAISE(ABORT, 'test failure'); END",
                0,
            )

            val result = runCatching {
                repository.updateFromRemote(
                    removedIds = listOf(old.id),
                    added = listOf(Chapter.create().copy(mangaId = mangaId, url = "/failure", name = "Failure")),
                    updated = emptyList(),
                )
            }

            check(result.isFailure)
            check(repository.getChapterById(old.id)?.url == "/old")
            check(repository.getChapterByUrlAndMangaId("/failure", mangaId) == null)
        } finally {
            driver.close()
        }
    }

    @Test
    fun `remote sync preserves Komikku chapter fields`() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("foreign_keys", "true") })
        try {
            Database.Schema.create(driver).await()
            val database = testDatabase(driver)
            val mangaId = insertTestManga(database, "/manga")
            val repository = ChapterRepositoryImpl(database)
            val old = repository.addAll(listOf(Chapter.create().copy(mangaId = mangaId, url = "/old", name = "Old"))).single()
            val kept = repository.addAll(listOf(Chapter.create().copy(mangaId = mangaId, url = "/kept", name = "Kept", fillermark = true))).single()
            val memo = JsonObject(mapOf("note" to JsonPrimitive("retained")))

            val added = repository.updateFromRemote(
                removedIds = listOf(old.id),
                added = listOf(Chapter.create().copy(mangaId = mangaId, url = "/new", name = "New", fillermark = true, version = 7, memo = memo)),
                updated = listOf(
                    ChapterRemoteUpdate(
                        id = kept.id,
                        name = "Renamed",
                        scanlator = kept.scanlator,
                        chapterNumber = kept.chapterNumber,
                        dateUpload = null,
                        sourceOrder = kept.sourceOrder,
                        memo = kept.memo,
                    ),
                ),
            )

            check(repository.getChapterById(old.id) == null)
            check(added.single().let { it.fillermark && it.version == 7L && it.memo == memo })
            check(repository.getChapterById(kept.id)?.let { it.name == "Renamed" && it.fillermark } == true)
        } finally {
            driver.close()
        }
    }
}
// KMK <--
