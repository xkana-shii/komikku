package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import mihon.core.common.extensions.toInstantOrNull
import mihon.core.common.extensions.toJsonObject
import tachiyomi.domain.backup.model.RestoredForkState
import tachiyomi.domain.chapter.service.legacyFillermarkBlacklistKeys
import tachiyomi.domain.manga.model.Manga

@Suppress("DEPRECATION")
@Serializable
class BackupManga(
    // in 1.x some of these values have different names
    // SY -->
    @ProtoNumber(1) var source: Long = 0,
    // url is called key in 1.x
    @ProtoNumber(2) var url: String = "",
    // SY <--
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    // thumbnailUrl is called cover in 1.x
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    // @ProtoNumber(10) val customCover: String = "", 1.x value, not used in 0.x
    // @ProtoNumber(11) val lastUpdate: Long = 0, 1.x value, not used in 0.x
    // @ProtoNumber(12) val lastInit: Long = 0, 1.x value, not used in 0.x
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0, // Replaced by viewer_flags
    // @ProtoNumber(15) val flags: Int = 0, 1.x value, not used in 0.x
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    // Bump by 100 for values that are not saved/implemented in 1.x but are used in 0.x
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    // @ProtoNumber(102) var brokenHistory, legacy history model with non-compliant proto number
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    // Mihon values start here
    @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(110) var notes: String = "",
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(112) var memo: ByteArray = JsonObjectEmptyBytes,

    @ProtoNumber(113) var nextUpdate: Long = 0,

    // KNS --> read only: the previous personal fork wrote its last-update time at 113 and the next update
    // at 114. A backup with 114 set is one of its backups, so 114 is the next update and 113 is not.
    @ProtoNumber(114) var legacyNextUpdate: Long = 0,
    // KNS <--

    // KNS -->
    // 614–620 keep the previous personal fork's numbers so its backups restore (613 categoryIds and
    // 617, used by Houri, are not used here).
    @ProtoNumber(614) var scanlatorPriority: List<String> = emptyList(),
    @ProtoNumber(615) var blacklistedChapters: List<String> = emptyList(),
    @ProtoNumber(616) var scanlatorRangeRules: List<String> = emptyList(),
    /** "enabled" / "disabled" / "global"; null = a payload without this setting. */
    @ProtoNumber(618) var smartScanlatorMergeOverride: String? = null,
    // 619 hideBlacklistedChapters (a per-manga view toggle) is not restored.
    /** Repeated fields cannot tell "unset" from "empty": this states that 614–616 are authoritative. */
    @ProtoNumber(620) var scanlatorSettingsPresent: Boolean = false,
    // KNS <--

    // SY specific values
    @ProtoNumber(600) var mergedMangaReferences: List<BackupMergedMangaReference> = emptyList(),
    @ProtoNumber(601) var flatMetadata: BackupFlatMetadata? = null,
    @ProtoNumber(602) var customStatus: Int = 0,
    @ProtoNumber(603) var customThumbnailUrl: String? = null,

    // J2K specific values
    @ProtoNumber(800) var customTitle: String? = null,
    @ProtoNumber(801) var customArtist: String? = null,
    @ProtoNumber(802) var customAuthor: String? = null,
    // skipping 803 due to using duplicate value in previous builds
    @ProtoNumber(804) var customDescription: String? = null,
    @ProtoNumber(805) var customGenre: List<String>? = null,

    // KUN -->
    // Fork-reserved block 10000-10999 (see AGENTS.md, "Backup protobuf field numbers"). Upstream
    // owns the low ranges and has already claimed 604 for a different wire type in an open SY PR.
    // Whether the sender included the custom-info overrides in this entry. Nullable so an older
    // peer or backup that never sent the field is "unknown" (merge only): `true` with every
    // override empty is an explicit clear, while an absent marker must never clear anything.
    @ProtoNumber(10000) var customInfoIncluded: Boolean? = null,
    // Whether the sender included the merged-reference list for this entry. Nullable so a legacy
    // payload that never sent the field is not mistaken for "this merge has no children": only
    // `true` lets a winning payload delete local references.
    @ProtoNumber(10001) var mergedMangaReferencesIncluded: Boolean? = null,
    // KUN <--

) {
    fun getMangaImpl(): Manga {
        return Manga.create().copy(
            url = this@BackupManga.url,
            // SY -->
            ogTitle = this@BackupManga.title,
            ogArtist = this@BackupManga.artist,
            ogAuthor = this@BackupManga.author,
            ogThumbnailUrl = this@BackupManga.thumbnailUrl,
            ogDescription = this@BackupManga.description,
            ogGenre = this@BackupManga.genre,
            ogStatus = this@BackupManga.status.toLong(),
            // SY <--
            favorite = this@BackupManga.favorite,
            source = this@BackupManga.source,
            // KUN -->
            // `favorite_modified_at` is not a fallback for a missing `date_added`: every manga
            // update rewrote it (see migrations/49.sqm). A missing date-added becomes UNKNOWN.
            // KUN <--
            dateAdded = if (this@BackupManga.favorite) {
                this@BackupManga.dateAdded.toInstantOrNull()
                    ?: Manga.UNKNOWN_FAVORITE_AT
            } else {
                null
            },
            viewerFlags = (this@BackupManga.viewer_flags ?: this@BackupManga.viewer).toLong(),
            chapterFlags = this@BackupManga.chapterFlags.toLong(),
            updateStrategy = this@BackupManga.updateStrategy,
            lastModifiedAt = this@BackupManga.lastModifiedAt,
            favoriteModifiedAt = this@BackupManga.favoriteModifiedAt,
            version = this@BackupManga.version,
            notes = this@BackupManga.notes,
            initialized = this@BackupManga.initialized,
            memo = this@BackupManga.memo.toJsonObject(),
            // KNS -->
            nextUpdate = (legacyNextUpdate.takeIf { it != 0L } ?: this@BackupManga.nextUpdate).toInstantOrNull(),
            // KNS <--
            // SY -->
            userTitle = this@BackupManga.customTitle,
            userAuthor = this@BackupManga.customAuthor,
            userArtist = this@BackupManga.customArtist,
            userThumbnailUrl = this@BackupManga.customThumbnailUrl,
            userDescription = this@BackupManga.customDescription,
            userGenre = this@BackupManga.customGenre,
            userStatus = this@BackupManga.customStatus.takeUnless { it == 0 }?.toLong(),
            // SY <--
        )
    }

    // KNS -->
    /**
     * The fork-owned state of this entry. Legacy Fillermarks of old-fork backups become exact chapter
     * blacklist entries, as the old fork's own migration did.
     */
    fun getForkState(): RestoredForkState? {
        val fillermarkKeys = legacyFillermarkBlacklistKeys(chapters.filter { it.fillermark }.map { it.url })
        val blacklist = (blacklistedChapters + fillermarkKeys).distinct()
        val scanlatorPresent = scanlatorSettingsPresent || scanlatorPriority.isNotEmpty() ||
            scanlatorRangeRules.isNotEmpty() || blacklist.isNotEmpty()
        val override = smartScanlatorMergeOverride
        if (!scanlatorPresent && override == null) return null
        return RestoredForkState(
            scanlatorSettingsPresent = scanlatorPresent,
            blacklistedChapters = blacklist,
            scanlatorPriority = scanlatorPriority,
            scanlatorRangeRules = scanlatorRangeRules,
            smartMergeOverridePresent = override != null,
            smartMergeOverride = when (override) {
                "enabled" -> true
                "disabled" -> false
                else -> null
            },
        )
    }
    // KNS <--
}
