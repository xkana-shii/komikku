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
        val previousListItem = api.getLibraryEntryWithSeries(track.remote_id)
        val releaseIsCompleted = previousListItem.Series?.status == "completed"
        val total = previousListItem.Series?.total_chapters?.toIntOrNull() ?: 0
        if (releaseIsCompleted && track.last_chapter_read > total && total > 0) {
            track.last_chapter_read = total.toDouble()
        }
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
        if (previousStatus == COMPLETED && track.status != COMPLETED) {
            when (track.status) {
                READING -> if (total > 0) track.last_chapter_read = (total - 1).toDouble()
                PLAN_TO_READ -> track.last_chapter_read = 0.0
            }
        }
        val progress = track.last_chapter_read.toInt()
        val statusEligible = track.status == READING || track.status == PLAN_TO_READ
        if (progress == total && total > 0 && releaseIsCompleted && statusEligible) {
            track.status = COMPLETED
            if (track.finished_reading_date == 0L) {
                track.finished_reading_date = System.currentTimeMillis()
            }
        }
        if (track.status != COMPLETED && didReadChapter) {
            if (track.started_reading_date == 0L) {
                track.started_reading_date = System.currentTimeMillis()
            }
        }
        val previousRereads = previousListItem.number_of_rereads ?: 0
        val wasRereading = previousListItem.state == "rereading"
        var rereadsToSend: Int? = null
        if (track.status == COMPLETED && wasRereading) {
            rereadsToSend = previousRereads + 1
        }
        try {
            api.updateSeriesEntryPatch(track, rereadsToSend)
        } catch (e: Exception) {
            throw e
        }
        track.tracking_url = "https://mangabaka.dev/${track.remote_id}"
        return track
    }
    override suspend fun delete(track: DomainTrack) {
        try {
            api.deleteSeriesEntry(track.remoteId)
        } catch (e: Exception) {
            throw e
        }
    }
    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        try {
            val item: MBListItem = api.getSeriesListItem(track.remote_id)
            item.copyTo(track)

            val seriesRecord: MBRecord? = try {
                api.getSeries(track.remote_id)
            } catch (_: Exception) {
                item.Series
            }

            val totalFromSeries = seriesRecord?.total_chapters?.toLongOrNull() ?: 0L
            if (totalFromSeries > 0L) {
                track.total_chapters = totalFromSeries
                if (seriesRecord?.status == "completed" && track.last_chapter_read > totalFromSeries) {
                    track.last_chapter_read = totalFromSeries.toDouble()
                }
            }

            autoCompleteIfFinished(track, seriesRecord ?: item.Series)

            if (track.status == 0L ||
                item.state.isNullOrBlank() ||
                !STATUS_SET.contains(track.status)
            ) {
                track.status = PLAN_TO_READ
            }
            track.tracking_url = "https://mangabaka.dev/${track.remote_id}"
            return track
        } catch (_: Exception) {
            track.score = 0.0
            try {
                api.addSeriesEntry(track, hasReadChapters)
                val seriesRecord: MBRecord? = try {
                    api.getSeries(track.remote_id)
                } catch (_: Exception) {
                    null
                }
                val totalFromSeries = seriesRecord?.total_chapters?.toLongOrNull() ?: 0L
                if (totalFromSeries > 0L) {
                    track.total_chapters = totalFromSeries
                    if (seriesRecord?.status == "completed" && track.last_chapter_read > totalFromSeries) {
                        track.last_chapter_read = totalFromSeries.toDouble()
                    }
                }
                autoCompleteIfFinished(track, seriesRecord)
                track.status = PLAN_TO_READ
                track.tracking_url = "https://mangabaka.dev/${track.remote_id}"
            } catch (e2: Exception) {
                throw e2
            }
            return track
        }
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
        return try {
            val results: List<MBRecord> = api.search(query)
            results.map { it.toTrackSearch(id) }
        } catch (_: Exception) {
            emptyList()
        }
    }
    override suspend fun refresh(track: Track): Track {
        return try {
            val item: MBListItem = api.getSeriesListItem(track.remote_id)
            item.copyTo(track)

            val seriesRecord: MBRecord? = try {
                api.getSeries(track.remote_id)
            } catch (_: Exception) {
                item.Series
            }
            val totalFromSeries = seriesRecord?.total_chapters?.toLongOrNull() ?: 0L
            if (totalFromSeries > 0L) {
                track.total_chapters = totalFromSeries
                if (seriesRecord?.status == "completed" && track.last_chapter_read > totalFromSeries) {
                    track.last_chapter_read = totalFromSeries.toDouble()
                }
            }
            autoCompleteIfFinished(track, seriesRecord ?: item.Series)

            track.tracking_url = "https://mangabaka.dev/${track.remote_id}"
            track
        } catch (e: Exception) {
            throw e
        }
    }
    override suspend fun login(username: String, password: String) {
        saveCredentials(username, password)
        interceptor.newAuth(password)
        try {
            api.testLibraryAuth()
        } catch (e: Exception) {
            logout()
            throw Exception("PAT is invalid or authentication failed", e)
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
        try {
            val record: MBRecord = api.getSeries(track.remoteId)
            return TrackMangaMetadata(
                record.id,
                record.title ?: "",
                record.cover?.raw?.url ?: "",
                record.description ?: "",
                record.authors?.joinToString(", ") ?: "",
                record.artists?.joinToString(", ") ?: "",
            )
        } catch (e: Exception) {
            throw e
        }
    }
    override suspend fun searchById(id: String): TrackSearch? {
        return try {
            api.getSeries(id.toLong()).toTrackSearch(this.id)
        } catch (_: Exception) {
            null
        }
    }
    override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_READ
}
