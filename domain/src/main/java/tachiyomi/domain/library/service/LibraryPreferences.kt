package tachiyomi.domain.library.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.SubcategoryUpdateScope
import tachiyomi.domain.manga.model.Manga

@Inject
@SingleIn(AppScope::class)
class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val displayMode = preferenceStore.getObjectFromString(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val sortingMode = preferenceStore.getObjectFromString(
        "library_sorting_mode",
        LibrarySort.default,
        LibrarySort.Serializer::serialize,
        LibrarySort.Serializer::deserialize,
    )

    val randomSortSeed = preferenceStore.getInt("library_random_sort_seed", 0)

    val portraitColumns = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    val landscapeColumns = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    val lastUpdatedTimestamp = preferenceStore.getLong(Preference.appStateKey("library_update_last_timestamp"), 0L)
    val autoUpdateInterval = preferenceStore.getInt("pref_library_update_interval_key", 0)

    // KMK -->
    val showUpdatingProgressBanner = preferenceStore.getBoolean(
        Preference.appStateKey("pref_show_updating_progress_banner_key"),
        true,
    )
    // KMK <--

    val coverRatios = preferenceStore.getStringSet(
        Preference.appStateKey("pref_library_cover_ratios_key"),
        emptySet(),
    )

    val coverColors = preferenceStore.getStringSet(
        Preference.appStateKey("pref_library_cover_colors_key"),
        emptySet(),
    )
    // KMK <--

    val autoUpdateDeviceRestrictions = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )
    val autoUpdateMangaRestrictions = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            MANGA_HAS_UNREAD,
            MANGA_NON_COMPLETED,
            MANGA_NON_READ,
            MANGA_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    val autoUpdateMetadata = preferenceStore.getBoolean("auto_update_metadata", false)

    // KMK -->
    val fetchMetadataOnAdd = preferenceStore.getBoolean("fetch_metadata_on_add", false)
    val fetchChaptersOnAdd = preferenceStore.getBoolean("fetch_chapters_on_add", false)
    // KMK <--

    val showContinueReadingButton = preferenceStore.getBoolean(
        "display_continue_reading_button",
        false,
    )

    val markDuplicateReadChapterAsRead = preferenceStore.getStringSet("mark_duplicate_read_chapter_read", emptySet())

    // region Filter

    val filterDownloaded = preferenceStore.getEnum(
        "pref_filter_library_downloaded_v2",
        TriState.DISABLED,
    )

    val filterUnread = preferenceStore.getEnum("pref_filter_library_unread_v2", TriState.DISABLED)

    val filterStarted = preferenceStore.getEnum(
        "pref_filter_library_started_v2",
        TriState.DISABLED,
    )

    val filterBookmarked = preferenceStore.getEnum(
        "pref_filter_library_bookmarked_v2",
        TriState.DISABLED,
    )

    val filterCompleted = preferenceStore.getEnum(
        "pref_filter_library_completed_v2",
        TriState.DISABLED,
    )

    val filterIntervalCustom = preferenceStore.getEnum(
        "pref_filter_library_interval_custom",
        TriState.DISABLED,
    )

    // SY -->
    val filterLewd = preferenceStore.getEnum(
        "pref_filter_library_lewd_v2",
        TriState.DISABLED,
    )
    // SY <--

    // KMK -->
    val filterCategories = preferenceStore.getBoolean(
        "pref_filter_library_categories",
        false,
    )

    val filterCategoriesInclude = preferenceStore.getStringSet(FILTER_LIBRARY_CATEGORIES_INCLUDE_PREF_KEY, emptySet())

    val filterCategoriesExclude = preferenceStore.getStringSet(FILTER_LIBRARY_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())
    // KMK <--

    fun filterTracking(id: Int) = preferenceStore.getEnum(
        "pref_filter_library_tracked_${id}_v2",
        TriState.DISABLED,
    )

    // endregion

    // region Badges

    val downloadBadge = preferenceStore.getBoolean("display_download_badge", false)

    val unreadBadge = preferenceStore.getBoolean("display_unread_badge", true)

    val localBadge = preferenceStore.getBoolean("display_local_badge", true)

    val languageBadge = preferenceStore.getBoolean("display_language_badge", true)

    // KMK -->
    val sourceBadge = preferenceStore.getBoolean("display_source_badge", true)

    val useLangIcon = preferenceStore.getBoolean("display_language_text", true)
    // KMK <--

    val newShowUpdatesCount = preferenceStore.getBoolean("library_show_updates_count", true)
    val newUpdatesCount = preferenceStore.getInt(Preference.appStateKey("library_unseen_updates_count"), 0)

    // endregion

    // region Category

    val defaultCategory = preferenceStore.getInt(DEFAULT_CATEGORY_PREF_KEY, -1)

    val lastUsedCategory = preferenceStore.getInt(Preference.appStateKey("last_used_category"), 0)

    val categoryTabs = preferenceStore.getBoolean("display_category_tabs", true)

    val categoryNumberOfItems = preferenceStore.getBoolean("display_number_of_items", false)

    // KNS -->
    // Category manager view state; same keys as the previous personal fork.
    val categoryManagerSortMode = preferenceStore.getInt("category_manager_sort_mode", 0)

    val categoryManagerCollapsedIds = preferenceStore.getStringSet("category_manager_collapsed_ids", emptySet())
    // KNS <--

    // KNS -->
    // Chapter visibility; same keys as the previous personal fork. The per-manga Smart Merge override
    // is stored on the manga (`Manga.smartMergeOverride`), not here.
    val smartScanlatorMerge = preferenceStore.getBoolean("pref_smart_scanlator_merge", false)

    /** Whether a manga's chapter list hides its blacklisted chapters (they can be revealed per manga). */
    fun hideBlacklistedChapters(mangaId: Long) =
        preferenceStore.getBoolean("pref_hide_blacklisted_chapters_$mangaId", true)

    /** Library filter: manga with at least one bookmark of these colors ([BookmarkColor] names). */
    val filterBookmarkColors = preferenceStore.getStringSet("pref_filter_library_bookmark_colors", emptySet())

    /** Chapter list filter of one manga: only bookmarks of these colors ([BookmarkColor] names). */
    fun bookmarkColorFilter(mangaId: Long) =
        preferenceStore.getStringSet("manga_bookmark_color_filter_$mangaId", emptySet())
    // KNS <--

    val categorizedDisplaySettings = preferenceStore.getBoolean("categorized_display", false)

    // KMK -->
    val showHiddenCategories = preferenceStore.getBoolean("hide_hidden_categories", false)
    // KMK <--

    val updateCategories = preferenceStore.getStringSet(LIBRARY_UPDATE_CATEGORIES_PREF_KEY, emptySet())

    val updateCategoriesExclude = preferenceStore.getStringSet(LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    // KNS -->
    /** When each category with an update interval was last updated by a scheduled run (`id:epochMillis`). */
    val categoryUpdateLastRun = preferenceStore.getStringSet(
        Preference.appStateKey("library_update_category_last_run"),
        emptySet(),
    )

    /**
     * How many entries the library and metadata updates refresh at the same time (old fork key; this
     * default keeps the updater's previous fixed limit).
     */
    val libraryUpdateParallelSlots = preferenceStore.getInt("library_update_parallel_slots", DEFAULT_PARALLEL_SLOTS)

    /** Same key as the previous personal fork. */
    val subcategoryUpdateScope = preferenceStore.getEnum(
        "pref_library_update_subcategory_scope",
        SubcategoryUpdateScope.WHOLE_CATEGORY,
    )
    // KNS <--

    // endregion

    // region Chapter

    val filterChapterByRead = preferenceStore.getLong(
        "default_chapter_filter_by_read",
        Manga.SHOW_ALL,
    )

    val filterChapterByDownloaded = preferenceStore.getLong(
        "default_chapter_filter_by_downloaded",
        Manga.SHOW_ALL,
    )

    val filterChapterByBookmarked = preferenceStore.getLong(
        "default_chapter_filter_by_bookmarked",
        Manga.SHOW_ALL,
    )

    // and upload date
    val sortChapterBySourceOrNumber = preferenceStore.getLong(
        "default_chapter_sort_by_source_or_number",
        Manga.CHAPTER_SORTING_SOURCE,
    )

    val displayChapterByNameOrNumber = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Manga.CHAPTER_DISPLAY_NAME,
    )

    val sortChapterByAscendingOrDescending = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Manga.CHAPTER_SORT_DESC,
    )

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead.set(manga.unreadFilterRaw)
        filterChapterByDownloaded.set(manga.downloadedFilterRaw)
        filterChapterByBookmarked.set(manga.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber.set(manga.sorting)
        displayChapterByNameOrNumber.set(manga.displayMode)
        sortChapterByAscendingOrDescending.set(
            if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        )
    }

    val autoClearChapterCache = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    val hideMissingChapters = preferenceStore.getBoolean("pref_hide_missing_chapter_indicators", false)

    // KMK -->
    val showEmptyCategoriesSearch = preferenceStore.getBoolean("show_empty_categories_search", false)
    // KMK <--
    // endregion

    // region Swipe Actions

    val swipeToStartAction = preferenceStore.getEnum(
        "pref_chapter_swipe_end_action",
        ChapterSwipeAction.ToggleBookmark,
    )

    val swipeToEndAction = preferenceStore.getEnum(
        "pref_chapter_swipe_start_action",
        ChapterSwipeAction.ToggleRead,
    )

    val updateMangaTitles = preferenceStore.getBoolean("pref_update_library_manga_titles", false)

    val disallowNonAsciiFilenames = preferenceStore.getBoolean("disallow_non_ascii_filenames", false)

    // endregion

    enum class ChapterSwipeAction {
        ToggleRead,
        ToggleBookmark,
        Download,
        Disabled,
    }

    // SY -->

    val sortTagsForLibrary = preferenceStore.getStringSet("sort_tags_for_library", mutableSetOf())

    val groupLibraryUpdateType = preferenceStore.getEnum("group_library_update_type", GroupLibraryMode.GLOBAL)

    val groupLibraryBy = preferenceStore.getInt("group_library_by", LibraryGroup.BY_DEFAULT)

    // SY <--

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"

        // KNS -->
        const val DEFAULT_PARALLEL_SLOTS = 5
        const val MAX_PARALLEL_SLOTS = 10
        // KNS <--

        const val MANGA_NON_COMPLETED = "manga_ongoing"
        const val MANGA_HAS_UNREAD = "manga_fully_read"
        const val MANGA_NON_READ = "manga_started"
        const val MANGA_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"

        const val MARK_DUPLICATE_CHAPTER_READ_NEW = "new"
        const val MARK_DUPLICATE_CHAPTER_READ_EXISTING = "existing"

        const val DEFAULT_CATEGORY_PREF_KEY = "default_category"
        private const val LIBRARY_UPDATE_CATEGORIES_PREF_KEY = "library_update_categories"
        private const val LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY = "library_update_categories_exclude"

        // KMK -->
        private const val FILTER_LIBRARY_CATEGORIES_INCLUDE_PREF_KEY = "pref_filter_library_categories_include"
        private const val FILTER_LIBRARY_CATEGORIES_EXCLUDE_PREF_KEY = "pref_filter_library_categories_exclude"
        // KMK <--

        val categoryPreferenceKeys = setOf(
            DEFAULT_CATEGORY_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
            // KMK -->
            FILTER_LIBRARY_CATEGORIES_INCLUDE_PREF_KEY,
            FILTER_LIBRARY_CATEGORIES_EXCLUDE_PREF_KEY,
            // KMK <--
        )
    }
}
