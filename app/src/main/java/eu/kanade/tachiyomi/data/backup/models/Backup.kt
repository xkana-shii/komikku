package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class Backup(
    // SY -->
    @EncodeDefault
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    // SY <--
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensionStores: List<BackupExtensionStore> = emptyList(),
    // SY specific values
    @ProtoNumber(600) var backupSavedSearches: List<BackupSavedSearch> = emptyList(),
    // KMK -->
    // Global Popular/Latest feeds
    @ProtoNumber(610) var backupFeeds: List<BackupFeed> = emptyList(),
    // KMK <--
    // KUN -->
    // Explicit tombstones a SyncYomi v2 delta carries. `11000` is SyncYomi's own root-envelope
    // band, outside komi-kun's `10000-10999` fork block; see AGENTS.md "Backup protobuf field
    // numbers". A server that predates the field sends none, and the full-snapshot heuristic
    // remains the fallback for it.
    @ProtoNumber(11000) var deletions: List<BackupDeletedItem> = emptyList(),
    // KUN <--
)
