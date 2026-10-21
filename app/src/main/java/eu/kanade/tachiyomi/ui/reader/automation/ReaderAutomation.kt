package eu.kanade.tachiyomi.ui.reader.automation

import kotlin.math.max

// KNS -->
/** Why a running reader automation stopped. */
enum class AutomationStop {
    /** The configured maximum duration was reached. */
    MAX_MINUTES,

    /** The configured number of chapters was read. */
    MAX_CHAPTERS,

    /** The reader went to the background or the screen turned off. */
    INACTIVE,

    /** There is nothing left to scroll or flip to. */
    END,
}

/**
 * The limits of one automation run. [maxMinutes] and [maxChapters] of 0 mean no limit; chapters are
 * counted when the reader moves on to another chapter.
 */
class AutomationSession(
    private val maxMinutes: Int,
    private val maxChapters: Int,
    private val startedAtMillis: Long,
    startChapterId: Long?,
) {
    private var lastChapterId = startChapterId
    var chaptersRead = 0
        private set

    fun check(nowMillis: Long, chapterId: Long?, active: Boolean): AutomationStop? {
        if (!active) return AutomationStop.INACTIVE
        if (maxMinutes > 0 && nowMillis - startedAtMillis >= maxMinutes * 60_000L) return AutomationStop.MAX_MINUTES
        if (chapterId != null && chapterId != lastChapterId) {
            if (lastChapterId != null) chaptersRead++
            lastChapterId = chapterId
            if (maxChapters in 1..chaptersRead) return AutomationStop.MAX_CHAPTERS
        }
        return null
    }
}

/** Ends a scroll run when the viewer stopped moving for [maxStalledFrames] frames in a row. */
class StallDetector(private val maxStalledFrames: Int = 30) {
    private var stalled = 0

    /** Records one step; true once the viewer has not moved for long enough. */
    fun record(moved: Boolean): Boolean {
        stalled = if (moved) 0 else stalled + 1
        return stalled >= maxStalledFrames
    }
}

object AutomationSpeed {
    const val MIN_SECONDS_PER_SCREEN = 1
    const val MAX_SECONDS_PER_SCREEN = 10
    const val MIN_FLIP_SECONDS = 1
    const val MAX_FLIP_SECONDS = 30

    /**
     * Pixels to scroll each frame so one [screenHeight] passes in [secondsPerScreen], whatever the
     * display's [refreshRate] (it is re-read every frame, so variable refresh rates keep the speed).
     */
    fun pixelsPerFrame(screenHeight: Int, secondsPerScreen: Int, refreshRate: Float): Float {
        val rate = refreshRate.takeIf { it.isFinite() && it > 0f } ?: 60f
        return screenHeight / (max(secondsPerScreen, MIN_SECONDS_PER_SCREEN) * rate)
    }

    fun faster(secondsPerScreen: Int) = (secondsPerScreen - 1).coerceIn(MIN_SECONDS_PER_SCREEN, MAX_SECONDS_PER_SCREEN)

    fun slower(secondsPerScreen: Int) = (secondsPerScreen + 1).coerceIn(MIN_SECONDS_PER_SCREEN, MAX_SECONDS_PER_SCREEN)

    fun fasterFlip(seconds: Int) = (seconds - 1).coerceIn(MIN_FLIP_SECONDS, MAX_FLIP_SECONDS)

    fun slowerFlip(seconds: Int) = (seconds + 1).coerceIn(MIN_FLIP_SECONDS, MAX_FLIP_SECONDS)
}

/** Accumulates fractional scroll distances so slow speeds still move on integer-pixel views. */
class PixelAccumulator {
    private var carry = 0f

    fun take(pixels: Float): Int {
        val total = carry + pixels
        val whole = total.toInt()
        carry = total - whole
        return whole
    }
}
// KNS <--
