package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreateWorker
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.library.LibraryUpdateWorker
import eu.kanade.tachiyomi.data.updater.AppUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import exh.EXHMigrations
import exh.log.xLogE
import exh.log.xLogW
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

@Inject
class PreferenceRestorer(
    private val context: Context,
    private val getCategories: GetCategories,
    private val preferenceStore: PreferenceStore,
) {
    suspend fun restoreApp(
        preferences: List<BackupPreference>,
        backupCategories: List<BackupCategory>?,
        // KUN -->
        options: RestoreOptions = RestoreOptions(),
        // KUN <--
    ) {
        restorePreferences(
            preferences,
            preferenceStore,
            backupCategories,
            // KUN -->
            options,
            isAppPreferences = true,
            // KUN <--
        )

        LibraryUpdateWorker.setupTask(context)
        BackupCreateWorker.setupTask(context)
        // KMK -->
        AppUpdateJob.setupTask(context)
        // KMK <--
    }

    suspend fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    // KNS: internal so the restore can be tested without WorkManager
    internal suspend fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        backupCategories: List<BackupCategory>? = null,
        // KUN -->
        options: RestoreOptions = RestoreOptions(),
        isAppPreferences: Boolean = false,
        // KUN <--
    ) {
        val allCategories = if (backupCategories != null) getCategories.await() else emptyList()
        // KNS --> a backup category id maps to the local category with the same name path, so a
        // preference naming Shounen › Reading never lands on Josei › Reading
        val localCategoryId = localCategoryIdResolver(backupCategories.orEmpty(), allCategories)
        // KNS <--
        val prefs = preferenceStore.getAll()
        // KNS -->
        // Every value is collected and written in one batch: one write per value made the store copy
        // all of its entries once per value, and a backup with thousands of values ran out of memory.
        val writes = LinkedHashMap<String, Any>()
        var skippedLocalIdKeys = 0
        // KNS <--
        toRestore.forEach { (key, value) ->
            // KUN -->
            // App-state keys are device-local state (sync cursors, the pinned-source choice, ...)
            // and are already excluded from newly created app-preference backups; never restore
            // them from an older or hand-edited payload. Source preferences are namespaced
            // separately and keep whatever keys their source uses.
            if (isAppPreferences && Preference.isAppState(key)) return@forEach
            // KUN <--
            // KNS -->
            if (isKeyedByLocalId(key)) {
                skippedLocalIdKeys++
                return@forEach
            }
            // KNS <--
            try {
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            val newValue = if (key == LibraryPreferences.DEFAULT_CATEGORY_PREF_KEY) {
                                // KNS -->
                                localCategoryId(value.value.toString())?.toInt()
                                // KNS <--
                            } else {
                                value.value
                            }

                            newValue?.let { writes[key] = it }
                        }
                    }
                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            writes[key] = value.value
                        }
                    }
                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            writes[key] = value.value
                        }
                    }
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            writes[key] = value.value
                        }
                    }
                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            writes[key] = value.value
                        }
                    }
                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            val categoryIds = restoreCategoriesPreference(key, value.value, localCategoryId)
                            if (categoryIds != null) {
                                if (categoryIds.isNotEmpty()) writes[key] = categoryIds
                            } else {
                                // KMK -->
                                when (key) {
                                    SourcePreferences.PINNED_SOURCES_PREF_KEY ->
                                        // KNS --> collected into the batch like every other value
                                        restorePinnedSourcesPreference(
                                            key,
                                            value.value,
                                            preferenceStore,
                                            // KUN -->
                                            options,
                                            // KUN <--
                                        )?.let { writes[key] = it }
                                    // KNS <--
                                    else ->
                                        // KMK <--
                                        writes[key] = value.value
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
        // KNS -->
        if (skippedLocalIdKeys > 0) {
            xLogW("Skipped $skippedLocalIdKeys per-entry preferences keyed by another installation's database ids")
        }
        preferenceStore.putAll(writes)
        // KNS <--
    }

    /**
     * The category ids for a category preference, remapped from the backup's ids to local ones; null
     * when [key] is not a category preference.
     */
    private fun restoreCategoriesPreference(
        key: String,
        value: Set<String>,
        localCategoryId: (String) -> Long?,
    ): Set<String>? {
        val categoryPreferences = LibraryPreferences.categoryPreferenceKeys + DownloadPreferences.categoryPreferenceKeys
        if (key !in categoryPreferences) return null

        return value.mapNotNullTo(mutableSetOf()) { localCategoryId(it)?.toString() }
    }

    internal fun restorePinnedSourcesPreference(
        key: String,
        value: Set<String>,
        preferenceStore: PreferenceStore,
        // KUN -->
        options: RestoreOptions,
        // KUN <--
        // KNS --> returns the set to write (null: leave the pins alone) for the batched write
    ): Set<String>? {
        // KNS <--
        // KUN -->
        // Pinned sources are device-local unless the user opted into syncing them: leave both the
        // local pins and the synced set alone in that case.
        if (options.skipPinnedSources) return null

        val valueToSet = EXHMigrations.migrateSourceIds(value)
        // KNS --> returned instead of written, so the restore stays one batched write
        return if (options.replacePinnedSources) {
            // Synced: the incoming set is authoritative, so an un-pin propagates to this device and
            // an empty set clears the local pins.
            valueToSet
        } else {
            // Manual/file restore keeps the historical union behaviour.
            preferenceStore.getStringSet(key).get() + valueToSet
        }
        // KNS <--
        // KUN <--
    }

    // KNS -->
    companion object {
        /**
         * Per-entry preferences whose key holds a manga's local database id. Ids are assigned anew on
         * every installation, so restored into another one such a value would land on whichever entry
         * happens to have that id; the values the old fork kept here travel with the manga instead.
         */
        private val LOCAL_ID_KEY_PREFIXES = listOf(
            "pref_hide_blacklisted_chapters_",
            "pref_smart_scanlator_merge_override_",
            "manga_bookmark_color_filter_",
        )

        /**
         * Maps a backup category id to the local category with the same name path (as the manga
         * restore resolves categories); a bare name only matches a top-level category.
         */
        internal fun localCategoryIdResolver(
            backupCategories: List<BackupCategory>,
            localCategories: List<Category>,
        ): (String) -> Long? {
            val nodes = BackupCategoryHierarchy(backupCategories).nodes.associateBy { it.category.id.toString() }
            val localById = localCategories.associateBy(Category::id)
            val localUser = localCategories.filterNot(Category::isSystemCategory)
            val localByPath = localUser.associateBy { category ->
                CategoryTree.path(category, localById).joinToString("\u0000") { CategoryTree.normalizeName(it) }
            }
            val localTopByName = localUser.filter { it.parentId == Category.UNCATEGORIZED_ID }
                .associateBy { CategoryTree.normalizeName(it.name) }
            return { backupId ->
                nodes[backupId]?.let { node ->
                    localByPath[node.pathKey]?.id
                        ?: localTopByName[CategoryTree.normalizeName(node.category.name)]?.takeIf { node.depth == 0 }?.id
                }
            }
        }

        internal fun isKeyedByLocalId(key: String): Boolean = LOCAL_ID_KEY_PREFIXES.any { prefix ->
            val id = key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.removePrefix("exclude_")
            !id.isNullOrEmpty() && id.all(Char::isDigit)
        }
    }
    // KNS <--
}
