package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBaka
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tachiyomi.domain.track.model.Track

class NormalizedScoreTest {
    private fun track(score: Double) = Track(1, 10, 1, 100, null, "Title", 0.0, 0, 1, score, "", 0, 0, false)

    @ParameterizedTest
    @CsvSource("7.5,7.5", "0.0,0.0", "10.0,10.0", "-2.0,0.0", "90.0,10.0")
    fun `default tracker constrains native ten point scores`(remote: Double, normalized: Double) {
        val service = mockk<BaseTracker>()
        every { service.get10PointScore(any()) } answers { callOriginal() }
        service.get10PointScore(track(remote)) shouldBe normalized
    }

    @Test
    fun `AniList and MangaBaka retain their native hundred point conversions`() {
        // Call each production override without constructing its unrelated network and preference dependencies.
        val anilist = mockk<Anilist>()
        val mangaBaka = mockk<MangaBaka>()
        every { anilist.get10PointScore(any()) } answers { callOriginal() }
        every { mangaBaka.get10PointScore(any()) } answers { callOriginal() }
        for (service in listOf<Tracker>(anilist, mangaBaka)) {
            service.get10PointScore(track(75.0)) shouldBe 7.5
            service.get10PointScore(track(100.0)) shouldBe 10.0
            service.get10PointScore(track(0.0)) shouldBe 0.0
        }
        listOf(anilist.get10PointScore(track(80.0)), mangaBaka.get10PointScore(track(60.0)))
            .average() shouldBe 7.0
    }
}
