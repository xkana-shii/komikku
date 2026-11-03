package eu.kanade.tachiyomi.data.track.mangabaka

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.ReadingDates
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import tachiyomi.domain.manga.model.SequelPrequelEntry
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class MangaBaka(id: Long) : BaseTracker(id, "MangaBaka"), DeletableTracker {

    private val json: Json by injectLazy()

    private val interceptor by lazy { MangaBakaInterceptor(this) }
    private val api by lazy { MangaBakaApi(id, client, interceptor) }

    // KNS -->
    override suspend fun getRelatedEntries(remoteId: Long): List<SequelPrequelEntry> {
        val series = api.getSeries(remoteId) ?: return emptyList()
        return series.relationships.orEmpty()
            .distinctBy { it.seriesId }
            .take(MAX_RELATIONS)
            .mapNotNull { relation ->
                api.getSeries(relation.seriesId)?.toRelatedEntry(relation.relationType, id)
            }
    }
    // KNS <--

    override val supportsReadingDates: Boolean = true
    override val supportsPrivateTracking: Boolean = true

    private val scorePreference = trackPreferences.mangabakaScoreType

    override fun getLogo(): Int = R.drawable.brand_mangabaka

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, PAUSED, DROPPED, PLAN_TO_READ, REREADING, CONSIDERING)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        CONSIDERING -> MR.strings.considering
        COMPLETED -> MR.strings.completed
        DROPPED -> MR.strings.dropped
        PAUSED -> MR.strings.paused
        PLAN_TO_READ -> MR.strings.plan_to_read
        READING -> MR.strings.reading
        REREADING -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = REREADING

    override fun getCompletionStatus(): Long = COMPLETED

    // KUN -->
    override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_READ
    // KUN <--

    override fun getScoreList(): List<String> = getScoreRange().map(Int::toString)

    // score preference only dictates step size, scores are always 0-100
    override fun get10PointScore(track: DomainTrack): Double = track.score / 10.0

    override fun indexToScore(index: Int): Double = getScoreRange().toList()[index].toDouble()

    override fun displayScore(track: DomainTrack): String = track.score.toInt().toString()

    // KNS -->
    /**
     * Whether the chapters read reach the end of the series. Reaching MangaBaka's chapter count only ends a series
     * that MangaBaka lists as completed: a releasing, hiatus or upcoming one just has that many chapters so far,
     * so reading all of them (or more than the count it last had) stays Reading. The series is fetched for this,
     * which also refreshes the stored count; a failed request counts as not finished.
     */
    private suspend fun hasReachedEnd(track: Track): Boolean {
        if (track.total_chapters <= 0 || track.last_chapter_read < track.total_chapters) return false
        val series = try {
            api.getSeries(track.remote_id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return false
        series.chapterCount()?.let { track.total_chapters = it }
        return MangaBakaCompletion.isFinished(series.isFinished(), track.last_chapter_read, track.total_chapters)
    }

    override suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int): Track {
        if (
            track.last_chapter_read == 0.0 &&
            track.last_chapter_read < chapterNumber &&
            track.status != getRereadingStatus()
        ) {
            track.status = getReadingStatus()
        }
        track.last_chapter_read = chapterNumber.toDouble()
        if (hasReachedEnd(track)) {
            track.status = getCompletionStatus()
            track.finished_reading_date = ReadingDates.finished(track.finished_reading_date, System.currentTimeMillis())
        }
        updateRemote(track)
        return track
    }
    // KNS <--

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETED && didReadChapter) {
            // KNS --> only a series MangaBaka lists as completed is finished by its last chapter
            if (hasReachedEnd(track)) {
                // KNS <--
                track.status = COMPLETED
                // KNS --> completing (also a reread) keeps an existing finish date
                track.finished_reading_date = ReadingDates.finished(track.finished_reading_date, System.currentTimeMillis())
                // KNS <--
            } else if (track.status != REREADING) {
                track.status = READING
                if (track.last_chapter_read == 1.0) {
                    // KNS --> reading chapter 1 again keeps an existing start date
                    track.started_reading_date = ReadingDates.started(track.started_reading_date, System.currentTimeMillis())
                    // KNS <--
                }
            }
        }

        return api.updateLibManga(track)
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api.findLibManga(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.title = remoteTrack.title
            track.remote_id = remoteTrack.remote_id
            // KNS -->
            if (remoteTrack.total_chapters > 0) track.total_chapters = remoteTrack.total_chapters
            // KNS <--

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0

            api.addLibManga(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().toIntOrNull()?.let { id ->
                return api.getMangaDetails(id)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.findLibManga(track) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteTrack)
        track.remote_id = remoteTrack.remote_id
        track.title = remoteTrack.title
        // KNS --> keep the chapter count current: it is what the progress display and completion check use
        if (remoteTrack.total_chapters > 0) track.total_chapters = remoteTrack.total_chapters
        // KNS <--
        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.getAccessToken(code)
            interceptor.setAuth(oauth)
            val currentUser = api.getCurrentUser()
            val scoreType = when (currentUser.ratingSteps) {
                1 -> STEP_1
                5 -> STEP_5
                10 -> STEP_10
                20 -> STEP_20
                25 -> STEP_25
                else -> throw Exception("Unknown score step size ${currentUser.ratingSteps}")
            }
            scorePreference.set(scoreType)
            saveDisplayUsername(currentUser.nickname ?: currentUser.preferredUsername ?: currentUser.id)
            saveCredentials("user", oauth.accessToken)
        } catch (_: Exception) {
            logout()
        }
    }

    override suspend fun updateUserConfig() {
        val currentUser = api.getCurrentUser()
        val scoreType = when (currentUser.ratingSteps) {
            1 -> STEP_1
            5 -> STEP_5
            10 -> STEP_10
            20 -> STEP_20
            25 -> STEP_25
            else -> throw Exception("Unknown score step size ${currentUser.ratingSteps}")
        }
        scorePreference.set(scoreType)
        saveDisplayUsername(currentUser.nickname ?: currentUser.preferredUsername ?: currentUser.id)
    }

    fun saveToken(oauth: MangaBakaOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): MangaBakaOAuth? {
        return try {
            json.decodeFromString(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }

    fun verifyOAuthState(state: String): Boolean = api.verifyOAuthState(state)

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    // KNS -->
    override val supportsMangaMetadata = true

    override suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata = api.getMangaMetadata(track)
    // KNS <--

    override suspend fun delete(track: DomainTrack) {
        api.deleteLibManga(track)
    }

    private fun getScoreRange(): IntProgression = when (scorePreference.get()) {
        STEP_1 -> STEP_1_SCORES
        STEP_5 -> STEP_5_SCORES
        STEP_10 -> STEP_10_SCORES
        STEP_20 -> STEP_20_SCORES
        STEP_25 -> STEP_25_SCORES
        else -> throw Exception("Unknown score type")
    }

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val PAUSED = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        const val REREADING = 6L
        const val CONSIDERING = 7L

        const val STEP_1 = "STEP_1"
        const val STEP_5 = "STEP_5"
        const val STEP_10 = "STEP_10"
        const val STEP_20 = "STEP_20"
        const val STEP_25 = "STEP_25"

        // 1, 2, ..., 99, 100
        private val STEP_1_SCORES = IntRange(0, 100)

        // 5, 10, ..., 95, 100
        private val STEP_5_SCORES = IntRange(0, 100).step(5)

        // 10, 20, ..., 90, 100
        private val STEP_10_SCORES = IntRange(0, 100).step(10)

        // 20, 40, ..., 80, 100
        private val STEP_20_SCORES = IntRange(0, 100).step(20)

        // 25, 50, 75, 100
        private val STEP_25_SCORES = IntRange(0, 100).step(25)

        private const val SEARCH_ID_PREFIX = "id:"
    }
}
