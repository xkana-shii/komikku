package eu.kanade.presentation.track

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.suwayomi.Suwayomi
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.lang.toLocalDate
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import java.time.format.DateTimeFormatter

class TrackerSheetPresentationTest {
    private val format = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private fun item(id: Long, progress: Double = 3.0, score: Double = 0.0, capable: Boolean = true): TrackItem {
        val service = mockk<Tracker> {
            every { this@mockk.id } returns id
            every { getStatus(any()) } returns null
            every { getScoreList() } returns if (capable) persistentListOf("0", "1", "2") else persistentListOf()
            every { supportsReadingDates } returns capable
            every { supportsPrivateTracking } returns capable
            every { displayScore(any()) } returns score.toString()
            every { get10PointScore(any()) } returns score
        }
        return TrackItem(Track(id, 10, id, id, null, "Title", progress, 100, 1, score, "", 86400000, 172800000, false), service)
    }

    @Test
    fun `preferred service capabilities never fall back to a capable sibling`() {
        val one = item(1, capable = false)
        val two = item(2, progress = 8.0, score = 5.0)
        val first = TrackerSheetPresentation(listOf(one, two), 1, format, emptySet())
        first.primary shouldBe one
        first.supportsScore shouldBe false
        first.supportsReadingDates shouldBe false
        first.supportsPrivate shouldBe false
        first.score shouldBe null
        first.startDate shouldBe null
        first.finishDate shouldBe null
        // Changing the preference without changing the bindings updates every field.
        val second = TrackerSheetPresentation(listOf(one, two), 2, format, emptySet())
        second.primary shouldBe two
        second.supportsScore shouldBe true
        second.supportsReadingDates shouldBe true
        second.supportsPrivate shouldBe true
        second.score shouldBe "5.0"
        second.mismatchedIds shouldBe setOf(1L)
    }

    @Test
    fun `single and unified presentation use the supplied date formatter`() {
        val one = item(1)
        for (items in listOf(listOf(one), listOf(one, item(2)))) {
            for (formatter in listOf(format, DateTimeFormatter.ofPattern("yyyy/MM/dd"))) {
                val state = TrackerSheetPresentation(items, 1, formatter, emptySet())
                state.startDate shouldBe formatter.format(one.track!!.startDate.toLocalDate())
                state.finishDate shouldBe formatter.format(one.track.finishDate.toLocalDate())
            }
        }
    }

    @Test
    fun `unset or blank scores use placeholder and star scores do not get another star`() {
        val one = item(1)
        var state = TrackerSheetPresentation(listOf(one), 1, format, emptySet())
        state.status shouldBe MR.strings.reading
        state.score shouldBe null
        state.appendScoreStar shouldBe false
        val scored = one.copy(track = one.track!!.copy(score = 3.0))
        every { one.tracker.displayScore(any()) } returns "★★★"
        state = TrackerSheetPresentation(listOf(scored), 1, format, emptySet())
        state.score shouldBe "★★★"
        state.appendScoreStar shouldBe false
        every { one.tracker.displayScore(any()) } returns "3"
        TrackerSheetPresentation(listOf(scored), 1, format, emptySet()).appendScoreStar shouldBe true
        every { one.tracker.displayScore(any()) } returns " "
        TrackerSheetPresentation(listOf(scored), 1, format, emptySet()).score shouldBe null
    }

    @Test
    fun `enhanced services remain visible only when bound and cannot be rebound manually`() {
        val enhanced = mockk<Suwayomi> { every { id } returns 3L }
        val bound = TrackItem(item(3).track, enhanced)
        val unbound = TrackItem(null, enhanced)
        val manual = item(2).copy(track = null)
        bound.canChangeEntry shouldBe false
        unbound.canChangeEntry shouldBe false
        manual.canChangeEntry shouldBe true
        val state = TrackerSheetPresentation(listOf(item(1), unbound, manual), 1, format, setOf(3))
        state.visibleItems.map { it.tracker.id } shouldBe listOf(1L, 2L)
        state.errorIds shouldBe emptySet()
        val boundState = TrackerSheetPresentation(listOf(item(1), bound), 1, format, setOf(3))
        boundState.visibleItems.map { it.tracker.id } shouldBe listOf(1L, 3L)
        boundState.errorIds shouldBe setOf(3L)
    }

    @Test
    fun `summary uses native score normalization and excludes unset scores`() {
        val one = item(1, score = 80.0)
        val two = item(2, score = 3.0)
        every { one.tracker.get10PointScore(any()) } returns 8.0
        every { two.tracker.get10PointScore(any()) } returns 6.0
        val state = TrackerSheetPresentation(listOf(one, two, item(3)), 1, format, emptySet())
        state.averageScore shouldBe 7.0f
        state.scoredCount shouldBe 2
        TrackerSheetPresentation(listOf(one, item(3)), 1, format, emptySet()).averageScore shouldBe null
    }

    @Test
    fun `unavailable preferred tracker falls back to current progress and ignores stale error ids`() {
        val one = item(1)
        val two = item(2, progress = 8.0)
        val state = TrackerSheetPresentation(listOf(one, two), 99, format, setOf(1, 99))
        state.primary shouldBe two
        state.mismatchedIds shouldBe setOf(1L)
        state.errorIds shouldBe setOf(1L)
    }
}
