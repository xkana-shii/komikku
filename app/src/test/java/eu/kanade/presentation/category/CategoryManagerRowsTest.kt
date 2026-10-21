package eu.kanade.presentation.category

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category

// KNS -->
class CategoryManagerRowsTest {

    private val shounen = cat(1, "Shounen", order = 0)
    private val sReading = cat(2, "Reading", parent = 1, order = 1)
    private val sWeekly = cat(3, "Weekly", parent = 2, order = 2)
    private val josei = cat(4, "Josei", order = 3)
    private val jReading = cat(5, "Reading", parent = 4, order = 4)
    private val all = listOf(shounen, sReading, sWeekly, josei, jReading)

    @Test
    fun `rows are depth-first with depth and subtree counts of distinct manga`() {
        val rows = buildCategoryManagerRows(
            categories = all,
            searchQuery = "",
            collapsedIds = emptySet(),
            sortMode = CategoryManagerSort.MANUAL,
            mangaIdsByCategory = mapOf(1L to setOf(10L), 2L to setOf(10L, 11L), 3L to setOf(12L), 5L to setOf(13L)),
        )
        rows.map { it.category.id to it.depth } shouldBe listOf(1L to 0, 2L to 1, 3L to 2, 4L to 0, 5L to 1)
        // Shounen: 10, 11, 12 (manga 10 counted once).
        rows.associate { it.category.id to it.mangaCount } shouldBe mapOf(1L to 3, 2L to 3, 3L to 1, 4L to 1, 5L to 1)
    }

    @Test
    fun `collapsing hides the subtree`() {
        val rows = buildCategoryManagerRows(all, "", setOf(1L), CategoryManagerSort.MANUAL, emptyMap())
        rows.map { it.category.id } shouldBe listOf(1L, 4L, 5L)
        rows.first().collapsed shouldBe true
    }

    @Test
    fun `a category whose parent is missing is shown as a root, never dropped`() {
        val orphan = cat(9, "Lost", parent = 99, order = 9)
        val rows = buildCategoryManagerRows(all + orphan, "", setOf(1L), CategoryManagerSort.MANUAL, emptyMap())
        rows.map { it.category.id } shouldBe listOf(1L, 4L, 5L, 9L)
    }

    @Test
    fun `search shows matches with their ancestors and paths, ignoring collapse`() {
        val rows = buildCategoryManagerRows(all, "read", setOf(1L, 4L), CategoryManagerSort.MANUAL, emptyMap())
        rows.map { it.category.id } shouldBe listOf(1L, 2L, 4L, 5L)
        rows.first { it.category.id == 2L }.path shouldBe "Shounen / Reading"
        rows.first { it.category.id == 5L }.path shouldBe "Josei / Reading"
        rows.first { it.category.id == 1L }.path shouldBe null
    }

    @Test
    fun `sorting applies per level`() {
        val rows = buildCategoryManagerRows(all, "", emptySet(), CategoryManagerSort.ZA, emptyMap())
        rows.map { it.category.id } shouldBe listOf(1L, 2L, 3L, 4L, 5L)
        buildCategoryManagerRows(all, "", emptySet(), CategoryManagerSort.AZ, emptyMap())
            .map { it.category.id } shouldBe listOf(4L, 5L, 1L, 2L, 3L)
    }

    @Test
    fun `a drop resolves to the position among siblings`() {
        val rows = buildCategoryManagerRows(all, "", emptySet(), CategoryManagerSort.MANUAL, emptyMap())
        // Josei dragged above Shounen's expanded subtree.
        val reordered = listOf(rows[3], rows[0], rows[1], rows[2], rows[4])
        siblingIndexAfterDrop(reordered, 4L) shouldBe 0
        // A child dragged past another branch stays below its own parent, at the end.
        val childMoved = listOf(rows[0], rows[2], rows[3], rows[4], rows[1])
        siblingIndexAfterDrop(childMoved, 2L) shouldBe 0
    }

    @Test
    fun `the assignment dialog shows the tree and stores a child with its ancestors`() {
        val selection = listOf(
            CheckboxState.State.None(jReading),
            CheckboxState.State.None(cat(0, "", order = -1)),
            CheckboxState.State.Checked(sWeekly),
            CheckboxState.State.None(shounen),
            CheckboxState.State.None(josei),
            CheckboxState.State.None(sReading),
        )
        orderedCategorySelection(selection).map { it.checkbox.value.id to it.depth } shouldBe
            listOf(0L to 0, 1L to 0, 2L to 1, 3L to 2, 4L to 0, 5L to 1)

        val (included, excluded) = categorySelectionChange(selection)
        included shouldBe listOf(3L, 2L, 1L)
        excluded.toSet() shouldBe setOf(0L, 4L, 5L)
    }

    private fun cat(id: Long, name: String, parent: Long = 0, order: Long) =
        Category(id = id, name = name, order = order, flags = 0, hidden = false, parentId = parent)
}
// KNS <--
