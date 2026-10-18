package tachiyomi.data.source

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.testDatabase
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import java.io.File
import java.nio.file.Files
import java.util.Properties

// KMK -->
class FeedSavedSearchRepositoryTest {

    private lateinit var file: File
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var searches: SavedSearchRepositoryImpl
    private lateinit var feeds: FeedSavedSearchRepositoryImpl

    @BeforeEach
    fun setup() = runBlocking<Unit> {
        file = Files.createTempFile("saved-feeds", ".db").toFile()
        open(create = true)
    }

    private suspend fun open(create: Boolean = false) {
        driver = JdbcSqliteDriver(
            "jdbc:sqlite:${file.absolutePath}",
            Properties().apply {
                setProperty("foreign_keys", "true")
            },
        )

        if (create) {
            Database.Schema.create(driver).await()
        }

        driver.execute(null, "PRAGMA foreign_keys = ON", 0)

        val db = testDatabase(driver)

        searches = SavedSearchRepositoryImpl(db)
        feeds = FeedSavedSearchRepositoryImpl(db)
    }

    @AfterEach
    fun close() {
        driver.close()
        file.delete()
    }

    @Test
    fun `query default and filtered records resolve by saved ID and source and survive a reopened database`() = runBlocking<Unit> {
        val values = listOf(
            SavedSearch(-1L, 101L, "Query", "title", null),
            SavedSearch(-1L, 101L, "Default", "", "[]"),
            SavedSearch(
                -1L,
                202L,
                "Filtered",
                null,
                """[{"_type":"CHECKBOX","name":"Completed","state":"true"}]""",
            ),
        )

        // Ensure feed IDs cannot accidentally be confused
        // with saved-search IDs.
        feeds.insert(
            FeedSavedSearch(-1L, 101L, null, true, 0L),
        )

        val saved = values.map {
            it.copy(id = searches.insert(it))
        }

        saved.forEach {
            feeds.insert(
                FeedSavedSearch(-1L, it.source, it.id, true, 0L),
            )
            feeds.insert(
                FeedSavedSearch(-1L, it.source, it.id, false, 0L),
            )
        }

        driver.close()
        open()

        feeds.getGlobalFeedSavedSearch().toSet() shouldBe saved.toSet()

        feeds.getBySourceIdFeedSavedSearch(101L).toSet() shouldBe
            saved.take(2).toSet()

        feeds.getBySourceIdFeedSavedSearch(202L) shouldBe
            listOf(saved.last())
    }

    @Test
    fun `edit keeps references and delete removes only the deleted saved search feeds`() = runBlocking<Unit> {
        val first = SavedSearch(
            -1L,
            101L,
            "First",
            "old",
            null,
        ).let {
            it.copy(id = searches.insert(it))
        }

        val second = SavedSearch(
            -1L,
            101L,
            "Second",
            "other",
            null,
        ).let {
            it.copy(id = searches.insert(it))
        }

        for ((id) in listOf(first, second)) {
            feeds.insert(
                FeedSavedSearch(-1L, 101L, id, true, 0L),
            )
        }

        val edited = first.copy(
            name = "Renamed",
            query = "edited",
            filtersJson = "[]",
        )

        searches.update(edited)

        feeds.getGlobalFeedSavedSearch()
            .first { it.id == first.id } shouldBe edited

        feeds.getGlobal()
            .map { it.savedSearch }
            .toSet() shouldBe setOf(first.id, second.id)

        searches.delete(first.id)

        feeds.getGlobalFeedSavedSearch() shouldBe listOf(second)
        feeds.getGlobal().single().savedSearch shouldBe second.id
    }
}
// KMK <--
