package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import exh.EXHMigrations
import exh.log.xLogE
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceRestorer(
    private val context: Context,
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    suspend fun restoreApp(
        preferences: List<BackupPreference>,
        backupCategories: List<BackupCategory>?,
        // KMK --> Category preference values store device-local IDs.
        restoredCategoryIds: Map<Long, Long> = emptyMap(),
        // KMK <--
    ) {
        restorePreferences(
            preferences,
            preferenceStore,
            backupCategories,
            // KMK -->
            restoredCategoryIds,
            // KMK <--
        )

        LibraryUpdateJob.setupTask(context)
        BackupCreateJob.setupTask(context)
        // KMK -->
        AppUpdateJob.setupTask(context)
        // KMK <--
    }

    suspend fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private suspend fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        backupCategories: List<BackupCategory>? = null,
        // KMK -->
        restoredCategoryIds: Map<Long, Long> = emptyMap(),
        // KMK <--
    ) {
        val backupCategoriesById = backupCategories?.associateBy { it.id.toString() }.orEmpty()
        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            try {
                // KMK --> These keys are tied to old database IDs. New BackupManga fields carry
                // an identity-safe replacement; legacy backups have no manga ID to map safely.
                if (key.startsWith("pref_smart_scanlator_merge_override_") ||
                    key.startsWith("pref_hide_blacklisted_chapters_")
                ) {
                    return@forEach
                }
                // KMK <--
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            val newValue = if (key == LibraryPreferences.DEFAULT_CATEGORY_PREF_KEY) {
                                restoredCategoryIds[value.value.toLong()]?.toInt()
                            } else {
                                value.value
                            }

                            newValue?.let { preferenceStore.getInt(key).set(it) }
                        }
                    }
                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            preferenceStore.getLong(key).set(value.value)
                        }
                    }
                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            preferenceStore.getFloat(key).set(value.value)
                        }
                    }
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            preferenceStore.getString(key).set(value.value)
                        }
                    }
                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            preferenceStore.getBoolean(key).set(value.value)
                        }
                    }
                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            val restored = restoreCategoriesPreference(
                                key,
                                value.value,
                                preferenceStore,
                                backupCategoriesById,
                                restoredCategoryIds,
                            )
                            if (!restored) {
                                // KMK -->
                                when (key) {
                                    SourcePreferences.PINNED_SOURCES_PREF_KEY ->
                                        restorePinnedSourcesPreference(
                                            key,
                                            value.value,
                                            preferenceStore,
                                        )
                                    else ->
                                        // KMK <--
                                        preferenceStore.getStringSet(key).set(value.value)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // KMK -->
                xLogE("Failed to restore preference <$key>", e)
                // KMK <--
            }
        }
    }

    private fun restoreCategoriesPreference(
        key: String,
        value: Set<String>,
        preferenceStore: PreferenceStore,
        backupCategoriesById: Map<String, BackupCategory>,
        restoredCategoryIds: Map<Long, Long>,
    ): Boolean {
        val categoryPreferences = LibraryPreferences.categoryPreferenceKeys + DownloadPreferences.categoryPreferenceKeys
        if (key !in categoryPreferences) return false

        val ids = value.mapNotNull {
            backupCategoriesById[it]?.id?.let(restoredCategoryIds::get)?.toString()
        }

        if (ids.isNotEmpty()) {
            preferenceStore.getStringSet(key) += ids
        }
        return true
    }

    private fun restorePinnedSourcesPreference(
        key: String,
        value: Set<String>,
        preferenceStore: PreferenceStore,
    ) {
        if (value.isEmpty()) return

        val valueToSet = EXHMigrations.migrateSourceIds(value)
        preferenceStore.getStringSet(key).set(valueToSet)
    }
}
