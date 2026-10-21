package tachiyomi.domain.track.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track

// KNS -->
class TrackerProgressSyncTest {

    @Test
    fun `no tracker, a single tracker and equal trackers have no mismatch`() {
        TrackerProgressSync.progress(emptyList(), null) shouldBe 0.0
        TrackerProgressSync.hasMismatch(emptyList()) shouldBe false
        TrackerProgressSync.hasMismatch(listOf(track(1, 4.0))) shouldBe false
        TrackerProgressSync.hasMismatch(listOf(track(1, 4.0), track(2, 4.0))) shouldBe false
    }

    @Test
    fun `maximum and preferred progress identify lagging and ahead services`() {
        val tracks = listOf(track(1, 4.0), track(2, 9.5))
        TrackerProgressSync.progress(tracks, null) shouldBe 9.5
        TrackerProgressSync.mismatchedIds(tracks) shouldBe setOf(1L)
        TrackerProgressSync.progress(tracks, 1) shouldBe 4.0
        TrackerProgressSync.mismatchedIds(tracks, 1) shouldBe setOf(2L)
        TrackerProgressSync.progress(tracks, 3) shouldBe 9.5
        TrackerProgressSync.progress(listOf(track(1, Double.NaN), track(2, 3.0)), 1) shouldBe 3.0
    }

    @Test
    fun `the preferred track falls back to the furthest one`() {
        val tracks = listOf(track(1, 4.0), track(2, 9.0))
        TrackerProgressSync.resolvePreferredTrack(emptyList(), 1) shouldBe null
        TrackerProgressSync.resolvePreferredTrack(tracks.take(1), null) shouldBe tracks.first()
        TrackerProgressSync.resolvePreferredTrack(tracks, 1) shouldBe tracks.first()
        TrackerProgressSync.resolvePreferredTrack(tracks, 999) shouldBe tracks.last()
    }

    @Test
    fun `preferred tracker map round trip, clearing and malformed payloads`() {
        var raw = PreferredTrackerMap.update("", 10, 1)
        PreferredTrackerMap.decode(raw)[10] shouldBe 1L
        raw = PreferredTrackerMap.update(raw, 10, null)
        PreferredTrackerMap.decode(raw) shouldBe emptyMap()
        PreferredTrackerMap.decode("invalid") shouldBe emptyMap()
        PreferredTrackerMap.decode(" ".repeat(256_001)) shouldBe emptyMap()
        PreferredTrackerMap.decode("{\"bad\":1,\"-1\":2,\"3\":0,\"4\":2}") shouldBe mapOf(4L to 2L)
    }

    private fun track(trackerId: Long, progress: Double) = Track(
        id = trackerId, mangaId = 1, trackerId = trackerId, remoteId = trackerId, libraryId = null, title = "Title",
        lastChapterRead = progress, totalChapters = 100, status = 1, score = 0.0, remoteUrl = "",
        startDate = null, finishDate = null, private = false,
    )
}
// KNS <--
