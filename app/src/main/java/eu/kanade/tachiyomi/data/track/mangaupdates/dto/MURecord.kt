package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.model.autofillTags
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MURecord(
    @SerialName("series_id")
    val seriesId: Long? = null,
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    val image: MUImage? = null,
    val type: String? = null,
    val year: String? = null,
    @SerialName("bayesian_rating")
    val bayesianRating: Double? = null,
    @SerialName("rating_votes")
    val ratingVotes: Int? = null,
    @SerialName("latest_chapter")
    val latestChapter: Int? = null,
    val authors: List<MUAuthor> = emptyList(),
    // KNS --> series-detail fields (GET /v1/series/{id}) for metadata autofill
    val genres: List<MUGenre>? = null,
    val categories: List<MUCategory>? = null,
    /** Free text "status in country of origin", e.g. `13 Volumes (Ongoing)`. */
    val status: String? = null,
    // KNS <--
) {
    fun toTrackSearch(id: Long): TrackSearch {
        return TrackSearch.create(id).apply {
            remote_id = this@MURecord.seriesId ?: 0L
            title = this@MURecord.title?.htmlDecode() ?: ""
            total_chapters = 0
            cover_url = this@MURecord.image?.url?.original ?: ""
            summary = this@MURecord.description?.htmlDecode() ?: ""
            tracking_url = this@MURecord.url ?: ""
            publishing_status = ""
            publishing_type = this@MURecord.type.toString()
            start_date = this@MURecord.year.toString()
            score = this@MURecord.bayesianRating?.takeIf { it > 0 } ?: -1.0
            authors = this@MURecord.authors.filter { it.type == "Author" }.map { it.name }
            artists = this@MURecord.authors.filter { it.type == "Artist" }.map { it.name }
        }
    }
}

// KNS -->
@Serializable
data class MUGenre(
    val genre: String? = null,
)

@Serializable
data class MUCategory(
    val category: String? = null,
    @SerialName("votes_plus")
    val votesPlus: Int? = null,
    @SerialName("votes_minus")
    val votesMinus: Int? = null,
)

/**
 * Series metadata for autofill: genres, then the categories readers voted up (a category with more
 * down- than up-votes is left out; one without vote counts is kept), and the publication status in
 * the country of origin. `completed` is not used: on MangaUpdates it means "completely scanlated".
 */
internal fun MURecord.toAutofillMetadata() = TrackMangaMetadata(
    remoteId = seriesId,
    title = title?.htmlDecode(),
    thumbnailUrl = image?.url?.original,
    description = description?.htmlDecode(),
    // KUN --> (moved from MangaUpdates.getMangaMetadata)
    // MUAuthor.type/name are non-null in this fork, so the safe calls/Elvis checks were dead.
    authors = authors.filter { "Author" in it.type }.joinToString(separator = ", ") { it.name }.ifEmpty { null },
    artists = authors.filter { "Artist" in it.type }.joinToString(separator = ", ") { it.name }.ifEmpty { null },
    // KUN <--
    tags = autofillTags(
        genres.orEmpty().map { it.genre } +
            categories.orEmpty()
                .filter { (it.votesPlus ?: 0) >= (it.votesMinus ?: 0) }
                .map { it.category },
    ),
    status = muPublicationStatus(status),
)

/**
 * The publication status in a MangaUpdates status text (which can list several parts, e.g. a
 * complete season and an ongoing one): hiatus first, then ongoing, cancelled, completed; anything
 * else keeps the current status.
 */
internal fun muPublicationStatus(text: String?): Long? {
    val status = text?.lowercase() ?: return null
    return when {
        "hiatus" in status -> SManga.ON_HIATUS
        "ongoing" in status -> SManga.ONGOING
        "cancel" in status || "discontinued" in status || "dropped" in status -> SManga.CANCELLED
        "complete" in status -> SManga.COMPLETED
        else -> null
    }?.toLong()
}
// KNS <--
