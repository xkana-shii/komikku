package tachiyomi.data.compat

import tachiyomi.data.StringListColumnAdapter
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.service.legacyFillermarkBlacklistKeys

// KNS -->
/**
 * Converts a database of the previous personal fork into the current schema.
 *
 * The personal fork reworked the schema with its own migrations (`47.sqm`–`56.sqm`, user_version 57)
 * while this foundation uses Komi-kun's (`47.sqm`–`56.sqm` with different SQL). Both end up with the
 * same tables and mostly the same columns, but the version numbers collide, so the SQLDelight driver
 * would open an old-fork database as if it were current and later fail on missing columns. This runs
 * on the raw connection before the driver checks the version:
 *
 * - Every table of the current schema is rebuilt from its fresh definition; columns present on both
 *   sides are copied, and the columns that differ are mapped explicitly ([columnSources]).
 * - Tables, views, triggers and indexes that only the old fork had are dropped; the current ones are
 *   recreated from their fresh definitions.
 * - Legacy Fillermarks become exact chapter blacklist keys, as the old fork's own migration did.
 * - Categories get the deterministic sync uid the foundation uses (roots by name, children below
 *   their parent), since the old fork had no category uid.
 * - `user_version` is set to the current schema version, so no foundation migration runs on top.
 *
 * Nothing is deleted from user data: rows are copied table by table inside one transaction, which
 * rolls back completely on any error.
 */
class OldForkDatabaseConverter(private val now: () -> Long = System::currentTimeMillis) {

    data class Report(
        val fromVersion: Long,
        val toVersion: Long,
        val rebuiltTables: List<String>,
        val droppedTables: List<String>,
        val convertedFillermarks: Int,
        val foreignKeyViolations: Int,
    )

    /**
     * The old fork renamed the SyncYomi columns to `state_*` in its schema rework, so `chapter` having
     * `state_version` identifies its lineage at every version from its `47.sqm` on.
     */
    fun isOldForkLineage(db: RawSqlConnection): Boolean {
        return db.columns("chapter").any { it.name == "state_version" }
    }

    fun convert(db: RawSqlConnection, target: List<SchemaObject>, targetVersion: Long): Report {
        val fromVersion = db.longValue("PRAGMA user_version") ?: 0L
        db.execute("PRAGMA foreign_keys = OFF")
        // Renaming a table would otherwise rewrite the REFERENCES clauses of the tables that are
        // still waiting to be rebuilt; every table gets its fresh definition anyway.
        db.execute("PRAGMA legacy_alter_table = ON")
        try {
            db.execute("BEGIN IMMEDIATE")
            try {
                val report = convertInTransaction(db, target, fromVersion, targetVersion)
                db.execute("COMMIT")
                return report
            } catch (e: Throwable) {
                runCatching { db.execute("ROLLBACK") }
                throw e
            }
        } finally {
            db.execute("PRAGMA legacy_alter_table = OFF")
            db.execute("PRAGMA foreign_keys = ON")
        }
    }

    private fun convertInTransaction(
        db: RawSqlConnection,
        target: List<SchemaObject>,
        fromVersion: Long,
        targetVersion: Long,
    ): Report {
        val oldObjects = db.schemaObjects()
        val oldTables = oldObjects.filter { it.type == "table" }.map { it.name }.toSet()

        // Read before the chapter table is rebuilt without the column.
        val fillermarks = if (db.columns("chapter").any { it.name == "user_fillermark" }) {
            db.query("SELECT manga_id, remote_url FROM chapter WHERE user_fillermark = 1")
                .map { (it[0] as Long) to (it[1] as String) }
        } else {
            emptyList()
        }

        // Views and triggers reference columns that are about to change; indexes are recreated
        // from the fresh schema. Auto-indexes (sql IS NULL) belong to their tables.
        oldObjects.filter { it.type == "trigger" }.forEach { db.execute("DROP TRIGGER IF EXISTS ${it.name.quoted()}") }
        oldObjects.filter { it.type == "view" }.forEach { db.execute("DROP VIEW IF EXISTS ${it.name.quoted()}") }
        oldObjects.filter { it.type == "index" }.forEach { db.execute("DROP INDEX IF EXISTS ${it.name.quoted()}") }

        val targetTables = target.filter { it.type == "table" }
        val rebuilt = mutableListOf<String>()
        targetTables.forEach { table ->
            if (table.name in oldTables) {
                rebuildTable(db, table)
                rebuilt += table.name
            } else {
                db.execute(table.sql)
            }
        }
        val dropped = (oldTables - targetTables.map { it.name }.toSet()).sorted()
        dropped.forEach { db.execute("DROP TABLE IF EXISTS ${it.quoted()}") }

        // Before the triggers exist again: a conversion is not a user edit and must not bump sync
        // versions or timestamps.
        convertFillermarks(db, fillermarks)
        assignCategoryUids(db)

        listOf("index", "view", "trigger").forEach { type ->
            target.filter { it.type == type }.forEach { db.execute(it.sql) }
        }

        val violations = db.query("PRAGMA foreign_key_check").size
        db.execute("PRAGMA user_version = $targetVersion")

        return Report(
            fromVersion = fromVersion,
            toVersion = targetVersion,
            rebuiltTables = rebuilt,
            droppedTables = dropped,
            convertedFillermarks = fillermarks.size,
            foreignKeyViolations = violations,
        )
    }

    private fun rebuildTable(db: RawSqlConnection, table: SchemaObject) {
        val oldName = "kns_old_${table.name}"
        db.execute("ALTER TABLE ${table.name.quoted()} RENAME TO ${oldName.quoted()}")
        db.execute(table.sql)

        val oldColumns = db.columns(oldName).map { it.name }.toSet()
        val mappings = columnSources[table.name].orEmpty()
        val targetColumns = db.columns(table.name)
        val copied = targetColumns.mapNotNull { column ->
            val mapped = mappings[column.name]?.takeIf { oldColumns.containsAll(it.requires) }
            when {
                mapped != null -> column.name to mapped.expression
                column.name in oldColumns -> column.name to column.name.quoted()
                column.notNull && !column.hasDefault -> error(
                    "Cannot convert ${table.name}: required column ${column.name} has no source",
                )
                else -> null
            }
        }
        if (copied.isNotEmpty()) {
            db.execute(
                "INSERT INTO ${table.name.quoted()} (${copied.joinToString { it.first.quoted() }}) " +
                    "SELECT ${copied.joinToString { it.second }} FROM ${oldName.quoted()}",
            )
        }
        db.execute("DROP TABLE ${oldName.quoted()}")
    }

    private fun convertFillermarks(db: RawSqlConnection, fillermarks: List<Pair<Long, String>>) {
        fillermarks.groupBy({ it.first }, { it.second }).forEach { (mangaId, urls) ->
            val stored = db.query("SELECT user_blacklisted_chapters FROM manga WHERE id = $mangaId")
                .firstOrNull()?.firstOrNull() as String? ?: return@forEach
            val keys = (StringListColumnAdapter.decode(stored) + legacyFillermarkBlacklistKeys(urls)).distinct()
            db.execute(
                "UPDATE manga SET user_blacklisted_chapters = " +
                    "${StringListColumnAdapter.encode(keys).sqlString()} WHERE id = $mangaId",
            )
        }
    }

    private fun assignCategoryUids(db: RawSqlConnection) {
        data class Row(val id: Long, val name: String, val parentId: Long, val uid: Long)
        val rows = db.query("SELECT id, name, parent_id, uid FROM category WHERE id > 0").map {
            Row(it[0] as Long, it[1] as String, it[2] as Long, it[3] as Long)
        }
        val byId = rows.associateBy { it.id }
        val resolved = mutableMapOf<Long, Long>()
        fun uidOf(row: Row, depth: Int = 0): Long {
            resolved[row.id]?.let { return it }
            val parent = byId[row.parentId]?.takeIf { depth < rows.size }
            val uid = row.uid.takeIf { it != 0L }
                ?: Category.childUid(parent?.let { uidOf(it, depth + 1) } ?: 0L, row.name)
            resolved[row.id] = uid
            return uid
        }
        rows.forEach { row ->
            val uid = uidOf(row)
            if (uid != row.uid) {
                db.execute("UPDATE category SET uid = $uid WHERE id = ${row.id}")
            }
        }
        val stamp = now()
        db.execute("UPDATE category SET date_added = $stamp WHERE id > 0 AND date_added = 0")
    }

    private class ColumnSource(val expression: String, val requires: Set<String>)

    private fun mapped(expression: String, vararg requires: String) = ColumnSource(expression, requires.toSet())

    /**
     * Old-fork columns whose name or meaning differs from the current schema, per table. A mapping
     * applies only when the old table has every column it reads; otherwise the same-name column or
     * the column default is used.
     */
    private val columnSources: Map<String, Map<String, ColumnSource>> = mapOf(
        "manga" to mapOf(
            "version" to mapped("state_version", "state_version"),
            "last_modified_at" to mapped("state_last_modified_at", "state_last_modified_at"),
            "is_syncing" to mapped("0"),
            // Old fork: user_favorite_at was the favorite-change time and state_date_added the date
            // added. Here user_favorite_at is the date added (0 = unknown, as in Komi-kun's 49.sqm)
            // and favorite_modified_at the change time in epoch seconds.
            "user_favorite_at" to mapped(
                "CASE WHEN user_favorite_at IS NOT NULL THEN coalesce(state_date_added, 0) END",
                "user_favorite_at",
                "state_date_added",
            ),
            "favorite_modified_at" to mapped(
                "CASE WHEN user_favorite_at IS NULL THEN NULL " +
                    "WHEN user_favorite_at > 100000000000 THEN user_favorite_at / 1000 " +
                    "ELSE user_favorite_at END",
                "user_favorite_at",
            ),
            // Old fork: sorting at 0x200/0x400/0x600 and the Fillermark filter at 0x080/0x100.
            // Here (Mihon): sorting at 0x100/0x200/0x300 and no Fillermark filter.
            "user_chapter_flags" to mapped(
                "(user_chapter_flags & ~1920) | ((user_chapter_flags & 1536) >> 1)",
                "user_chapter_flags",
            ),
        ),
        "chapter" to mapOf(
            "version" to mapped("state_version", "state_version"),
            "last_modified_at" to mapped("state_last_modified_at", "state_last_modified_at"),
            "is_syncing" to mapped("0"),
        ),
        "manga_category" to mapOf(
            "last_modified_at" to mapped("state_last_modified_at", "state_last_modified_at"),
        ),
        "category" to mapOf(
            "is_syncing" to mapped("0"),
        ),
    )

    private fun String.quoted(): String = "\"" + replace("\"", "\"\"") + "\""

    private fun String.sqlString(): String = "'" + replace("'", "''") + "'"
}
// KNS <--
