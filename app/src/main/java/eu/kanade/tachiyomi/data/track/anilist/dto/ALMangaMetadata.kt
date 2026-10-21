package eu.kanade.tachiyomi.data.track.anilist.dto

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.autofillTags
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALMangaMetadata(
    val data: ALMangaMetadataData,
)

@Serializable
data class ALMangaMetadataData(
    @SerialName("Media")
    val media: ALMangaMetadataMedia,
)

@Serializable
data class ALMangaMetadataMedia(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String?,
    val staff: ALStaff,
    // KNS -->
    val genres: List<String>? = null,
    val tags: List<ALTag>? = null,
    val status: String? = null,
    // KNS <--
)

// KNS -->
@Serializable
data class ALTag(
    val name: String,
)

/**
 * Series metadata for autofill: staff split into authors and artists, genres followed by tag names,
 * and the publication status as an `SManga` status (not yet released, unknown or missing keeps the
 * current one).
 */
internal fun ALMangaMetadataMedia.toAutofillMetadata() = TrackMangaMetadata(
    remoteId = id,
    title = title.userPreferred,
    thumbnailUrl = coverImage.large,
    description = description?.htmlDecode()?.ifEmpty { null },
    authors = staff.edges
        .filter { "Story" in it.role }
        .mapNotNull { it.node.name() }
        .joinToString(", ")
        .ifEmpty { null },
    artists = staff.edges
        .filter { "Art" in it.role }
        .mapNotNull { it.node.name() }
        .joinToString(", ")
        .ifEmpty { null },
    tags = autofillTags(genres.orEmpty() + tags.orEmpty().map { it.name }),
    status = when (status) {
        "RELEASING" -> SManga.ONGOING.toLong()
        "FINISHED" -> SManga.COMPLETED.toLong()
        "CANCELLED" -> SManga.CANCELLED.toLong()
        "HIATUS" -> SManga.ON_HIATUS.toLong()
        else -> null
    },
)
// KNS <--

@Serializable
data class ALItemTitle(
    val userPreferred: String,
)

@Serializable
data class ItemCover(
    val large: String,
)

@Serializable
data class ALStaff(
    val edges: List<ALEdge>,
)

@Serializable
data class ALEdge(
    val role: String,
    val node: ALStaffNode,
)

@Serializable
data class ALStaffNode(
    val name: ALStaffName,
)

@Serializable
data class ALStaffName(
    val userPreferred: String?,
    val native: String?,
    val full: String?,
) {
    operator fun invoke(): String? {
        return userPreferred ?: full ?: native
    }
}
