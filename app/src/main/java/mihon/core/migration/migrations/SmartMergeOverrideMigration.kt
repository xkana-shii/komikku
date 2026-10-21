package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

// KNS -->
/**
 * The previous personal fork kept each manga's Smart Scanlator Merge override in a preference
 * (`pref_smart_scanlator_merge_override_<mangaId>` = "enabled"/"disabled"). It now lives on the manga
 * row, where backups and SyncYomi carry it and one library-wide query can read it. Manga ids survive the
 * old-fork database conversion, so the values are moved over once and the old keys removed.
 *
 * Runs on every start but only acts while old keys exist, so it is cheap and safe to repeat.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class SmartMergeOverrideMigration(
    private val preferenceStore: PreferenceStore,
    private val mangaRepository: MangaRepository,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val overrides = preferenceStore.getAll().keys.filter { it.startsWith(PREFIX) }
        overrides.forEach { key ->
            val mangaId = key.removePrefix(PREFIX).toLongOrNull()
            val value = when (preferenceStore.getString(key, "").get()) {
                "enabled" -> true
                "disabled" -> false
                else -> null
            }
            // A manga that no longer exists, or "follow global", only needs the key removed.
            val moved = mangaId == null || value == null ||
                mangaRepository.update(MangaUpdate(mangaId) { smartMergeOverride = value })
            if (moved) preferenceStore.getString(key, "").delete()
        }
        return true
    }

    private companion object {
        const val PREFIX = "pref_smart_scanlator_merge_override_"
    }
}
// KNS <--
