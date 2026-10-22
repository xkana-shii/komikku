package eu.kanade.tachiyomi.data.track

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class ReadingDatesTest {
    private val jan10 = 1_704_844_800_000L
    private val jan20 = 1_705_708_800_000L
    private val oct21 = 1_792_540_800_000L
    private val oct25 = 1_792_886_400_000L

    @Test
    fun `finishing a reread keeps both dates of the first reading`() {
        ReadingDates.started(jan10, oct21) shouldBe jan10
        ReadingDates.finished(jan20, oct25) shouldBe jan20
    }

    @Test
    fun `a completed entry without dates gets them when rereading starts and finishes`() {
        ReadingDates.started(0L, oct21) shouldBe oct21
        ReadingDates.finished(0L, oct25) shouldBe oct25
    }

    @Test
    fun `only the missing date is filled`() {
        ReadingDates.started(jan10, oct21) shouldBe jan10
        ReadingDates.finished(0L, oct25) shouldBe oct25
        ReadingDates.started(0L, oct21) shouldBe oct21
        ReadingDates.finished(jan20, oct25) shouldBe jan20
    }

    @Test
    fun `repeated rereads never move the dates`() {
        var start = jan10
        var finish = jan20
        repeat(3) { round ->
            start = ReadingDates.started(start, oct21 + round)
            finish = ReadingDates.finished(finish, oct25 + round)
        }
        start shouldBe jan10
        finish shouldBe jan20
    }
}
// KNS <--
