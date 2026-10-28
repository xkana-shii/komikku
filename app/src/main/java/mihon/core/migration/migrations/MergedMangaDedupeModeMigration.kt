package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.repository.MangaMergeRepository

@Inject
@ContributesIntoSet(AppScope::class)
class MergedMangaDedupeModeMigration(
    private val mangaMergeRepository: MangaMergeRepository,
    private val updateMergedSettings: UpdateMergedSettings,
) : Migration {
    override val version: Float = 77f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        // KUN -->
        // The sort mode moved from the shared `merged` self-reference row to one `merge` row per
        // merge, so the shift is applied per merge instead of per reference.
        //
        // The parents are the distinct merge ids of the entries: `getMergedManga()` lists the
        // entries' children and never a merge parent, so it found no settings at all and the
        // renumbering below never ran.
        val mergeIds = mangaMergeRepository.getAllEntries()
            .map { it.mergeId }
            .distinct()

        mergeIds.forEach { mergeId ->
            val settings = mangaMergeRepository.getMergeSettings(mergeId) ?: return@forEach
            updateMergedSettings.awaitMerge(
                mergeId = mergeId,
                chapterSortMode = (settings.chapterSortMode - 1).coerceAtLeast(0),
                infoMangaId = settings.infoMangaId,
            )
        }
        // KUN <--
        return@withIOContext true
    }
}
