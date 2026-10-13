package eu.kanade.presentation.category

import eu.kanade.tachiyomi.ui.category.CategoryManagerSort
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KMK -->
class CategoryManagerRowsTest {

    @Test
    fun `child search result retains its parent context`() {
        val rows = buildCategoryManagerRows(
            categories = categories(),
            searchQuery = "romance",
            collapsedIds = setOf(1),
            sortMode = CategoryManagerSort.MANUAL,
            mangaCounts = emptyMap(),
        )

        rows.map { it.category.id } shouldContainExactly listOf(1L, 12L)
    }

    @Test
    fun `collapsed parents hide children outside search`() {
        val rows = buildCategoryManagerRows(
            categories = categories(),
            searchQuery = "",
            collapsedIds = setOf(1),
            sortMode = CategoryManagerSort.MANUAL,
            mangaCounts = emptyMap(),
        )

        rows.map { it.category.id } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `count sorting applies independently inside each hierarchy level`() {
        val rows = buildCategoryManagerRows(
            categories = categories(),
            searchQuery = "",
            collapsedIds = emptySet(),
            sortMode = CategoryManagerSort.MOST_ITEMS,
            mangaCounts = mapOf(1L to 2, 2L to 8, 11L to 1, 12L to 4),
        )

        rows.map { it.category.id } shouldContainExactly listOf(2L, 1L, 12L, 11L)
    }

    private fun categories() = listOf(
        category(1, "Parent", 0, 0),
        category(11, "Action", 1, 1),
        category(12, "Romance", 1, 2),
        category(2, "Other", 0, 3),
    )

    private fun category(id: Long, name: String, parentId: Long, order: Long) = Category(
        id = id,
        name = name,
        order = order,
        flags = 0,
        hidden = false,
        parentId = parentId,
    )
}
// KMK <--
