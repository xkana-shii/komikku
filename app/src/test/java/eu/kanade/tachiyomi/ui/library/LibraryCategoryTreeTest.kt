package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KNS -->
class LibraryCategoryTreeTest {

    private val system = cat(0, "", order = -1)
    private val shounen = cat(1, "Shounen", order = 0)
    private val sReading = cat(2, "Reading", parent = 1, order = 1)
    private val sDeep = cat(3, "Weekly", parent = 2, order = 2)
    private val josei = cat(4, "Josei", order = 3)
    private val jReading = cat(5, "Reading", parent = 4, order = 4)
    private val categories = listOf(system, shounen, sReading, sDeep, josei, jReading)

    @Test
    fun `top-level tabs contain their whole subtree, each manga once`() {
        val groups = groupByCategoryTree(
            memberships = listOf(10L to listOf(1L, 2L), 11L to listOf(3L), 12L to listOf(5L)),
            categories = categories,
            showHiddenCategories = false,
        )
        groups.mapKeys { it.key.id } shouldBe mapOf(1L to listOf(10L, 11L), 4L to listOf(12L))
    }

    @Test
    fun `hidden subcategories do not feed their parent unless hidden categories are shown`() {
        val withHidden = categories.map { if (it.id == 2L) it.copy(hidden = true) else it }
        val memberships = listOf(10L to listOf(2L), 11L to listOf(3L), 12L to listOf(1L))
        groupByCategoryTree(memberships, withHidden, showHiddenCategories = false)
            .mapKeys { it.key.id } shouldBe mapOf(1L to listOf(12L), 4L to emptyList())
        groupByCategoryTree(memberships, withHidden, showHiddenCategories = true)
            .mapKeys { it.key.id } shouldBe mapOf(1L to listOf(10L, 11L, 12L), 4L to emptyList())
    }

    @Test
    fun `uncategorized manga add the system tab and orphans become tabs`() {
        val orphan = cat(9, "Lost", parent = 99, order = 9)
        val groups = groupByCategoryTree(
            memberships = listOf(10L to listOf(0L), 11L to listOf(9L)),
            categories = categories + orphan,
            showHiddenCategories = false,
        )
        groups.mapKeys { it.key.id } shouldBe mapOf(0L to listOf(10L), 1L to emptyList(), 4L to emptyList(), 9L to listOf(11L))
    }

    @Test
    fun `a flat category list groups exactly like before`() {
        val flat = listOf(system, shounen, josei)
        groupByCategoryTree(listOf(10L to listOf(1L), 11L to listOf(1L, 4L)), flat, showHiddenCategories = false)
            .mapKeys { it.key.id } shouldBe mapOf(1L to listOf(10L, 11L), 4L to listOf(11L))
    }

    private fun cat(id: Long, name: String, parent: Long = 0, order: Long) =
        Category(id = id, name = name, order = order, flags = 0, hidden = false, parentId = parent)
}
// KNS <--
