package tachiyomi.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.Properties

// KMK -->
class DatabaseInsertTest {
    @Test
    fun `async insert IDs and bulk writes preserve relationships`() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("foreign_keys", "true") })
        try {
            Database.Schema.create(driver).await()
            verifyInsertIds(testDatabase(driver))
        } finally {
            driver.close()
        }
    }
}
// KMK <--
