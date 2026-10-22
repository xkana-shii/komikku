package eu.kanade.tachiyomi.ui.reader.automation

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class ReaderAutomationTest {

    @Test
    fun `the duration limit stops the run, 0 means no limit`() {
        val limited = AutomationSession(maxMinutes = 2, maxChapters = 0, startedAtMillis = 0, startChapterId = 1)
        limited.check(nowMillis = 119_999, chapterId = 1, active = true) shouldBe null
        limited.check(nowMillis = 120_000, chapterId = 1, active = true) shouldBe AutomationStop.MAX_MINUTES

        val unlimited = AutomationSession(maxMinutes = 0, maxChapters = 0, startedAtMillis = 0, startChapterId = 1)
        unlimited.check(nowMillis = 10 * 3_600_000L, chapterId = 1, active = true) shouldBe null
    }

    @Test
    fun `chapters are counted when the reader moves on`() {
        val session = AutomationSession(maxMinutes = 0, maxChapters = 2, startedAtMillis = 0, startChapterId = 1)
        session.check(1, chapterId = 1, active = true) shouldBe null
        session.check(2, chapterId = 2, active = true) shouldBe null
        session.chaptersRead shouldBe 1
        session.check(3, chapterId = 2, active = true) shouldBe null
        session.check(4, chapterId = 3, active = true) shouldBe AutomationStop.MAX_CHAPTERS
    }

    @Test
    fun `an unknown start chapter does not count as read`() {
        val session = AutomationSession(maxMinutes = 0, maxChapters = 1, startedAtMillis = 0, startChapterId = null)
        session.check(1, chapterId = 5, active = true) shouldBe null
        session.check(2, chapterId = 6, active = true) shouldBe AutomationStop.MAX_CHAPTERS
    }

    @Test
    fun `an inactive reader stops the run first`() {
        AutomationSession(maxMinutes = 1, maxChapters = 1, startedAtMillis = 0, startChapterId = 1)
            .check(nowMillis = 999_999, chapterId = 9, active = false) shouldBe AutomationStop.INACTIVE
    }

    @Test
    fun `a stalled viewer ends the run only after consecutive stalls`() {
        val detector = StallDetector(maxStalledFrames = 3)
        detector.record(false) shouldBe false
        detector.record(false) shouldBe false
        detector.record(true) shouldBe false
        detector.record(false) shouldBe false
        detector.record(false) shouldBe false
        detector.record(false) shouldBe true
    }

    @Test
    fun `a reset stall counts again from zero while the next chapter loads`() {
        val detector = StallDetector(maxStalledFrames = 2)
        detector.record(false) shouldBe false
        detector.record(false) shouldBe true
        detector.reset()
        detector.record(false) shouldBe false
        detector.record(false) shouldBe true
    }

    @Test
    fun `scroll speed follows the refresh rate and stays within bounds`() {
        AutomationSpeed.pixelsPerFrame(screenHeight = 2400, secondsPerScreen = 5, refreshRate = 60f) shouldBe
            (8f plusOrMinus 0.001f)
        AutomationSpeed.pixelsPerFrame(screenHeight = 2400, secondsPerScreen = 5, refreshRate = 120f) shouldBe
            (4f plusOrMinus 0.001f)
        AutomationSpeed.pixelsPerFrame(screenHeight = 2400, secondsPerScreen = 5, refreshRate = 0f) shouldBe
            (8f plusOrMinus 0.001f)
        AutomationSpeed.faster(1) shouldBe 1
        AutomationSpeed.slower(10) shouldBe 10
        AutomationSpeed.faster(5) shouldBe 4
        AutomationSpeed.slowerFlip(30) shouldBe 30
        AutomationSpeed.fasterFlip(1) shouldBe 1
    }

    @Test
    fun `fractional distances add up to whole pixels`() {
        val accumulator = PixelAccumulator()
        (1..10).sumOf { accumulator.take(0.4f) } shouldBe 4
    }
}
// KNS <--
