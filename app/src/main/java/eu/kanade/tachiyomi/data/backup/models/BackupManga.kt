package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import mihon.core.common.extensions.toInstantOrNull
import mihon.core.common.extensions.toJsonObject
import tachiyomi.domain.manga.model.Manga

@Suppress("DEPRECATION")
@Serializable
class BackupManga(
    // SY -->
    @ProtoNumber(1) var source: Long = 0,
    @ProtoNumber(2) var url: String = "",
    // SY <--

    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,

    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0,
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),

    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy =
        UpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(110) var notes: String = "",
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(112) var memo: ByteArray = JsonObjectEmptyBytes,
    @ProtoNumber(113) var nextUpdate: Long = 0,

    // KMK --> Retain user-owned chapter-management settings.
    @ProtoNumber(114)
    var scanlatorPriority: List<String> = emptyList(),

    @ProtoNumber(115)
    var blacklistedChapters: List<String> = emptyList(),

    @ProtoNumber(116)
    var scanlatorRangeRules: List<String> = emptyList(),
    // KMK <--

    // SY -->
    @ProtoNumber(600)
    var mergedMangaReferences: List<BackupMergedMangaReference> = emptyList(),

    @ProtoNumber(601)
    var flatMetadata: BackupFlatMetadata? = null,

    @ProtoNumber(602)
    var customStatus: Int = 0,

    @ProtoNumber(603)
    var customThumbnailUrl: String? = null,
    // SY <--

    // J2K -->
    @ProtoNumber(800)
    var customTitle: String? = null,

    @ProtoNumber(801)
    var customArtist: String? = null,

    @ProtoNumber(802)
    var customAuthor: String? = null,

    @ProtoNumber(804)
    var customDescription: String? = null,

    @ProtoNumber(805)
    var customGenre: List<String>? = null,
    // J2K <--
) {

    fun getMangaImpl(): Manga {
        return Manga.create().copy(
            url = url,

            // SY -->
            ogTitle = title,
            ogArtist = artist,
            ogAuthor = author,
            ogThumbnailUrl = thumbnailUrl,
            ogDescription = description,
            ogGenre = genre,
            ogStatus = status.toLong(),
            // SY <--

            favorite = favorite,
            source = source,

            dateAdded = if (favorite) {
                dateAdded.toInstantOrNull()
                    ?: Manga.UNKNOWN_FAVORITE_AT
            } else {
                null
            },

            viewerFlags = (viewer_flags ?: viewer).toLong(),
            chapterFlags = chapterFlags.toLong(),
            updateStrategy = updateStrategy,
            lastModifiedAt = lastModifiedAt,
            favoriteModifiedAt = favoriteModifiedAt,
            version = version,
            notes = notes,
            initialized = initialized,
            memo = memo.toJsonObject(),
            nextUpdate = nextUpdate.toInstantOrNull(),

            // KMK -->
            scanlatorPriority = scanlatorPriority,
            blacklistedChapters = blacklistedChapters,
            scanlatorRangeRules = scanlatorRangeRules,
            // KMK <--
        )
    }
}
