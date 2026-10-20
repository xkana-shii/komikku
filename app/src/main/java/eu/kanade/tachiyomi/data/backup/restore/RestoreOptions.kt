package eu.kanade.tachiyomi.data.backup.restore

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

data class RestoreOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val appSettings: Boolean = true,
    val extensionStores: Boolean = true,
    val sourceSettings: Boolean = true,
    // SY -->
    val savedSearchesFeeds: Boolean = true,
    // SY <--
    // KUN -->
    // Sections inside the manga payload. Manual restore has no UI for them yet, so they default to
    // enabled and keep the existing behaviour; sync passes the user's per-section settings.
    val tracking: Boolean = true,
    val history: Boolean = true,
    val customInfo: Boolean = true,
    // KUN <--
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        appSettings,
        extensionStores,
        sourceSettings,
        // SY -->
        savedSearchesFeeds,
        // SY <--
        // KUN -->
        tracking,
        history,
        customInfo,
        // KUN <--
    )

    fun canRestore() =
        libraryEntries ||
            categories ||
            appSettings ||
            extensionStores ||
            sourceSettings /* SY --> */ ||
            savedSearchesFeeds /* SY <-- */

    companion object {
        val options = listOf(
            Entry(
                label = MR.strings.label_library,
                getter = RestoreOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.categories,
                getter = RestoreOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.app_settings,
                getter = RestoreOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionStores,
                getter = RestoreOptions::extensionStores,
                setter = { options, enabled -> options.copy(extensionStores = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = RestoreOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            // SY -->
            Entry(
                // KMK-->
                label = KMR.strings.saved_searches_feeds,
                // KMK <--
                getter = RestoreOptions::savedSearchesFeeds,
                setter = { options, enabled -> options.copy(savedSearchesFeeds = enabled) },
            ),
            // SY <--
        )

        fun fromBooleanArray(array: BooleanArray) = RestoreOptions(
            libraryEntries = array[0],
            categories = array[1],
            appSettings = array[2],
            extensionStores = array[3],
            sourceSettings = array[4],
            // SY -->
            savedSearchesFeeds = array[5],
            // SY <--
            // KUN -->
            // Old WorkManager input arrays predate these flags; treat a missing entry as enabled so
            // a queued or partially restored job keeps the historical behaviour.
            tracking = array.getOrElse(6) { true },
            history = array.getOrElse(7) { true },
            customInfo = array.getOrElse(8) { true },
            // KUN <--
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (RestoreOptions) -> Boolean,
        val setter: (RestoreOptions, Boolean) -> RestoreOptions,
    )
}
