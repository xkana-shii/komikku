package tachiyomi.data.source

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import java.io.File
import java.nio.file.Files

class FeedSavedSearchRepositoryTest {
    private lateinit var file: File
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var searches: SavedSearchRepositoryImpl
    private lateinit var feeds: FeedSavedSearchRepositoryImpl

    @BeforeEach
    fun setup() {
        file = Files.createTempFile("saved-feeds", ".db").toFile()
        open(create = true)
    }

    private fun open(create: Boolean = false) {
        driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        if (create) Database.Schema.create(driver).value
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        val db = Database(driver = driver, historyAdapter = History.Adapter(DateColumnAdapter), mangasAdapter = Mangas.Adapter(genreAdapter = StringListColumnAdapter, update_strategyAdapter = UpdateStrategyColumnAdapter, memoAdapter = MemoColumnAdapter), chaptersAdapter = Chapters.Adapter(MemoColumnAdapter))
        val handler = AndroidDatabaseHandler(db, driver)
        searches = SavedSearchRepositoryImpl(handler)
        feeds = FeedSavedSearchRepositoryImpl(handler)
    }

    @AfterEach
    fun close() {
        driver.close()
        file.delete()
    }

    @Test
    fun `query default and filtered records resolve by saved ID and source and survive a reopened database`() = runBlocking {
        val values = listOf(
            SavedSearch(-1, 101, "Query", "title", null),
            SavedSearch(-1, 101, "Default", "", "[]"),
            SavedSearch(-1, 202, "Filtered", null, "[{\"_type\":\"CHECKBOX\",\"name\":\"Completed\",\"state\":\"true\"}]"),
        )
        // Offset feed IDs deliberately so confusing feed IDs with saved-search IDs cannot pass.
        feeds.insert(FeedSavedSearch(-1, 101, null, true, 0))
        val saved = values.map { it.copy(id = searches.insert(it)) }
        saved.forEach {
            feeds.insert(FeedSavedSearch(-1, it.source, it.id, true, 0))
            feeds.insert(FeedSavedSearch(-1, it.source, it.id, false, 0))
        }
        driver.close()
        open()
        feeds.getGlobalFeedSavedSearch().toSet() shouldBe saved.toSet()
        feeds.getBySourceIdFeedSavedSearch(101).toSet() shouldBe saved.take(2).toSet()
        feeds.getBySourceIdFeedSavedSearch(202) shouldBe listOf(saved.last())
    }

    @Test
    fun `edit keeps references and delete removes only the deleted saved search feeds`() = runBlocking {
        val first = SavedSearch(-1, 101, "First", "old", null).let { it.copy(id = searches.insert(it)) }
        val second = SavedSearch(-1, 101, "Second", "other", null).let { it.copy(id = searches.insert(it)) }
        for (search in listOf(first, second)) feeds.insert(FeedSavedSearch(-1, 101, search.id, true, 0))
        val edited = first.copy(name = "Renamed", query = "edited", filtersJson = "[]")
        searches.update(edited)
        feeds.getGlobalFeedSavedSearch().first { it.id == first.id } shouldBe edited
        feeds.getGlobal().map { it.savedSearch }.toSet() shouldBe setOf(first.id, second.id)
        searches.delete(first.id)
        feeds.getGlobalFeedSavedSearch() shouldBe listOf(second)
        feeds.getGlobal().single().savedSearch shouldBe second.id
    }
}
