package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
@ContributesIntoSet(AppScope::class)
class MergedMangaDedupeModeMigration(
    private val mangaMergeRepository: MangaMergeRepository,
    private val updateMergedSettings: UpdateMergedSettings,
) : Migration {
    override val version: Float = 77f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        // Get merged manga references from db
        val dbMergedMangaReferences = mangaMergeRepository.getAllReferences()

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
