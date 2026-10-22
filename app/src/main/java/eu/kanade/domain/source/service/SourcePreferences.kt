package eu.kanade.domain.source.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.domain.extension.model.ContentWarning
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getEnumSet
import tachiyomi.core.common.preference.getLongArray
import tachiyomi.domain.library.model.LibraryDisplayMode

@Inject
@SingleIn(AppScope::class)
class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    val sourceDisplayMode = preferenceStore.getObjectFromString(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val enabledLanguages = preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    val disabledSources = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    val incognitoExtensions = preferenceStore.getStringSet("incognito_extensions", emptySet())

    val pinnedSources = preferenceStore.getStringSet(
        // KMK -->
        PINNED_SOURCES_PREF_KEY,
        // KMK <--
        emptySet(),
    )

    val lastUsedSource = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    val enabledContentWarnings: Preference<Set<ContentWarning>> = preferenceStore.getEnumSet(
        "enabled_content_warnings",
        setOf(ContentWarning.SAFE, ContentWarning.MIXED, ContentWarning.NSFW),
    )

    val applyContentWarningsToInstalled: Preference<Boolean> = preferenceStore.getBoolean(
        "apply_content_warnings_to_installed",
        true,
    )

    val migrationSortingMode = preferenceStore.getEnum("pref_migration_sorting", SetMigrateSorting.Mode.ALPHABETICAL)

    val migrationSortingDirection = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    val hideInLibraryItems = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    // KMK -->
    val hideInLibraryFeedItems = preferenceStore.getBoolean("feed_hide_in_library_items", false)
    // KMK <--

    @Deprecated("Use ExtensionRepoRepository instead", replaceWith = ReplaceWith("ExtensionRepoRepository.getAll()"))
    val extensionRepos = preferenceStore.getStringSet("extension_repos", emptySet())

    val extensionUpdatesCount = preferenceStore.getInt("ext_updates_count", 0)

    val trustedExtensions = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    val globalSearchFilterState = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    val migrationSources = preferenceStore.getLongArray("migration_sources", emptyList())

    val migrationFlags = preferenceStore.getObjectFromInt(
        key = "migration_flags",
        defaultValue = MigrationFlag.entries.toSet(),
        serializer = { MigrationFlag.toBit(it) },
        deserializer = { value: Int -> MigrationFlag.fromBit(value) },
    )

    val migrationDeepSearchMode = preferenceStore.getBoolean("migration_deep_search", false)

    val migrationPrioritizeByChapters = preferenceStore.getBoolean("migration_prioritize_by_chapters", false)

    val migrationHideUnmatched = preferenceStore.getBoolean("migration_hide_unmatched", false)

    val migrationHideWithoutUpdates = preferenceStore.getBoolean("migration_hide_without_updates", false)

    // KMK -->
    val migrationSmartSearchSingleEntry = preferenceStore.getBoolean("migration_smart_search_single_entry", false)

    val globalSearchPinnedState = preferenceStore.getEnum(
        Preference.appStateKey("global_search_pinned_toggle_state"),
        SourceFilter.PinnedOnly,
    )

    val disabledRepos = preferenceStore.getStringSet("disabled_repos", emptySet())
    // KMK <--

    // SY -->
    val enableSourceBlacklist = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    val sourcesTabCategories = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    val sourcesTabCategoriesFilter = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    val sourcesTabSourcesInCategories = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    // KNS --> the previous personal fork's Global Search source-category scope (same key); blank = no category
    val globalSearchSourceCategory = preferenceStore.getString(Preference.appStateKey("global_search_source_category"), "")
    // KNS <--

    val dataSaver = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    val dataSaverIgnoreJpeg = preferenceStore.getBoolean("ignore_jpeg", false)

    val dataSaverIgnoreGif = preferenceStore.getBoolean("ignore_gif", true)

    val dataSaverImageQuality = preferenceStore.getInt("data_saver_image_quality", 80)

    val dataSaverImageFormatJpeg = preferenceStore.getBoolean("data_saver_image_format_jpeg", false)

    val dataSaverServer = preferenceStore.getString("data_saver_server", "")

    val dataSaverColorBW = preferenceStore.getBoolean("data_saver_color_bw", false)

    val dataSaverExcludedSources = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    val dataSaverDownloader = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
    }

    val allowLocalSourceHiddenFolders = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)

    val preferredMangaDexId = preferenceStore.getString("preferred_mangaDex_id", "0")

    val mangadexSyncToLibraryIndexes = preferenceStore.getStringSet(
        "pref_mangadex_sync_to_library_indexes",
        emptySet(),
    )

    val recommendationSearchFlags = preferenceStore.getInt("rec_search_flags", Int.MAX_VALUE)
    // SY <--

    // KMK -->
    val relatedMangas = preferenceStore.getBoolean("related_mangas", true)

    companion object {
        const val PINNED_SOURCES_PREF_KEY = "pinned_catalogues"
    }
    // KMK <--
}
