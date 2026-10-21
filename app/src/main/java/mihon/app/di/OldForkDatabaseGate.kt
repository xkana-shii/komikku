package mihon.app.di

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import exh.log.xLogE
import exh.log.xLogI
import tachiyomi.data.compat.FreshSchemaCapture
import tachiyomi.data.compat.OldForkDatabaseConverter
import tachiyomi.data.compat.RawSqlConnection

// KNS -->
/**
 * Converts a database left by the previous personal fork (see [OldForkDatabaseConverter]) the first
 * time a connection is opened, before the SQLDelight driver reads `user_version`. Its schema version
 * collides with this foundation's, so without this the driver would run no migration and the app
 * would fail on missing or renamed columns.
 *
 * Runs once per process; concurrent connection openings wait for it. A failed conversion rolls back
 * and is rethrown, so the old data stays intact instead of being opened with the wrong schema.
 */
internal class OldForkDatabaseGate(
    private val schema: SqlSchema<QueryResult.AsyncValue<Unit>>,
    private val onConverting: (Boolean) -> Unit,
) {
    @Volatile
    private var checked = false

    fun ensureConverted(connection: SQLiteConnection) {
        if (checked) return
        synchronized(this) {
            if (checked) return
            val db = AndroidxRawSql(connection)
            val converter = OldForkDatabaseConverter()
            if (converter.isOldForkLineage(db)) {
                onConverting(true)
                try {
                    val target = BundledSQLiteDriver().open(":memory:").let { scratch ->
                        try {
                            FreshSchemaCapture.capture(schema, AndroidxRawSql(scratch))
                        } finally {
                            scratch.close()
                        }
                    }
                    val report = converter.convert(db, target, schema.version)
                    xLogI("Converted the previous fork's database: $report")
                } catch (e: Throwable) {
                    xLogE("Converting the previous fork's database failed", e)
                    throw e
                } finally {
                    onConverting(false)
                }
            }
            checked = true
        }
    }

    private class AndroidxRawSql(private val connection: SQLiteConnection) : RawSqlConnection {
        override fun execute(sql: String) {
            connection.execSQL(sql)
        }

        override fun query(sql: String): List<List<Any?>> {
            return connection.prepare(sql).use { statement ->
                val rows = mutableListOf<List<Any?>>()
                while (statement.step()) {
                    rows += (0..<statement.getColumnCount()).map { index ->
                        when (statement.getColumnType(index)) {
                            SQLITE_DATA_NULL -> null
                            SQLITE_DATA_INTEGER -> statement.getLong(index)
                            SQLITE_DATA_FLOAT -> statement.getDouble(index)
                            SQLITE_DATA_BLOB -> statement.getBlob(index)
                            else -> statement.getText(index)
                        }
                    }
                }
                rows
            }
        }
    }
}
// KNS <--
