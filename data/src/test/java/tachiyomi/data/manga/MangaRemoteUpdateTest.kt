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
            val before = database.mangaQueries.getMangaById(mangaId).awaitAsOne()

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

            val after = database.mangaQueries.getMangaById(mangaId).awaitAsOne()
            check(after.remote_title == "New title" && after.remote_author == "New author")
            check(after.user_favorite_at == before.user_favorite_at)
            check(after.user_reader_flags == before.user_reader_flags)
            check(after.user_chapter_flags == before.user_chapter_flags)
            check(after.user_notes == before.user_notes)
            check(after.state_version == before.state_version)
        } finally {
            driver.close()
        }
    }
}
// KMK <--
