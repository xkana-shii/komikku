package tachiyomi.domain.track.service

import tachiyomi.domain.track.model.Track
import kotlin.math.abs

// KNS -->
/**
 * Reading progress across several linked trackers: which one leads, and which ones lag behind. Invalid
 * progress (negative, NaN or infinite) never counts.
 */
object TrackerProgressSync {

    private fun Double.isValidProgress() = isFinite() && this >= 0

    /** The preferred tracker's binding, else the one furthest ahead, else any. */
    fun resolvePreferredTrack(tracks: List<Track>, preferredId: Long?): Track? =
        tracks.find { it.trackerId == preferredId }
            ?: tracks.filter { it.lastChapterRead.isValidProgress() }.maxByOrNull { it.lastChapterRead }
            ?: tracks.firstOrNull()

    fun maxProgress(tracks: List<Track>): Double =
        tracks.map { it.lastChapterRead }.filter { it.isValidProgress() }.maxOrNull() ?: 0.0

    /** The preferred tracker's valid progress, else the furthest progress. */
    fun progress(tracks: List<Track>, preferredId: Long?): Double =
        tracks.find { it.trackerId == preferredId }?.lastChapterRead?.takeIf { it.isValidProgress() }
            ?: maxProgress(tracks)

    /** Trackers whose progress differs from [progress]. */
    fun mismatchedIds(tracks: List<Track>, preferredId: Long? = null): Set<Long> {
        val target = progress(tracks, preferredId)
        return tracks.filter { abs(it.lastChapterRead - target) > 0.01 }.mapTo(mutableSetOf()) { it.trackerId }
    }

    fun hasMismatch(tracks: List<Track>, preferredId: Long? = null): Boolean =
        tracks.size > 1 && mismatchedIds(tracks, preferredId).isNotEmpty()
}
// KNS <--
