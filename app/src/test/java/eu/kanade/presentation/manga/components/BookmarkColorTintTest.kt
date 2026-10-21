package eu.kanade.presentation.manga.components

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.BookmarkColor

// KNS -->
/**
 * The chapter list and the Updates screen tint a bookmark with `bookmarkColorOf(id) ?: primary`, so
 * an ordinary bookmark (0) follows the theme and 1–6 use the fixed palette.
 */
class BookmarkColorTintTest {

    private val themePrimary = Color(0xFF123456)

    private fun tint(id: Int) = bookmarkColorOf(id) ?: themePrimary

    @Test
    fun `an ordinary bookmark uses the theme color`() {
        bookmarkColorOf(BookmarkColor.DEFAULT_ID) shouldBe null
        tint(0) shouldBe themePrimary
    }

    @Test
    fun `colors 1 to 6 map to the fixed red, orange, yellow, green, blue and purple`() {
        (1..6).map(::tint) shouldBe listOf(
            Color(0xFFD32F2F),
            Color(0xFFE64A19),
            Color(0xFFF9A825),
            Color(0xFF2E7D32),
            Color(0xFF1565C0),
            Color(0xFF6A1B9A),
        )
    }
}
// KNS <--
