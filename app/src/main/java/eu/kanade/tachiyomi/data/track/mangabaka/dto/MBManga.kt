package eu.kanade.tachiyomi.data.track.mangabaka.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mangabaka.MangabakaApi
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import java.text.SimpleDateFormat
import java.util.Locale

data class MBManga(
    val remoteId: Long,
    val title: String,
    val imageUrl: String,
    val description: String?,
    val format: String,
    val publishingStatus: String,
    val startDateFuzzy: Long,
    val totalChapters: Long,
    val averageScore: Int,
    val staff: MBStaff,
) {
    fun toTrack() = TrackSearch.create(TrackerManager.MANGABAKA).apply {
        remote_id = remoteId
        title = this@MBManga.title
        total_chapters = totalChapters
        cover_url = imageUrl
        summary = description?.htmlDecode() ?: ""
        score = averageScore.toDouble()
        tracking_url = MangabakaApi.mangaUrl(remote_id)
        publishing_status = publishingStatus
        publishing_type = format
        if (startDateFuzzy != 0L) {
            start_date = try {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                outputDf.format(startDateFuzzy)
            } catch (e: IllegalArgumentException) {
                ""
            }
        }
        staff.edges.forEach {
            val name = it.node.name() ?: return@forEach
            if ("Story" in it.role) authors += name
            if ("Art" in it.role) artists += name
        }
    }

    data class MBUserManga(
        val libraryId: Long,
        val listStatus: String,
        val scoreRaw: Int,
        val chaptersRead: Int,
        val startDateFuzzy: Long,
        val completedDateFuzzy: Long,
        val private: Boolean,
    ) {
        fun toTrack() = Track.create(TrackerManager.MANGABAKA).apply {
            remote_id = this@MBUserManga.libraryId
            status = toTrackStatus()
            score = scoreRaw.toDouble()
            started_reading_date = startDateFuzzy
            finished_reading_date = completedDateFuzzy
            last_chapter_read = chaptersRead.toDouble()
            library_id = libraryId
        }

        private fun toTrackStatus() = when (listStatus) {
            "CURRENT" -> TrackerManager.READING
            "COMPLETED" -> TrackerManager.COMPLETED
            "PAUSED" -> TrackerManager.ON_HOLD
            "DROPPED" -> TrackerManager.DROPPED
            "PLANNING" -> TrackerManager.PLAN_TO_READ
            "REPEATING" -> TrackerManager.REREADING
            else -> throw NotImplementedError("Unknown status: $listStatus")
        }
    }
}
