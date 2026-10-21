package eu.kanade.domain.sync.models

data class SyncSettings(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val appSettings: Boolean = true,
    val extensionStores: Boolean = true,
    val sourceSettings: Boolean = true,
    val privateSettings: Boolean = false,

    // SY -->
    val customInfo: Boolean = true,
    val readEntries: Boolean = true,
    val savedSearchesFeeds: Boolean = true,
    // SY <--
    // KUN -->
    // Pinned sources are usually device-specific, so they are not synced by default: the key is
    // excluded from the sync upload and skipped on restore. Enabling this syncs the set; on
    // SyncYomi protocol v2, where the server merges the upload, the synced value then replaces the
    // local pins instead of being added to them. Other backends keep the union.
    val syncPinnedSources: Boolean = false,
    // KUN <--
) {
    // KUN -->
    /**
     * A force push needs the library and chapters: it exists to push manga/chapter/read state.
     * Categories, tracking, history and custom info can stay disabled; their server-side values are
     * copied into the upload so the forced manga payloads do not replace them with empty data.
     */
    val supportsForcePush: Boolean
        get() = libraryEntries && chapters

    /**
     * A force push must copy the server's values for any manga-payload section that is disabled,
     * or the forced payloads would replace them with empty data library-wide.
     */
    val forcePushNeedsSnapshot: Boolean
        get() = !categories || !tracking || !history || !customInfo
    // KUN <--
}
