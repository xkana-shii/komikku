package eu.kanade.tachiyomi.data.track.mangabaka.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class MBListItem(
    val note: String? = null,
    val read_link: String? = null,
    val rating: Double? = null,
    val state: String? = null,
    val priority: Int? = null,
    val is_private: Boolean? = null,
    val number_of_rereads: Int? = null,
    val progress_chapter: Int? = null,
    val progress_volume: Int? = null,
    val start_date: String? = null,
    val finish_date: String? = null,
    val id: Int? = null,
    val series_id: Long? = null,
    val user_id: String? = null,
    val Entries: List<MBEntry>? = null,
    val Series: MBRecord? = null,
)

@Serializable
data class MBEntry(
    val progress_chapter: Int? = null,
    val rating: Double? = null,
)

@Serializable
data class MBListItemRequest(
    val note: String? = null,
    val read_link: String? = null,
    val rating: Double? = null,
    val state: String? = null,
    val priority: Int? = null,
    val is_private: Boolean? = null,
    val number_of_rereads: Int? = null,
    val progress_chapter: Int? = null,
    val progress_volume: Int? = null,
    val start_date: String? = null,
    val finish_date: String? = null,
)

fun MBListItem.copyTo(track: Track, remoteTitle: String? = null): Track {
    val entry = Entries?.firstOrNull()
    return track.apply {
        this.status = when (state) {
            "reading" -> MangaBaka.READING
            "completed" -> MangaBaka.COMPLETED
            "paused" -> MangaBaka.PAUSED
            "dropped" -> MangaBaka.DROPPED
            "plan_to_read" -> MangaBaka.PLAN_TO_READ
            "rereading" -> MangaBaka.REREADING
            else -> MangaBaka.PLAN_TO_READ
        }
        this.last_chapter_read = entry?.progress_chapter?.toDouble()
            ?: progress_chapter?.toDouble()
            ?: 0.0
        this.total_chapters = Series?.total_chapters?.toLongOrNull() ?: 0L
        this.score = entry?.rating?.let { it / 10.0 }
            ?: rating?.let { it / 10.0 }
            ?: 0.0
        this.private = is_private ?: false
        this.started_reading_date = start_date?.let { parseDate(it) } ?: 0L
        this.finished_reading_date = finish_date?.let { parseDate(it) } ?: 0L
        remoteTitle?.takeIf { it.isNotBlank() }?.let {
            title = it.htmlDecode()
        }
    }
}

fun parseDate(date: String): Long {
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time
            ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(date)?.time
            ?: 0L
    } catch (_: Exception) {
        0L
    }
}

fun formatDate(epochMillis: Long): String {
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(epochMillis)
    } catch (_: Exception) {
        ""
    }
}

@Serializable
data class MBSeriesResponse(
    val status: Int,
    val data: MBRecord,
)
