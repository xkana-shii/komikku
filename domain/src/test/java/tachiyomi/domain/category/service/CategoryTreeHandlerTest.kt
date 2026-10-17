package tachiyomi.domain.category.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KMK -->
class CategoryTreeHandlerTest {

    @Test
    fun `subcategory reparenting preserves the two-level hierarchy and sibling order`() {
        val categories = listOf(
            category(1, 0, 0),
            category(11, 1, 1),
            category(12, 1, 2),
            category(2, 0, 3),
            category(21, 2, 4),
        )

        CategoryTreeHandler.reorderedIds(12, 2, 0, categories) shouldBe listOf(1L, 11L, 2L, 12L, 21L)
    }

    @Test
    fun `moving a root also moves its visible child block`() {
        val categories = listOf(
            category(1, 0, 0),
            category(11, 1, 1),
            category(2, 0, 2),
            category(21, 2, 3),
        )

        CategoryTreeHandler.reorderedIds(2, 0, 0, categories) shouldBe listOf(2L, 21L, 1L, 11L)
    }

    @Test
    fun `moves that create cycles or a third category level are rejected`() {
        val categories = listOf(
            category(1, 0, 0),
            category(11, 1, 1),
            category(2, 0, 2),
        )

        CategoryTreeHandler.canMove(1, 11, categories) shouldBe false
        CategoryTreeHandler.canMove(2, 11, categories) shouldBe false
        CategoryTreeHandler.canMove(11, 2, categories) shouldBe true
    }

    @Test
    fun `category names are unique among siblings rather than across the tree`() {
        val categories = listOf(
            category(1, 0, 0, "Yaoi"),
            category(11, 1, 1, "Reading"),
            category(2, 0, 2, "Normal"),
            category(21, 2, 3, "Reading"),
        )

        CategoryTreeHandler.hasSiblingName(" reading ", 1, categories) shouldBe true
        CategoryTreeHandler.hasSiblingName("Reading", 2, categories) shouldBe true
        CategoryTreeHandler.hasSiblingName("Reading", 0, categories) shouldBe false
        CategoryTreeHandler.hasSiblingName("Reading", 1, categories, excludingId = 11) shouldBe false
    }

    private fun category(id: Long, parentId: Long, order: Long, name: String = "category$id") = Category(
        id = id,
        name = name,
        order = order,
        flags = 0,
        hidden = false,
        parentId = parentId,
    )
}
// KMK <--
