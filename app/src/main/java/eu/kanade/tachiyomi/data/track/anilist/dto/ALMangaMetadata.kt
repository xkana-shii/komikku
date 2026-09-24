package eu.kanade.tachiyomi.data.track.anilist.dto

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
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
    // KMK -->
    val genres: List<String>? = null,
    val tags: List<ALTag>? = null,
    val status: String? = null,
    // KMK <--
)

@Serializable
data class ALTag(
    val name: String,
)

// KMK --> The API and autofill tests share the same metadata conversion.
internal fun ALMangaMetadataMedia.toAutofillMetadata(): TrackMangaMetadata {
    val media = this
    return TrackMangaMetadata(
        remoteId = media.id,
        title = media.title.userPreferred,
        thumbnailUrl = media.coverImage.large,
        description = media.description?.htmlDecode()?.ifEmpty { null },
        authors = media.staff.edges
            .filter { "Story" in it.role }
            .mapNotNull { it.node.name() }
            .joinToString(", ")
            .ifEmpty { null },
        artists = media.staff.edges
            .filter { "Art" in it.role }
            .mapNotNull { it.node.name() }
            .joinToString(", ")
            .ifEmpty { null },
        // KMK -->
        tags = (media.genres.orEmpty() + media.tags.orEmpty().map { it.name }).distinct().ifEmpty { null },
        status = when (media.status) {
            "FINISHED" -> SManga.COMPLETED.toLong()
            "RELEASING" -> SManga.ONGOING.toLong()
            "NOT_YET_RELEASED" -> null
            "CANCELLED" -> SManga.CANCELLED.toLong()
            "HIATUS" -> SManga.ON_HIATUS.toLong()
            else -> null
        },
        // KMK <--
    )
}
// KMK <--
