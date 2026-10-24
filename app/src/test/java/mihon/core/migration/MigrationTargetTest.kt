// KUN -->
package mihon.core.migration

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MigrationTargetTest {

    private fun migration(version: Float) = object : Migration {
        override val version = version
        override val isAlways = false
        override suspend fun invoke(migrationContext: MigrationContext) = true
    }

    @Test
    fun `a migration added after the pinned version code is still reachable`() {
        nextMigrationTarget(applied = 83, migrations = listOf(migration(84f))) shouldBe 84
    }

    @Test
    fun `an applied migration is not selected again`() {
        nextMigrationTarget(applied = 84, migrations = listOf(migration(84f))) shouldBe 84
    }

    @Test
    fun `the highest defined version wins over the applied one`() {
        nextMigrationTarget(84, listOf(migration(84f), migration(85f))) shouldBe 85
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
