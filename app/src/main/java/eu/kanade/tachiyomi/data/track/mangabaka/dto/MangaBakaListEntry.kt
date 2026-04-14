package eu.kanade.tachiyomi.data.track.mangabaka.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class MangaBakaListResult(
    val data: MangaBakaListEntry,
)

@Serializable
data class MangaBakaListEntry(
    val state: String,
    @SerialName("start_date")
    val startDate: String?,
    @SerialName("finish_date")
    val finishDate: String?,
    @SerialName("is_private")
    val isPrivate: Boolean,
    @SerialName("progress_chapter")
    val progressChapter: Double?,
    val rating: Long?,
    @SerialName("number_of_rereads")
    val numberOfRereads: Int? = null,
) {
    fun getStatus(): Long = when (state) {
        "considering" -> MangaBaka.CONSIDERING
        "completed" -> MangaBaka.COMPLETED
        "dropped" -> MangaBaka.DROPPED
        "paused" -> MangaBaka.PAUSED
        "plan_to_read" -> MangaBaka.PLAN_TO_READ
        "reading" -> MangaBaka.READING
        "rereading" -> MangaBaka.REREADING
        else -> throw NotImplementedError("Unknown status: $state")
    }

    fun toTrack(resolvedId: Long, seriesData: MangaBakaItem): Track =
        Track.create(TrackerManager.MANGABAKA).apply {
            remote_id = resolvedId
            title = seriesData.title
            status = getStatus()
            score = rating?.toDouble() ?: 0.0
            started_reading_date = startDate?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
            finished_reading_date = finishDate?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
            last_chapter_read = progressChapter ?: 0.0
            total_chapters = seriesData.totalChapters?.toLongOrNull() ?: 0
            private = isPrivate
        }

    fun mergeWithResolved(other: MangaBakaListEntry?): MangaBakaListEntry {
        if (other == null) return this

        val statusPriority = mapOf(
            "completed" to 7,
            "rereading" to 6,
            "reading" to 5,
            "paused" to 4,
            "dropped" to 3,
            "plan_to_read" to 2,
            "considering" to 1,
        )

        return other.copy(
            state = if ((statusPriority[other.state] ?: 0) >= (statusPriority[this.state] ?: 0)) {
                other.state
            } else {
                this.state
            },
            startDate = listOfNotNull(this.startDate, other.startDate).minOrNull(),
            finishDate = listOfNotNull(this.finishDate, other.finishDate).maxOrNull(),
            progressChapter = maxOf(this.progressChapter ?: 0.0, other.progressChapter ?: 0.0)
                .takeIf { it > 0.0 },
            rating = listOfNotNull(this.rating, other.rating).maxOrNull(),
            numberOfRereads = maxOf(this.numberOfRereads ?: 0, other.numberOfRereads ?: 0),
            isPrivate = this.isPrivate || other.isPrivate,
        )
    }
}
