package tachiyomi.data

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.data.source.SavedSearchRepositoryImpl
import tachiyomi.domain.source.model.SavedSearch

// KNS -->
/** Verifies the database write and the explicit live-feed invalidation travel together. */
class SavedSearchLiveUpdateTest {

    private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val repository = SavedSearchRepositoryImpl(DatabaseBindings.providesDatabase(driver))

    init {
        runBlocking { Database.Schema.create(driver).await() }
    }

    @AfterEach
    fun tearDown() = driver.close()

    @Test
    fun `in-place replacement publishes the persisted search configuration immediately`() = runBlocking {
        val id = repository.insert(SavedSearch(-1, 42, "Feed search", "old", "sort=popular"))
        val replacement = SavedSearch(id, 42, "Feed search", "new", "sort=latest;language=ja")
        val updateEvent = async(start = CoroutineStart.UNDISPATCHED) {
            repository.getUpdatesAsFlow().first()
        }

        repository.update(replacement)

        updateEvent.await() shouldBe replacement
        repository.getById(id) shouldBe replacement
        repository.getAllAsFlow().first() shouldBe listOf(replacement)
    }
}
// KNS <--
