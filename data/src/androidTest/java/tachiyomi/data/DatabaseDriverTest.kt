package tachiyomi.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Test
import org.junit.runner.RunWith

// KMK -->
@RunWith(AndroidJUnit4::class)
class DatabaseDriverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledFreshDatabaseAndReturnedIds() = runBlocking<Unit> {
        val name = "bundled-insert-test.db"
        context.deleteDatabase(name)
        val driver = bundled(name)
        try {
            verifyInsertIds(testDatabase(driver))
        } finally {
            driver.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun existingSynchronousDatabaseOpensWithBundledDriver() = runBlocking<Unit> {
        val name = "driver-upgrade-test.db"
        context.deleteDatabase(name)
        val oldDriver = AndroidSqliteDriver(Database.Schema.synchronous(), context, name)
        val id = try {
            insertTestManga(testDatabase(oldDriver), "/existing")
        } finally {
            oldDriver.close()
        }
        val newDriver = bundled(name)
        try {
            check(testDatabase(newDriver).mangaQueries.getMangaById(id).awaitAsOne().url == "/existing")
        } finally {
            newDriver.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun sqlCipherCreatesReopensAndReturnsIds() = runBlocking<Unit> {
        val name = "encrypted-insert-test.db"
        context.deleteDatabase(name)
        System.loadLibrary("sqlcipher")
        var driver = encrypted(name)
        try {
            verifyInsertIds(testDatabase(driver))
            val id = insertTestManga(testDatabase(driver), "/encrypted")
            driver.close()
            driver = encrypted(name)
            check(testDatabase(driver).mangaQueries.getMangaById(id).awaitAsOne().url == "/encrypted")
        } finally {
            driver.close()
            context.deleteDatabase(name)
        }
    }

    private fun bundled(name: String): SqlDriver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = AndroidxSqliteDatabaseType.FileProvider(context, name),
        schema = Database.Schema,
        configuration = AndroidxSqliteConfiguration(isForeignKeyConstraintsEnabled = true),
    )

    private fun encrypted(name: String): SqlDriver = AndroidSqliteDriver(
        schema = Database.Schema.synchronous(),
        context = context,
        name = name,
        factory = SupportOpenHelperFactory("test-only-password".toByteArray(), null, false, 25),
        callback = object : AndroidSqliteDriver.Callback(Database.Schema.synchronous()) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                for (pragma in listOf("foreign_keys = ON", "journal_mode = WAL", "synchronous = NORMAL")) {
                    db.query("PRAGMA $pragma").use { it.moveToFirst() }
                }
            }
        },
    )
}
// KMK <--
