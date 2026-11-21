// KUN -->
package mihon.core.migration

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class MigrationTargetTest {

    private fun migration(version: Float, ran: MutableList<Float> = mutableListOf()) = object : Migration {
        override val version = version
        override val isAlways = false
        override suspend fun invoke(migrationContext: MigrationContext): Boolean {
            ran += version
            return true
        }
    }

    @Test
    fun `a migration added after the pinned version code is still reachable`() {
        nextMigrationTarget(applied = 83, migrations = listOf(migration(84f))) shouldBe 84
    }

    @Test
    fun `an applied migration is not selected again`() {
        runBlocking {
            val ran = mutableListOf<Float>()
            val target = nextMigrationTarget(84, listOf(migration(84f, ran)))
            target shouldBe 84

            // The strategy is what decides whether the migration runs; at the target it must be a no-op.
            strategy(84, target)(listOf(migration(84f, ran))).await()
            ran shouldBe emptyList()
        }
    }

    @Test
    fun `the migration past the pinned version code runs through the strategy`() {
        runBlocking {
            val ran = mutableListOf<Float>()
            val migrations = listOf(migration(84f, ran), migration(85f, ran))
            val target = nextMigrationTarget(83, migrations)
            target shouldBe 85

            strategy(83, target)(migrations).await()
            ran shouldContainExactly listOf(84f, 85f)
        }
    }

    private fun strategy(old: Int, new: Int): MigrationStrategy {
        val factory = MigrationStrategyFactory(
            MigrationJobFactory(MigrationContext(dryrun = false, previousVersion = old), Migrator.scope),
            {},
        )
        return factory.create(old, new)
    }

    @Test
    fun `the highest defined version wins over the applied one`() {
        nextMigrationTarget(84, listOf(migration(84f), migration(85f))) shouldBe 85
    }

    @Test
    fun `a migration numbered above the last applied one runs on the next launch`() {
        runBlocking {
            val ran = mutableListOf<Float>()
            // 85f is the highest migration shipped so far (the page-preview cache clear), so this is
            // the upgrade path of every existing install; the repair migration is the 86f one.
            val migrations = listOf(migration(84f, ran), migration(85f, ran), migration(86f, ran))
            val target = nextMigrationTarget(applied = 85, migrations = migrations)
            target shouldBe 86

            strategy(85, target)(migrations).await()
            // The 85f migration must not run a second time; only the new one is selected.
            ran shouldContainExactly listOf(86f)
        }
    }

    @Test
    fun `an applied version ahead of every migration is kept`() {
        nextMigrationTarget(applied = 90, migrations = listOf(migration(84f))) shouldBe 90
    }

    @Test
    fun `no migrations keeps the applied version`() {
        nextMigrationTarget(applied = 0, migrations = emptyList()) shouldBe 0
    }
}
// KUN <--
