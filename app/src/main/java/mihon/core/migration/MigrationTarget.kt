// KUN -->
package mihon.core.migration

/**
 * The version `Migrator` has to migrate up to.
 *
 * This fork pins `versionCode`, and the migrator only runs `(old + 1)..new`, so using the app
 * version code as the target silently skips every migration added after that point. Tracking the
 * highest defined migration version keeps each one reachable exactly once, independently of the
 * app version code.
 */
fun nextMigrationTarget(applied: Int, migrations: Collection<Migration>): Int =
    maxOf(applied, migrations.maxOfOrNull { it.version }?.toInt() ?: 0)
// KUN <--
