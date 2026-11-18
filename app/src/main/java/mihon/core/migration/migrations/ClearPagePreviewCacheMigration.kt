package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.cache.PagePreviewCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

/**
 * SY-specific file
 *
 * Page previews are now cached under a key that is not the image URL (E-Hentai reuses one blank
 * thumbnail URL for every page), so every entry written by an older build is unreachable. Clear the
 * disk cache once so those orphans do not sit in the cache forever.
 *
 * Follows `ClearBrokenPagePreviewCacheMigration`, whose version this one must stay above.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class ClearPagePreviewCacheMigration(
    private val pagePreviewCache: PagePreviewCache,
) : Migration {
    override val version: Float = 85f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        pagePreviewCache.clear()
        return@withContext true
    }
}
