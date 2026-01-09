package eu.kanade.tachiyomi.data.track.mangabaka

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.toDomainTrack
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
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
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

    // Persist updated tracks so status/remote_id changes are saved locally.
    private val trackRepository: TrackRepository by injectLazy()

    override val supportsReadingDates: Boolean = true
    override val supportsPrivateTracking: Boolean = true

    override fun getLogo(): Int = R.drawable.ic_manga_baka

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
        // Try to get the library entry for the current remote_id.
        var previousListItem: MBListItem? = api.getLibraryEntryWithSeries(track.remote_id)

        // If we didn't find an entry, check if the series was merged and resolve to final id.
        if (previousListItem == null) {
            val resolvedSeries = api.getSeries(track.remote_id)
            if (resolvedSeries != null && resolvedSeries.id != track.remote_id) {
                // Use the final id from the resolved series.
                val oldId = track.remote_id
                track.remote_id = resolvedSeries.id
                // Try to get the library entry for the final id.
                previousListItem = api.getLibraryEntryWithSeries(track.remote_id)
                // If still not present in library, add it so updates will work.
                if (previousListItem == null) {
                    val added = api.addSeriesEntry(track, track.last_chapter_read > 0)
                    if (added) {
                        previousListItem = api.getLibraryEntryWithSeries(track.remote_id)
                    }
                }
            }
        }

        // If still no library entry, bail out (previous behavior).
        val previousList = previousListItem ?: return track

        val total = previousList.Series?.total_chapters?.toIntOrNull() ?: 0

        val previousStatus = previousList.state?.let {
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

        // Prefer Series from the library entry if it's not merged; otherwise fetch the (resolved) series.
        val seriesRecord: MBRecord? = previousList.Series
            ?.takeIf { it.merged_with == null }
            ?: api.getSeries(track.remote_id)
            ?: previousList.Series

        val totalFromSeries = seriesRecord?.total_chapters?.toLongOrNull() ?: 0L

        // Determine server-side previous progress so we can detect a local increase even if caller didn't set didReadChapter.
        val serverProgress = previousList.Entries?.firstOrNull()?.progress_chapter
            ?: previousList.progress_chapter
            ?: 0

        // If caller indicated a chapter read OR local progress increased vs server, treat as read event.
        val effectiveDidRead = didReadChapter || (track.last_chapter_read.toInt() > serverProgress)

        // Handle read transitions AFTER we know series totals/status.
        if (track.status != COMPLETED && effectiveDidRead) {
            if (track.started_reading_date == 0L) {
                track.started_reading_date = System.currentTimeMillis()
            }

            val progress = track.last_chapter_read.toInt()

            // If progress equals total and release is completed, set COMPLETED.
            if (totalFromSeries > 0L && progress == totalFromSeries.toInt() && seriesRecord?.status == "completed") {
                track.status = COMPLETED
                if (track.finished_reading_date == 0L) {
                    track.finished_reading_date = System.currentTimeMillis()
                }
            } else {
                // Only set READING when more than 1 chapter has been read.
                if (progress > 1) {
                    track.status = READING
                }
                // If progress is 1 or 0, preserve existing status (likely PLAN_TO_READ).
            }
        }

        // Also ensure if progress indicates they've started reading (>1) and not completed, set READING.
        if (track.status != COMPLETED && track.last_chapter_read.toInt() > 1) {
            track.status = READING
        }

        // If the series was merged, update the tracked id so subsequent reads/writes use the final id.
        seriesRecord?.id?.let { newId ->
            if (newId != track.remote_id) {
                track.remote_id = newId
            }
        }

        if (totalFromSeries > 0L) {
            track.total_chapters = totalFromSeries
            if (seriesRecord?.status == "completed" && track.last_chapter_read > totalFromSeries) {
                track.last_chapter_read = totalFromSeries.toDouble()
            }
        }

        try {
            autoCompleteIfFinished(track, seriesRecord)
        } catch (_: Exception) {
        }

        val previousRereads = previousList.number_of_rereads ?: 0
        val wasRereading = previousList.state == "rereading"
        var rereadsToSend: Int? = null
        if (track.status == COMPLETED && wasRereading) {
            rereadsToSend = previousRereads + 1
        }

        api.updateSeriesEntryPatch(track, rereadsToSend)

        track.tracking_url = "$URL_BASE/${track.remote_id}"

        // Persist the updated track (status/remote_id/chapters) so UI/DB reflects changes.
        try {
            track.toDomainTrack()?.let { domainTrack ->
                trackRepository.insert(domainTrack)
            }
        } catch (_: Exception) {
        }

        return track
    }

    override suspend fun delete(track: DomainTrack) {
        api.deleteSeriesEntry(track.remoteId)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        // Try to get the library item for the current tracked id.
        var item: MBListItem? = api.getSeriesListItem(track.remote_id)

        // If not found, resolve merges and ensure library entry exists for final id.
        if (item == null) {
            val resolvedSeries = api.getSeries(track.remote_id)
            if (resolvedSeries != null && resolvedSeries.id != track.remote_id) {
                val oldId = track.remote_id
                track.remote_id = resolvedSeries.id
                item = api.getSeriesListItem(track.remote_id)
                if (item == null) {
                    val added = api.addSeriesEntry(track, hasReadChapters)
                    if (added) {
                        item = api.getSeriesListItem(track.remote_id)
                    }
                }
            }
        }

        if (item != null) {
            try {
                item.copyTo(track)
            } catch (_: Exception) {
            }

            // Prefer Series from the library entry if it's not merged; otherwise fetch the (resolved) series.
            val seriesRecord: MBRecord? = item.Series
                ?.takeIf { it.merged_with == null }
                ?: api.getSeries(track.remote_id)
                ?: item.Series

            // Update tracked id if the series has been merged.
            seriesRecord?.id?.let { newId ->
                if (newId != track.remote_id) {
                    track.remote_id = newId
                }
            }
            val totalFromSeries = seriesRecord?.total_chapters?.toLongOrNull() ?: 0L
            if (totalFromSeries > 0L) {
                track.total_chapters = totalFromSeries
                if (seriesRecord?.status == "completed" && track.last_chapter_read > totalFromSeries) {
                    track.last_chapter_read = totalFromSeries.toDouble()
                }
            }

            try {
                autoCompleteIfFinished(track, seriesRecord ?: item.Series)
            } catch (_: Exception) {
            }

            // If status wasn't set by copyTo, infer from the library item or progress.
            if (track.status == 0L || item.state.isNullOrBlank() || !STATUS_SET.contains(track.status)) {
                val inferred = when (item.state) {
                    "reading" -> READING
                    "completed" -> COMPLETED
                    "paused" -> PAUSED
                    "dropped" -> DROPPED
                    "rereading" -> REREADING
                    else -> if (track.last_chapter_read.toInt() > 1) READING else PLAN_TO_READ
                }
                track.status = inferred
            }

            track.tracking_url = "$URL_BASE/${track.remote_id}"

            // Persist after bind so status changes are saved
            try {
                track.toDomainTrack()?.let { domainTrack ->
                    trackRepository.insert(domainTrack)
                }
            } catch (_: Exception) {
            }

            return track
        }

        // If still no item found after attempting to resolve/add, create an entry for the current id (fallback).
        track.score = 0.0
        val created = api.addSeriesEntry(track, hasReadChapters)
        val seriesRecord: MBRecord? = api.getSeries(track.remote_id)
        // After creating/updating the entry, if the API reports a final id, update it locally.
        seriesRecord?.id?.let { newId ->
            if (newId != track.remote_id) {
                track.remote_id = newId
            }
        }
        try {
            autoCompleteIfFinished(track, seriesRecord)
        } catch (_: Exception) {
        }
        // Set status based on whether the user has read more than one chapter; otherwise keep plan to read.
        track.status = if (track.last_chapter_read.toInt() > 1) READING else PLAN_TO_READ
        track.tracking_url = "$URL_BASE/${track.remote_id}"

        try {
            track.toDomainTrack()?.let { domainTrack ->
                trackRepository.insert(domainTrack)
            }
        } catch (_: Exception) {
        }

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
        var item: MBListItem? = api.getSeriesListItem(track.remote_id)
        if (item == null) {
            val resolvedSeries = api.getSeries(track.remote_id)
            if (resolvedSeries != null && resolvedSeries.id != track.remote_id) {
                val old = track.remote_id
                track.remote_id = resolvedSeries.id
            }

            // Try to get library entry for (possibly) updated id and add if missing.
            item = api.getSeriesListItem(track.remote_id)
            if (item == null) {
                val added = api.addSeriesEntry(track, track.last_chapter_read > 0)
                if (added) {
                    item = api.getSeriesListItem(track.remote_id)
                }
            }
        }

        if (item != null) {
            try {
                val seriesRecord: MBRecord? = api.getSeries(track.remote_id) ?: item.Series
                // Update tracked id if merged
                seriesRecord?.id?.let { newId ->
                    if (newId != track.remote_id) {
                        track.remote_id = newId
                    }
                }
                item.copyTo(track, seriesRecord?.title ?: item.Series?.title)
                val totalFromSeries = seriesRecord?.total_chapters?.toLongOrNull() ?: 0L
                if (totalFromSeries > 0L) {
                    track.total_chapters = totalFromSeries
                    if (seriesRecord?.status == "completed" && track.last_chapter_read > totalFromSeries) {
                        track.last_chapter_read = totalFromSeries.toDouble()
                    }
                }
            } catch (_: Exception) {
            }
            val seriesRecord: MBRecord? = api.getSeries(track.remote_id) ?: item.Series
            try {
                autoCompleteIfFinished(track, seriesRecord ?: item.Series)
            } catch (_: Exception) {
            }
            track.tracking_url = "$URL_BASE/${track.remote_id}"

            try {
                track.toDomainTrack()?.let { domainTrack ->
                    trackRepository.insert(domainTrack)
                }
            } catch (_: Exception) {
            }

            return track
        }

        val seriesOnly: MBRecord? = api.getSeries(track.remote_id)
        if (seriesOnly != null) {
            // If the API says the series was merged, update the track id to the final one.
            seriesOnly.id.let { newId ->
                if (newId != track.remote_id) {
                    track.remote_id = newId
                }
            }
            seriesOnly.title?.takeIf { it.isNotBlank() }?.let { title ->
                track.title = title.htmlDecode()
            }
            val totalFromSeries = seriesOnly.total_chapters?.toLongOrNull() ?: 0L
            if (totalFromSeries > 0L) {
                track.total_chapters = totalFromSeries
                if (seriesOnly.status == "completed" && track.last_chapter_read > totalFromSeries) {
                    track.last_chapter_read = totalFromSeries.toDouble()
                }
            }
            try {
                autoCompleteIfFinished(track, seriesOnly)
            } catch (_: Exception) {
            }

            try {
                track.toDomainTrack()?.let { domainTrack ->
                    trackRepository.insert(domainTrack)
                }
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
