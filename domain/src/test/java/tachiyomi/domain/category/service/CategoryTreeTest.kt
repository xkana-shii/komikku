package tachiyomi.domain.category.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KNS -->
class CategoryTreeTest {

    private val shounen = cat(1, "Shounen", order = 0)
    private val sReading = cat(2, "Reading", parent = 1, order = 1)
    private val josei = cat(3, "Josei", order = 2)
    private val jReading = cat(4, "Reading", parent = 3, order = 3)
    private val deep = cat(5, "Weekly", parent = 2, order = 4)
    private val all = listOf(shounen, sReading, josei, jReading, deep)
    private val byId = all.associateBy(Category::id)

    @Test
    fun `sibling names are unique per parent only`() {
        CategoryTree.hasSiblingName(" reading ", parentId = 1, categories = all) shouldBe true
        CategoryTree.hasSiblingName("Reading", parentId = 0, categories = all) shouldBe false
        CategoryTree.hasSiblingName("Reading", parentId = 1, categories = all, excludingId = 2) shouldBe false
    }

    @Test
    fun `ancestry and descendants are transitive`() {
        CategoryTree.ancestorIds(5, byId) shouldBe listOf(2L, 1L)
        CategoryTree.descendantIds(1, all) shouldBe setOf(2L, 5L)
        CategoryTree.withAncestors(listOf(5L, 4L), byId) shouldBe setOf(5L, 2L, 1L, 4L, 3L)
        CategoryTree.path(deep, byId) shouldBe listOf("Shounen", "Reading", "Weekly")
        CategoryTree.depth(5, byId) shouldBe 2
    }

    @Test
    fun `moves that would create a cycle are rejected`() {
        CategoryTree.canMove(1, newParentId = 5, categories = all) shouldBe false
        CategoryTree.canMove(1, newParentId = 1, categories = all) shouldBe false
        CategoryTree.canMove(2, newParentId = 3, categories = all) shouldBe true
        CategoryTree.canMove(2, newParentId = 0, categories = all) shouldBe true
        CategoryTree.canMove(0, newParentId = 3, categories = all) shouldBe false
        CategoryTree.canMove(2, newParentId = 99, categories = all) shouldBe false
    }

    @Test
    fun `the persisted order is depth-first and a move carries the subtree`() {
        CategoryTree.flattened(all.shuffled()).map(Category::id) shouldBe listOf(1L, 2L, 5L, 3L, 4L)

        val moved = CategoryTree.moved(2, newParentId = 3, newIndex = 0, categories = all)!!
        moved.map(Category::id) shouldBe listOf(1L, 3L, 2L, 5L, 4L)
        moved.map(Category::order) shouldBe listOf(0L, 1L, 2L, 3L, 4L)
        moved.first { it.id == 2L }.parentId shouldBe 3L
        // The other "Reading" keeps its parent.
        moved.first { it.id == 4L }.parentId shouldBe 3L
    }

    @Test
    fun `orphans and cycles are kept in the order instead of dropped`() {
        val orphan = cat(9, "Lost", parent = 42, order = 9)
        val loopA = cat(10, "A", parent = 11, order = 10)
        val loopB = cat(11, "B", parent = 10, order = 11)
        val categories = all + orphan + loopA + loopB
        CategoryTree.flattened(categories).map(Category::id).toSet() shouldBe categories.map(Category::id).toSet()
        CategoryTree.hasCycles(categories) shouldBe true
        CategoryTree.hasCycles(all) shouldBe false
    }

    private fun cat(id: Long, name: String, parent: Long = 0, order: Long) =
        Category(id = id, name = name, order = order, flags = 0, hidden = false, parentId = parent)
}
// KNS <--
