// KUN -->
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.sync.SyncRepositoryImpl

/**
 * Guards the fallback for rows inserted while the `insert_last_favorited_at_mangas` trigger was
 * missing: they can be currently favorited (`user_favorite_at` set) with a NULL
 * `favorite_modified_at`, and must still be uploaded by both the full and delta sync queries.
 */
class SyncRepositoryFavoriteTimestampTest {

    @Test
    fun `a currently favorited row without a favorite timestamp is uploaded`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = SyncRepositoryImpl(database)

                driver.insertManga(
                    id = 1L,
                    userFavoriteAt = 1_000L,
                    favoriteModifiedAt = 500L,
                    lastModifiedAt = 400L,
                )
                // Simulate a row written by a build whose insert trigger was missing.
                driver.execute(null, "UPDATE manga SET favorite_modified_at = NULL WHERE id = 1", 0).await()

                repository.getMangasToSync(removedSince = 0L).map { it.id } shouldContainExactly listOf(1L)
                repository.getMangasModifiedSince(since = 300L).map { it.id } shouldContainExactly listOf(1L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `a removed favorite is still uploaded`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = SyncRepositoryImpl(database)

                driver.insertManga(
                    id = 2L,
                    userFavoriteAt = null,
                    favoriteModifiedAt = 1_000L,
                    lastModifiedAt = 400L,
                )

                repository.getMangasToSync(removedSince = 0L).map { it.id } shouldContainExactly listOf(2L)
                repository.getMangasModifiedSince(since = 300L).map { it.id } shouldContainExactly listOf(2L)
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `an entry that was never in the library is not uploaded`() {
        runBlocking {
            val driver = newDriver()
            try {
                val database = DatabaseBindings.providesDatabase(driver)
                val repository = SyncRepositoryImpl(database)

                driver.insertManga(
                    id = 3L,
                    userFavoriteAt = null,
                    favoriteModifiedAt = null,
                    lastModifiedAt = 400L,
                )

                repository.getMangasToSync(removedSince = 0L) shouldContainExactly emptyList()
                repository.getMangasModifiedSince(since = 300L) shouldContainExactly emptyList()
            } finally {
                driver.close()
            }
        }
    }

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private suspend fun SqlDriver.insertManga(
        id: Long,
        userFavoriteAt: Long?,
        favoriteModifiedAt: Long?,
        lastModifiedAt: Long,
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
                $id, 1, '/manga/$id', 'Title', 0, 0,
                '{}', '', 0, 0,
                0, 1, 'https://example.com/cover.jpg',
                123, $lastModifiedAt, 1, 0,
                ${userFavoriteAt ?: "NULL"}, ${favoriteModifiedAt ?: "NULL"}
            )
            """.trimIndent(),
            0,
        ).await()
    }
}
// KUN <--
