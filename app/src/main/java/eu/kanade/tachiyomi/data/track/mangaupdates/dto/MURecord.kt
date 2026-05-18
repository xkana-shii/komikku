package eu.kanade.tachiyomi.data.track.mangaupdates.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

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
    val authors: List<MUAuthor>? = null,
)

fun MURecord.toTrackSearch(id: Long): TrackSearch {
    return TrackSearch.create(id).apply {
        remote_id = this@toTrackSearch.seriesId ?: 0L
        title = this@toTrackSearch.title?.htmlDecode() ?: ""
        total_chapters = 0
        cover_url = this@toTrackSearch.image?.url?.original ?: ""
        summary = prepareDescription(this@toTrackSearch.description)
        tracking_url = this@toTrackSearch.url ?: ""
        publishing_status = ""
        publishing_type = this@toTrackSearch.type.toString()
        start_date = this@toTrackSearch.year.toString()
    }
}

@Serializable
data class MUAuthor(
    val type: String? = null,
    val name: String? = null,
)

private val UNESCAPE_HYPHEN = Regex("""\\-""")
private val UNESCAPE_NEWLINE = Regex("""\\n""")
private val LEADING_MARKDOWN_LIST = Regex("(?m)^\\s*-\\s+")
private val MULTI_NEWLINES = Regex("\\n{3,}")

fun prepareDescription(raw: String?): String {
    if (raw.isNullOrBlank()) return ""

    var s = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    s = UNESCAPE_HYPHEN.replace(s, "-")
    s = UNESCAPE_NEWLINE.replace(s, "\n")
    s = LEADING_MARKDOWN_LIST.replace(s, "• ")
    s = MULTI_NEWLINES.replace(s, "\n\n")
    val decoded = s.htmlDecode()

    return decoded.replace(Regex("\\n{3,}"), "\n\n").trim()
}
