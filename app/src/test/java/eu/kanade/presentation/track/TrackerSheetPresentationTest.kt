package eu.kanade.presentation.track

import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.lang.toJavaLocalDate
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

// KNS --> the unified tracker card restored from the previous personal fork
class TrackerSheetPresentationTest {

    private val format = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun tracker(id: Long, capable: Boolean = true, score: String = "0"): Tracker = mockk {
        every { this@mockk.id } returns id
        every { getStatus(any()) } returns null
        every { getScoreList() } returns if (capable) listOf("0", "1", "2") else emptyList()
        every { supportsReadingDates } returns capable
        every { displayScore(any()) } returns score
    }

    private fun item(
        id: Long,
        progress: Double = 3.0,
        total: Long = 100,
        score: Double = 0.0,
        capable: Boolean = true,
        displayScore: String = score.toString(),
    ) = TrackItem(
        Track(
            id = id,
            mangaId = 10,
            trackerId = id,
            remoteId = id,
            libraryId = null,
            title = "Title",
            lastChapterRead = progress,
            totalChapters = total,
            status = 1,
            score = score,
            remoteUrl = "",
            startDate = Instant.fromEpochMilliseconds(86_400_000),
            finishDate = null,
            private = false,
        ),
        tracker(id, capable, displayScore),
    )

    @Test
    fun `the card shows the preferred tracker and marks the trackers that differ from it`() {
        val one = item(1, capable = false)
        val two = item(2, progress = 8.0, score = 5.0)
        val first = TrackerSheetPresentation(listOf(one, two), 1, format, emptySet())
        first.primary shouldBe one
        first.preferredTrackerId shouldBe 1L
        first.supportsScore shouldBe false
        first.supportsReadingDates shouldBe false
        first.score shouldBe null
        first.startDate shouldBe null
        first.mismatchedIds shouldBe setOf(2L)

        val second = TrackerSheetPresentation(listOf(one, two), 2, format, emptySet())
        second.primary shouldBe two
        second.supportsScore shouldBe true
        second.score shouldBe "5.0"
        second.chapters shouldBe "8/100"
        second.startDate shouldBe format.format(two.track!!.startDate!!.toJavaLocalDate())
        second.finishDate shouldBe null
        second.mismatchedIds shouldBe setOf(1L)
    }

    @Test
    fun `without an applicable preferred tracker the card follows the furthest progress and marks none`() {
        val one = item(1, progress = 2.0)
        val two = item(2, progress = 9.0, total = 0)
        val state = TrackerSheetPresentation(listOf(one, two, TrackItem(null, tracker(3))), 3, format, emptySet())
        state.primary shouldBe two
        state.preferredTrackerId shouldBe null
        state.chapters shouldBe "9"
        state.bound shouldBe listOf(one, two)
    }

    @Test
    fun `failures only mark linked trackers`() {
        val state = TrackerSheetPresentation(listOf(item(1), item(2)), null, format, setOf(2L, 5L))
        state.errorIds shouldBe setOf(2L)
    }

    @Test
    fun `unset or blank scores use the placeholder and star scores get no extra star`() {
        TrackerSheetPresentation(listOf(item(1)), 1, format, emptySet()).run {
            status shouldBe MR.strings.reading
            score shouldBe null
            appendScoreStar shouldBe false
        }
        TrackerSheetPresentation(listOf(item(1, score = 3.0, displayScore = "★★★")), 1, format, emptySet()).run {
            score shouldBe "★★★"
            appendScoreStar shouldBe false
        }
        TrackerSheetPresentation(listOf(item(1, score = 3.0, displayScore = "3")), 1, format, emptySet())
            .appendScoreStar shouldBe true
        TrackerSheetPresentation(listOf(item(1, score = 3.0, displayScore = " ")), 1, format, emptySet())
            .score shouldBe null
    }

    @Test
    fun `icons open and copy outside edit mode, and change the entry or the preferred tracker in it`() {
        val bound = item(1)
        val untracked = TrackItem(null, tracker(2))
        val enhanced = TrackItem(bound.track, mockk<Tracker>(moreInterfaces = *arrayOf(EnhancedTracker::class)))

        bound.unifiedIconClickAction(editMode = false) shouldBe UnifiedTrackerIconAction.OPEN
        bound.unifiedIconLongPressAction(editMode = false) shouldBe UnifiedTrackerIconAction.COPY_LINK
        bound.unifiedIconClickAction(editMode = true) shouldBe UnifiedTrackerIconAction.SEARCH
        bound.unifiedIconLongPressAction(editMode = true) shouldBe UnifiedTrackerIconAction.SET_PREFERRED

        untracked.unifiedIconClickAction(editMode = false) shouldBe UnifiedTrackerIconAction.SEARCH
        untracked.unifiedIconClickAction(editMode = true) shouldBe UnifiedTrackerIconAction.SEARCH
        untracked.unifiedIconLongPressAction(editMode = true) shouldBe UnifiedTrackerIconAction.NONE

        enhanced.unifiedIconClickAction(editMode = true) shouldBe UnifiedTrackerIconAction.NONE
        enhanced.unifiedIconLongPressAction(editMode = true) shouldBe UnifiedTrackerIconAction.SET_PREFERRED
    }
}
// KNS <--
