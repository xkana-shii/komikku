package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class BottomBarSwipeTest {

    private val threshold = 126f // 48 dp at 2.625 density

    private fun target(count: Int, current: Int, dx: Float, dy: Float = 0f) =
        BottomBarSwipe.targetIndex(count, current, dx, dy, threshold)

    @Test
    fun `a swipe to the left goes to the next tab and one to the right to the previous one`() {
        // Library, Updates, History, Browse, More: from Updates
        target(5, 1, dx = -200f) shouldBe 2
        target(5, 1, dx = 200f) shouldBe 0
    }

    @Test
    fun `a swipe moves exactly one tab whatever its length`() {
        target(5, 1, dx = -126f) shouldBe 2
        target(5, 1, dx = -2000f) shouldBe 2
        target(5, 3, dx = 2000f) shouldBe 2
    }

    @Test
    fun `there is no wrap-around at the first and last tab`() {
        target(5, 0, dx = 300f) shouldBe null
        target(5, 4, dx = -300f) shouldBe null
        target(5, 0, dx = -300f) shouldBe 1
        target(5, 4, dx = 300f) shouldBe 3
    }

    @Test
    fun `a drag below the threshold does nothing`() {
        target(5, 2, dx = -125f) shouldBe null
        target(5, 2, dx = 125.9f) shouldBe null
        target(5, 2, dx = 0f) shouldBe null
        target(5, 2, dx = -126f) shouldBe 3
    }

    @Test
    fun `mostly vertical drags are ignored`() {
        target(5, 2, dx = -200f, dy = 400f) shouldBe null
        target(5, 2, dx = 200f, dy = -200f) shouldBe null
        target(5, 2, dx = 200f, dy = 150f) shouldBe 1
    }

    @Test
    fun `the neighbour is taken from the enabled tabs, so disabled ones are skipped`() {
        // With History disabled the list is Library, Updates, Browse, More and Browse (index 2) neighbours Updates
        target(4, 1, dx = -200f) shouldBe 2
        target(4, 2, dx = 200f) shouldBe 1
    }

    @Test
    fun `an unknown current tab or an empty list is handled safely`() {
        target(5, -1, dx = -200f) shouldBe null
        target(5, 5, dx = 200f) shouldBe null
        target(0, 0, dx = -200f) shouldBe null
        target(1, 0, dx = -200f) shouldBe null
        target(1, 0, dx = 200f) shouldBe null
    }

    @Test
    fun `the threshold is 48 dp`() {
        BottomBarSwipe.THRESHOLD_DP shouldBe 48
    }
}
// KNS <--
