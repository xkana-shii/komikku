package mihon.core.migration.migrations

import app.cash.sqldelight.async.coroutines.awaitAsList
import tachiyomi.data.Database

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate

// KMK -->

class MergedMangaDedupeModeMigration : Migration {
    override val version: Float = 77f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val database = migrationContext.get<Database>() ?: return@withIOContext false
        val updateMergedSettings = migrationContext.get<UpdateMergedSettings>() ?: return@withIOContext false

        // Get merged manga references from db
        val dbMergedMangaReferences = database.mergedQueries.selectAll(MergedMangaMapper::map).awaitAsList()

        dbMergedMangaReferences.map {
            MergeMangaSettingsUpdate(
                id = it.id,
                chapterSortMode = (it.chapterSortMode - 1).coerceAtLeast(0),
                isInfoManga = null,
                getChapterUpdates = null,
                chapterPriority = null,
                downloadChapters = null,
            )
        }.let { updateMergedSettings.awaitAll(it) }
        return@withIOContext true
    }
}
// KMK <--
