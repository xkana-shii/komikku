package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.insertTestManga
import tachiyomi.data.testDatabase
import tachiyomi.domain.manga.model.MangaRemoteUpdate

// KMK -->
class MangaRemoteUpdateTest {
    @Test
    fun `source metadata update leaves user-owned manga fields intact`() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            Database.Schema.create(driver).await()
            val database = testDatabase(driver)
            val mangaId = insertTestManga(database, "/manga")
            val before = database.mangasQueries.getMangaById(mangaId).awaitAsOne()

            check(
                MangaRepositoryImpl(database).updateRemote(
                    MangaRemoteUpdate(
                        id = mangaId,
                        title = "New title",
                        author = "New author",
                        artist = null,
                        description = null,
                        genre = null,
                        status = 1,
                        thumbnailUrl = null,
                        updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
                        memo = JsonObject(emptyMap()),
                        initialized = true,
                        coverLastModified = null,
                    ),
                ),
            )

            val after = database.mangasQueries.getMangaById(mangaId).awaitAsOne()
            check(after.title == "New title" && after.author == "New author")
            check(after.favorite == before.favorite)
            check(after.viewer == before.viewer)
            check(after.chapter_flags == before.chapter_flags)
            check(after.notes == before.notes)
            check(after.version == before.version)
        } finally {
            driver.close()
        }
    }
}
// KMK <--
