package eu.kanade.tachiyomi.data.track.mangabaka

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MBListItem
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MBRecord
import eu.kanade.tachiyomi.data.track.mangabaka.dto.copyTo
import eu.kanade.tachiyomi.data.track.mangabaka.dto.toTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.domain.track.model.Track as DomainTrack

class MangaBaka(id: Long) : BaseTracker(id, "MangaBaka"), DeletableTracker {
    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val PAUSED = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        const val REREADING = 6L
        private val STATUS_SET = setOf(READING, COMPLETED, PAUSED, DROPPED, PLAN_TO_READ, REREADING)
        private val SCORE_LIST = (0..100).map { i -> "%.1f".format(i / 10.0) }.toImmutableList()

        private const val URL_BASE = "https://mangabaka.org"
    }

    private val interceptor by lazy { MangaBakaInterceptor(this) }
    private val api by lazy { MangaBakaApi(interceptor, client) }

    override val supportsReadingDates: Boolean = true
    override val supportsPrivateTracking: Boolean = true

    override fun getLogo(): Int = R.drawable.ic_manga_baka
    override fun getLogoColor(): Int = Color.rgb(255, 102, 170)

    override fun getStatusList(): List<Long> = listOf(READING, COMPLETED, PAUSED, DROPPED, PLAN_TO_READ, REREADING)
    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        PAUSED -> MR.strings.paused
        DROPPED -> MR.strings.dropped
        PLAN_TO_READ -> MR.strings.plan_to_read
        REREADING -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = READING
    override fun getRereadingStatus(): Long = REREADING
    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST
    override fun indexToScore(index: Int): Double = SCORE_LIST[index].toDouble()
    override fun displayScore(track: DomainTrack): String = "%.1f".format(track.score)

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        val previousListItem: MBListItem = api.getLibraryEntryWithSeries(track.remote_id) ?: return track

        val total = previousListItem.Series?.total_chapters?.toIntOrNull() ?: 0

        val previousStatus = previousListItem.state?.let {
            when (it) {
                "reading" -> READING
                "completed" -> COMPLETED
                "paused" -> PAUSED
                "dropped" -> DROPPED
                "plan_to_read" -> PLAN_TO_READ
                "rereading" -> REREADING
                else -> PLAN_TO_READ
            }
        } ?: PLAN_TO_READ

        val previousRereads = previousListItem.number_of_rereads ?: 0
        val wasRereading = previousListItem.state == "rereading"
        var rereadsToSend: Int? = null
        if (track.status == COMPLETED && wasRereading) {
            rereadsToSend = previousRereads + 1
        }

        api.updateSeriesEntryPatch(track, rereadsToSend)

        track.tracking_url = "$URL_BASE/${track.remote_id}"
        return track
    }

    override suspend fun delete(track: DomainTrack) {
        api.deleteSeriesEntry(track.remoteId)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val item: MBListItem? = api.getSeriesListItem(track.remote_id)

        if (item != null) {
            try {
                item.copyTo(track)
            } catch (_: Exception) {
            }
            val seriesRecord: MBRecord? = api.getSeries(track.remote_id) ?: item.Series

            try {
                autoCompleteIfFinished(track, seriesRecord ?: item.Series)
            } catch (_: Exception) {
            }

            if (track.status == 0L ||
                item.state.isNullOrBlank() ||
                !STATUS_SET.contains(track.status)
            ) {
                track.status = PLAN_TO_READ
            }
            track.tracking_url = "$URL_BASE/${track.remote_id}"
            return track
        }

        track.score = 0.0
        val seriesRecord: MBRecord? = api.getSeries(track.remote_id)
        try {
            autoCompleteIfFinished(track, seriesRecord)
        } catch (_: Exception) {
        }
        track.status = PLAN_TO_READ
        track.tracking_url = "$URL_BASE/${track.remote_id}"
        return track
    }

    private fun autoCompleteIfFinished(track: Track, series: MBRecord?) {
        val releaseIsCompleted = series?.status == "completed"
        val progress = track.last_chapter_read.toInt()
        val total = series?.total_chapters?.toIntOrNull() ?: 0
        val statusEligible = track.status == READING || track.status == PLAN_TO_READ
        if (progress == total && total > 0 && releaseIsCompleted && statusEligible) {
            track.status = COMPLETED
            if (track.finished_reading_date == 0L) {
                track.finished_reading_date = System.currentTimeMillis()
            }
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        val results = api.search(query)
        return results.map { it.toTrackSearch(id) }
    }

    override suspend fun refresh(track: Track): Track {
        val item: MBListItem? = api.getSeriesListItem(track.remote_id)
        val seriesRecord: MBRecord? = api.getSeries(track.remote_id) ?: item.Series
        if (item != null) {
            try {
                val seriesRecord: MBRecord? = api.getSeries(track.remote_id) ?: item.Series
                item.copyTo(track, seriesRecord?.title ?: item.Series?.title)
            } catch (_: Exception) {
            }
            try {
                autoCompleteIfFinished(track, seriesRecord ?: item.Series)
            } catch (_: Exception) {
            }
            track.tracking_url = "$URL_BASE/${track.remote_id}"
            return track
        }

        val seriesOnly: MBRecord? = api.getSeries(track.remote_id)
        if (seriesOnly != null) {
            seriesOnly.title?.takeIf { it.isNotBlank() }?.let { title ->
                track.title = title.htmlDecode()
            }
            try {
                autoCompleteIfFinished(track, seriesOnly)
            } catch (_: Exception) {
            }
        }
        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials(username, password)
        interceptor.newAuth(password)
        val ok = api.testLibraryAuth()
        if (!ok) {
            logout()
            throw Exception("PAT is invalid or authentication failed")
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackUsername(this).delete()
        trackPreferences.trackPassword(this).delete()
        interceptor.newAuth(null)
    }

    fun restoreSession(): String? {
        return trackPreferences.trackPassword(this).get().ifBlank { null }
    }

    override val isLoggedIn: Boolean
        get() = !(
            trackPreferences.trackUsername(this).get().isNullOrEmpty() ||
                trackPreferences.trackPassword(this).get().isNullOrEmpty()
            )

    override suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        val record: MBRecord = api.getSeries(track.remoteId) ?: throw Exception("Failed to fetch series metadata")
        return TrackMangaMetadata(
            record.id,
            record.title ?: "",
            record.cover?.raw?.url ?: "",
            record.description ?: "",
            record.authors?.joinToString(", ") ?: "",
            record.artists?.joinToString(", ") ?: "",
        )
    }

    override suspend fun searchById(id: String): TrackSearch? {
        return api.getSeries(id.toLong())?.toTrackSearch(this.id)
    }

    override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_READ
}
