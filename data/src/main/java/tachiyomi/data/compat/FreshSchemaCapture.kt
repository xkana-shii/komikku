package tachiyomi.data.compat

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import kotlinx.coroutines.runBlocking

// KNS -->
/**
 * Builds the current fresh schema into [scratch] (an empty, throwaway database) by running the
 * generated `Database.Schema.create`, and returns its `sqlite_master` rows. The converter uses
 * these as the target definitions, so it never carries its own copy of the DDL.
 */
object FreshSchemaCapture {

    fun capture(schema: SqlSchema<QueryResult.AsyncValue<Unit>>, scratch: RawSqlConnection): List<SchemaObject> {
        runBlocking { schema.create(ScratchDriver(scratch)).await() }
        return scratch.schemaObjects()
    }

    /** Forwards the parameterless statements of `Schema.create` to [scratch]. */
    private class ScratchDriver(private val scratch: RawSqlConnection) : SqlDriver {
        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            check(parameters == 0) { "Schema creation is not expected to bind parameters: $sql" }
            scratch.execute(sql)
            return QueryResult.Value(0L)
        }

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> = error("Schema creation does not query: $sql")

        override fun newTransaction(): QueryResult<Transacter.Transaction> = error("Not supported")
        override fun currentTransaction(): Transacter.Transaction? = null
        override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit
        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit
        override fun notifyListeners(vararg queryKeys: String) = Unit
        override fun close() = Unit
    }
}
// KNS <--
