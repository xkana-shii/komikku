package tachiyomi.data.compat

// KNS -->
/**
 * The few raw SQL operations [OldForkDatabaseConverter] needs. It runs before the SQLDelight driver
 * is usable, on a plain connection: the app adapts androidx `SQLiteConnection`, tests adapt JDBC.
 */
interface RawSqlConnection {

    /** Executes one statement that returns no rows. */
    fun execute(sql: String)

    /** Runs a query; every row is returned as its column values (Long, Double, String, ByteArray or null). */
    fun query(sql: String): List<List<Any?>>
}

/** A row of `sqlite_master`. */
data class SchemaObject(
    val type: String,
    val name: String,
    val tableName: String,
    val sql: String,
)

internal fun RawSqlConnection.schemaObjects(): List<SchemaObject> {
    return query(
        "SELECT type, name, tbl_name, sql FROM sqlite_master " +
            "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'",
    ).map { SchemaObject(it[0] as String, it[1] as String, it[2] as String, it[3] as String) }
}

internal data class ColumnInfo(val name: String, val notNull: Boolean, val hasDefault: Boolean)

internal fun RawSqlConnection.columns(table: String): List<ColumnInfo> {
    return query("SELECT name, \"notnull\", dflt_value FROM pragma_table_info('$table')").map {
        ColumnInfo(name = it[0] as String, notNull = (it[1] as Long) != 0L, hasDefault = it[2] != null)
    }
}

internal fun RawSqlConnection.longValue(sql: String): Long? = query(sql).firstOrNull()?.firstOrNull() as Long?
// KNS <--
