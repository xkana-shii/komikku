// KUN -->
package mihon.core.migration

import io.mockk.mockkClass
import mihon.core.migration.migrations.CategoryPreferenceRepairMigration
import mihon.core.migration.migrations.ClearPagePreviewCacheMigration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarFile

/**
 * `nextMigrationTarget` targets the highest defined version and `VersionRangeMigrationStrategy` runs
 * only `(applied + 1)..target`, so two migrations that share a version silently drop the second one:
 * a migration added at a version the install already applied can never run again. That is exactly
 * what the `85f` collision between [ClearPagePreviewCacheMigration] and
 * [CategoryPreferenceRepairMigration] would have done — every install that had already applied `85f`
 * would have skipped the repair — and nothing but this test catches it.
 *
 * The migrations are enumerated from the compiled classes of `mihon.core.migration.migrations`, the
 * package every `@ContributesIntoSet(AppScope::class)` migration lives in, so a newly added
 * migration is covered without editing this test. Each one is instantiated with relaxed mocks for
 * its constructor parameters, since only `version` is read and every migration keeps its
 * dependencies to plain constructor assignments.
 *
 * `Migration.ALWAYS` (`-1f`) is exempt: `isAlways` migrations are collected by `isAlways` rather than
 * by version, so sharing that value is not a collision.
 */
class MigrationVersionUniquenessTest {

    @Test
    fun `no two migrations share a version unless they already shipped together`() {
        val migrations = migrations()

        // A scan that silently found nothing would otherwise pass: name what is expected instead.
        assertTrue(
            migrations.size >= 25,
            "expected every migration class in $PACKAGE, found " +
                migrations.map { it::class.simpleName },
        )
        // The pair whose collision motivated this guard, proving the scan instantiates what it reads.
        val classes = migrations.map { it::class.java }
        assertTrue(ClearPagePreviewCacheMigration::class.java in classes)
        assertTrue(CategoryPreferenceRepairMigration::class.java in classes)

        val duplicates = migrations
            .filterNot { it.isAlways }
            .groupBy { it.version }
            .filterValues { it.size > 1 }
            .mapValues { (_, group) -> group.mapTo(mutableSetOf()) { it::class.simpleName ?: "?" } }

        assertEquals(
            LEGACY_SHARED_VERSIONS,
            duplicates,
            "a new migration reuses a version another migration already has, so it never runs for " +
                "an install that applied that version (or a legacy collision was added/changed)",
        )
    }

    private fun migrations(): List<Migration> = compiledClassNames()
        .filter { it.endsWith(".class") && '$' !in it }
        .sorted()
        .mapNotNull { name ->
            val type = Class.forName(
                "$PACKAGE.${name.removeSuffix(".class")}",
                false,
                CategoryPreferenceRepairMigration::class.java.classLoader,
            )
            val constructor = type.declaredConstructors.firstOrNull() ?: return@mapNotNull null
            constructor.isAccessible = true
            val arguments = constructor.parameterTypes
                .map { mockkClass(it.kotlin, relaxed = true) }
                .toTypedArray()
            runCatching { constructor.newInstance(*arguments) as? Migration }.getOrNull()
        }

    /** Simple file names of the compiled classes of [PACKAGE], from a class directory or a jar. */
    private fun compiledClassNames(): List<String> {
        val location = File(
            CategoryPreferenceRepairMigration::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val packagePath = PACKAGE.replace('.', '/')
        if (location.isDirectory) {
            val packageDir = File(location, packagePath)
            assertTrue(packageDir.isDirectory, "cannot locate the compiled migrations at $packageDir")
            return packageDir.listFiles().orEmpty().map { it.name }
        }
        return JarFile(location).use { jar ->
            jar.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("$packagePath/") }
                .map { it.removePrefix("$packagePath/") }
                .filter { it.isNotEmpty() && it.none { char -> char == '/' } }
                .toList()
        }
    }

    private companion object {
        const val PACKAGE = "mihon.core.migration.migrations"

        /**
         * The version numbers that already shipped shared by more than one migration: Mihon/SY/
         * Komikku added these at a number an earlier migration had taken, so an install that had
         * already applied it skipped the newer one. They are recorded rather than fixed — renumbering
         * a shipped migration would make it run, or stop running, for existing installs — and a new
         * migration must never join them.
         */
        val LEGACY_SHARED_VERSIONS = mapOf(
            17f to setOf("DeleteOldMangaDexTracksMigration", "ResetReaderSettingsMigration"),
            18f to setOf("RemoveOldReaderThemeMigration", "RemoveShorterLibraryUpdatesMigration"),
            27f to setOf("ChangeMiuiExtensionInstallerMigration", "MoveSecureScreenSettingMigration"),
            66f to setOf("MoveCacheToDiskSettingMigration", "MoveEncryptionSettingsToAppStateMigration"),
            80f to setOf("DisabledRepoMigration", "SyncPrefKeyMigration"),
            83f to setOf("ContentWarningMigration", "MoveVerticalSeekbarSettingsMigration", "VerticalNavigatorMigration"),
        )
    }
}
// KUN <--
