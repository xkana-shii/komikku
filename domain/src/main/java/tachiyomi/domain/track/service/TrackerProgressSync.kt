package tachiyomi.domain.track.service

import tachiyomi.domain.track.model.Track
import kotlin.math.abs

// KMK --> Pure progress decisions shared by reader and tracking UI.
object TrackerProgressSync {
    fun resolvePreferredTrack(tracks: List<Track>, preferredId: Long?): Track? =
        tracks.find { it.trackerId == preferredId }
            ?: tracks.filter { it.lastChapterRead.isFinite() && it.lastChapterRead >= 0 }.maxByOrNull { it.lastChapterRead }
            ?: tracks.firstOrNull()

    fun maxProgress(tracks: List<Track>): Double = tracks.map { it.lastChapterRead }.filter { it.isFinite() && it >= 0 }.maxOrNull() ?: 0.0

    fun preferredValue(tracks: List<Track>, preferredId: Long?): Double? =
        tracks.find { it.trackerId == preferredId }?.lastChapterRead?.takeIf { it.isFinite() && it >= 0 }

    fun progress(tracks: List<Track>, preferredId: Long?): Double = preferredValue(tracks, preferredId) ?: maxProgress(tracks)

    fun mismatchedIds(tracks: List<Track>, preferredId: Long? = null): Set<Long> {
        val target = progress(tracks, preferredId)
        return tracks.filter { abs(it.lastChapterRead - target) > 0.01 }.map { it.trackerId }.toSet()
    }

    fun hasMismatch(tracks: List<Track>): Boolean = tracks.size > 1 && mismatchedIds(tracks).isNotEmpty()
}
// KMK <--
