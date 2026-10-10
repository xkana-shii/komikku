package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.lang.withIOContext

// KMK -->
/**
 * Moves the former global-search-only category filter into Houri's independent category scope.
 * The old category filter covered all matching sources, so it becomes an [SourceFilter.All] source
 * filter plus the same persisted category. The operation is idempotent, so it runs for every app
 * version and still covers installations upgraded without a version-code bump.
 */
class GlobalSearchSourceCategoryMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val application = migrationContext.get<Application>() ?: return@withIOContext false
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return@withIOContext false
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        val sourceFilterKey = sourcePreferences.globalSearchPinnedState().key()
        val legacyCategoryKey = Preference.appStateKey("global_search_category_filter")

        if (preferences.getString(sourceFilterKey, null) != LEGACY_CATEGORY_FILTER) {
            return@withIOContext true
        }

        preferences.edit {
            preferences.getString(legacyCategoryKey, null)
                ?.takeIf(String::isNotBlank)
                ?.let { putString(sourcePreferences.globalSearchSourceCategory().key(), it) }
            putString(sourceFilterKey, SourceFilter.All.name)
            remove(legacyCategoryKey)
        }
        true
    }

    private companion object {
        const val LEGACY_CATEGORY_FILTER = "Category"
    }
}
// KMK <--
