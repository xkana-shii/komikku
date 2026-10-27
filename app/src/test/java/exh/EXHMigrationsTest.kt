// KUN -->
package exh

import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupMergedEntry
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.source.online.all.Hitomi
import exh.source.HITOMI_OLD_ID
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

/**
 * A restore from a build that predates the delegated sources has to move the old source ids, in
 * every model that stores one - not just the manga row.
 */
class EXHMigrationsTest {

    private val delegatedHitomi = Hitomi.HITOMI_SOURCE_IDS.first()

    @Test
    fun `a manga from the pre-delegation hitomi source moves to the delegated source`() {
        val manga = Manga.create().copy(source = HITOMI_OLD_ID)

        EXHMigrations.migrateBackupEntry(manga).source shouldBe delegatedHitomi
    }

    @Test
    fun `a merge reference, saved search, feed and source filter follow the same move`() {
        EXHMigrations.migrateBackupMergedEntry(
            BackupMergedEntry(mangaSourceId = HITOMI_OLD_ID),
        ).mangaSourceId shouldBe delegatedHitomi

        EXHMigrations.migrateBackupSavedSearch(
            BackupSavedSearch(source = HITOMI_OLD_ID),
        ).source shouldBe delegatedHitomi

        EXHMigrations.migrateBackupFeed(
            BackupFeed(source = HITOMI_OLD_ID),
        ).source shouldBe delegatedHitomi

        EXHMigrations.migrateSourceIds(setOf(HITOMI_OLD_ID.toString())) shouldBe
            setOf(delegatedHitomi.toString())
    }

    @Test
    fun `unrelated source ids are left alone`() {
        EXHMigrations.migrateBackupEntry(Manga.create().copy(source = 123L)).source shouldBe 123L
        EXHMigrations.migrateSourceIds(setOf("123")) shouldBe setOf("123")
    }
}
// KUN <--
