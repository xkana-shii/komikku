package mihon.core.migration.migrations

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.Database
import tachiyomi.domain.chapter.service.legacyFillermarkBlacklistKeys

// KMK -->
/**
 * Replaces legacy per-chapter Fillermarks with URL-specific blacklist keys.
 *
 * It is transactionally repeatable: a failed attempt leaves its source rows
 * intact, and keys are de-duplicated before the old value is cleared.
 */
class FillermarkBlacklistMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val database = migrationContext.get<Database>() ?: return@withIOContext false
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false
        val completed = preferenceStore.getBoolean(MIGRATION_COMPLETED_KEY, false)
        if (completed.get()) return@withIOContext true
        val legacyChapters = database.chapterQueries.getLegacyFillermarkedChapterUrls().awaitAsList()

        database.transaction {
            legacyChapters
                .groupBy({ it.mangaId }, { it.url })
                .forEach { (mangaId, urls) ->
                    val existing = database.mangaQueries
                        .getBlacklistedChaptersByMangaId(mangaId)
                        .awaitAsOneOrNull()
                        ?: error("Cannot migrate Fillermarks for missing manga $mangaId")
                    database.mangaQueries.setBlacklistedChapters(
                        blacklistedChapters = (existing + legacyFillermarkBlacklistKeys(urls)).distinct(),
                        mangaId = mangaId,
                    )
                }
            if (legacyChapters.isNotEmpty()) {
                database.chapterQueries.clearLegacyFillermarks()
            }
            database.mangaQueries.clearLegacyFillermarkFilters(
                mask = LEGACY_FILLERMARK_MASK.inv(),
                legacyMask = LEGACY_FILLERMARK_MASK,
            )
        }
        completed.set(true)
        true
    }

    private companion object {
        const val LEGACY_FILLERMARK_MASK = 0x180L
        const val MIGRATION_COMPLETED_KEY = "fillermark_blacklist_migration_completed"
    }
}
// KMK <--
