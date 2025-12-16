package eu.kanade.tachiyomi.data.track.mangabaka

import android.graphics.Color
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class MangaBaka(id: Long) : BaseTracker(id, "MangaBaka"), DeletableTracker {

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        const val REREADING = 6L

        const val POINT_100 = "POINT_100"
        const val POINT_10 = "POINT_10_DECIMAL"
        const val POINT_5 = "POINT_5"
        const val POINT_3 = "POINT_3"
    }

    private val api by lazy { MangaBakaApi(client) }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val scorePreference = trackPreferences.mangaBakaScoreType()

    override fun getLogo() = R.drawable.ic_manga_baka

    override fun getLogoColor() = Color.rgb(36, 123, 160)

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REREADING -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = REREADING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> {
        return when (scorePreference.get()) {
            POINT_100 -> IntRange(0, 100).map(Int::toString).toImmutableList()
            POINT_10 -> IntRange(0, 100).map { (it / 10f).toString() }.toImmutableList()
            POINT_5 -> IntRange(0, 5).map { "$it ★" }.toImmutableList()
            POINT_3 -> persistentListOf("-", "😦", "😐", "😊")
            else -> throw Exception("Unknown score type")
        }
    }

    override fun displayScore(track: DomainTrack): String {
        val score = track.score
        return when (scorePreference.get()) {
            POINT_5 -> when (score) {
                0.0 -> "0 ★"
                else -> "${((score + 10) / 20).toInt()} ★"
            }

            POINT_3 -> when {
                score == 0.0 -> "0"
                score <= 35 -> "😦"
                score <= 60 -> "😐"
                else -> "😊"
            }

            else -> track.toApiScore()
        }
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remoteTrack = api.findLibraryManga(track)
        return if (remoteTrack != null) {
            track.copyFrom(remoteTrack)

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.search(query)
    }

    // SY feature
    override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_READ

    private suspend fun add(track: Track): Track {
        return api.addLibraryManga(track)
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else if (track.status != REREADING) {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }
        return api.updateLibraryManga(track)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibraryManga(track)
        track.copyFrom(remoteTrack)
        track.title = remoteTrack.title
        return track
    }

    override suspend fun delete(track: DomainTrack) {
        if (track.libraryId == null || track.libraryId == 0L) {
            val libManga = api.findLibraryManga(track.toDbTrack())
            if (libManga != null) {
                api.deleteLibraryManga(track.copy(id = libManga.id))
            }
        } else {
            api.deleteLibraryManga(track)
        }
    }

    // Updated login functionality with username and password
    override suspend fun login(username: String, password: String) {
        saveCredentials(username, password)
        interceptor.newAuth(password)
        val ok = api.testLibraryAuth()
        if (!ok) {
            logout()
            throw Exception("PAT is invalid or authentication failed")
        }
    }

    // Updated logout functionality
    override fun logout() {
        super.logout()
        trackPreferences.trackUsername(this).delete()
        trackPreferences.trackPassword(this).delete()
        interceptor.newAuth(null)
    }

    // Restore session for logged-in users
    fun restoreSession(): String? {
        return trackPreferences.trackPassword(this).get().ifBlank { null }
    }

    // Check if the user is logged in
    override val isLoggedIn: Boolean
        get() = !(
            trackPreferences.trackUsername(this).get().isNullOrEmpty() ||
                trackPreferences.trackPassword(this).get().isNullOrEmpty()
            )


}
