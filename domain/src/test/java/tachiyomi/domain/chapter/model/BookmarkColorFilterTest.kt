package tachiyomi.domain.chapter.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class BookmarkColorFilterTest {

    @Test
    fun `an empty filter passes everything`() {
        BookmarkColor.matchesFilter(emptyList(), emptySet()) shouldBe true
        BookmarkColor.matchesFilter(listOf(3), emptySet()) shouldBe true
    }

    @Test
    fun `a filter needs a bookmark of one of its colors`() {
        val redOrOrdinary = setOf(BookmarkColor.RED, BookmarkColor.NONE)
        BookmarkColor.matchesFilter(listOf(1), redOrOrdinary) shouldBe true
        BookmarkColor.matchesFilter(listOf(0, 4), redOrOrdinary) shouldBe true
        BookmarkColor.matchesFilter(listOf(4, 5), redOrOrdinary) shouldBe false
        BookmarkColor.matchesFilter(emptyList(), redOrOrdinary) shouldBe false
    }

    @Test
    fun `included and excluded colors can be combined predictably`() {
        val included = setOf(BookmarkColor.RED, BookmarkColor.BLUE)
        val excluded = setOf(BookmarkColor.ORANGE)

        BookmarkColor.matchesFilter(listOf(BookmarkColor.RED.value), included, excluded) shouldBe true
        BookmarkColor.matchesFilter(listOf(BookmarkColor.BLUE.value), included, excluded) shouldBe true
        BookmarkColor.matchesFilter(listOf(BookmarkColor.ORANGE.value), included, excluded) shouldBe false
        BookmarkColor.matchesFilter(listOf(BookmarkColor.RED.value, BookmarkColor.ORANGE.value), included, excluded) shouldBe false
        BookmarkColor.matchesFilter(listOf(BookmarkColor.NONE.value), emptySet(), excluded) shouldBe true
    }

    @Test
    fun `every palette color is independently representable`() {
        BookmarkColor.entries.map { it.value } shouldBe (0..6).toList()
        BookmarkColor.entries.forEach { color ->
            BookmarkColor.matchesFilter(listOf(color.value), setOf(color)) shouldBe true
            BookmarkColor.matchesFilter(listOf(color.value), emptySet(), setOf(color)) shouldBe false
        }
    }

    @Test
    fun `stored filter names ignore unknown entries`() {
        BookmarkColor.fromNames(listOf("RED", "TEAL", "PURPLE")) shouldBe setOf(BookmarkColor.RED, BookmarkColor.PURPLE)
    }
}
// KNS <--
