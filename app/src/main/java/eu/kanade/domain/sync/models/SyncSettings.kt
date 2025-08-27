package eu.kanade.domain.sync.models

data class SyncSettings(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val appSettings: Boolean = true,
    val extensionRepoSettings: Boolean = true,
    val sourceSettings: Boolean = true,
    val privateSettings: Boolean = false,

    // SY -->
    val customInfo: Boolean = true,
    val readEntries: Boolean = true,
    val savedSearchesFeeds: Boolean = true,
    val smartCategories: Boolean = true,
    // SY <--
)
