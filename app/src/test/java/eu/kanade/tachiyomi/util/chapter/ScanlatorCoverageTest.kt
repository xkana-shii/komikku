package eu.kanade.tachiyomi.util.chapter

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KMK -->
class ScanlatorCoverageTest {

    @Test
    fun `returns no coverage when all chapter numbers are invalid`() {
        scanlatorCoverage(listOf(0.0, -1.0, Double.NaN)).shouldBeNull()
    }

    @Test
    fun `groups consecutive chapters and uses rounded decimal labels`() {
        scanlatorCoverage(listOf(0.65, 0.6999999999999999, 1.5, 2.5, 4.0)) shouldBe ScanlatorCoverage(
            groups = listOf("0.7", "1.5–2.5", "4"),
            hiddenCount = 0,
        )
    }

    @Test
    fun `limits visible coverage groups and counts the remainder`() {
        val oddChapters = (1..23 step 2).map(Int::toDouble)

        scanlatorCoverage(oddChapters, maxGroups = 3) shouldBe ScanlatorCoverage(
            groups = listOf("1", "3", "5"),
            hiddenCount = 9,
        )
    }
}
// KMK <--
