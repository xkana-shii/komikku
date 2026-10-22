package eu.kanade.tachiyomi.data.track.myanimelist.dto

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.autofillTags
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALManga(
    val id: Long,
    val title: String,
    val synopsis: String = "",
    @SerialName("num_chapters")
    val numChapters: Long,
    val mean: Double = -1.0,
    @SerialName("main_picture")
    val covers: MALMangaCovers?,
    val status: String,
    @SerialName("media_type")
    val mediaType: String,
    @SerialName("start_date")
    val startDate: String?,
    val authors: List<MALAuthorNode> = emptyList(),
)

@Serializable
data class MALAuthorNode(
    val node: MALAuthor,
    val role: String,
)

@Serializable
data class MALAuthor(
    val id: Int,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
) {
    fun getFullName(): String? = "$firstName $lastName".trim().ifBlank { null }
}

@Serializable
data class MALMangaCovers(
    val large: String?,
    val medium: String,
)

@Serializable
data class MALMangaMetadata(
    val id: Long,
    val title: String,
    val synopsis: String?,
    @SerialName("main_picture")
    val covers: MALMangaCovers,
    val authors: List<MALAuthorNode>,
    // KNS -->
    val genres: List<MALGenre>? = null,
    /** The series' publication status (not `my_list_status`). */
    val status: String? = null,
    // KNS <--
)

// KNS -->
@Serializable
data class MALGenre(
    val id: Long? = null,
    val name: String,
)

/**
 * Series metadata for autofill: "Story" (also "Story & Art") credits as authors, "Art" as artists,
 * genres as tags and the publication status as an `SManga` status (`not_yet_published`, unknown or
 * missing keeps the current one).
 */
internal fun MALMangaMetadata.toAutofillMetadata() = TrackMangaMetadata(
    remoteId = id,
    title = title,
    thumbnailUrl = covers.large?.ifEmpty { null } ?: covers.medium,
    description = synopsis,
    artists = authors
        .filter { authorNode -> authorNode.role == "Art" }
        .mapNotNull { authorNode -> authorNode.node.getFullName() }
        .joinToString()
        .ifEmpty { null },
    authors = authors
        .filter { authorNode -> authorNode.role.contains("Story") }
        .mapNotNull { authorNode -> authorNode.node.getFullName() }
        .joinToString()
        .ifEmpty { null },
    tags = autofillTags(genres.orEmpty().map { it.name }),
    status = when (status) {
        "currently_publishing" -> SManga.ONGOING.toLong()
        "finished" -> SManga.COMPLETED.toLong()
        "discontinued" -> SManga.CANCELLED.toLong()
        "on_hiatus" -> SManga.ON_HIATUS.toLong()
        else -> null
    },
)
// KNS <--
