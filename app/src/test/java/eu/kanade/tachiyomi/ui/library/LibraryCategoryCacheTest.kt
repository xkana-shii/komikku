package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KMK -->
class LibraryCategoryCacheTest {

    @Test
    fun `subcategory views preserve the pre-sorted parent order`() {
        val parent = category(1, 0)
        val child = category(11, 1)
        val cache = buildLibraryCategoryCache(
            groupedIds = linkedMapOf(parent to listOf(3, 1, 2)),
            directSubcategoryIds = mapOf(11L to listOf(2, 3)),
            categories = listOf(parent, child),
            itemsById = mapOf(1L to "one", 2L to "two", 3L to "three"),
        )

        cache.itemsByCategoryId[1] shouldContainExactly listOf("three", "one", "two")
        cache.itemsBySubcategoryId[11] shouldContainExactly listOf("three", "two")
        cache.itemsByCategoryId[1]?.size shouldBe 3
    }

    @Test
    fun `unrelated category selection reuses independently cached lists`() {
        val first = category(1, 0)
        val second = category(2, 0)
        val cache = buildLibraryCategoryCache(
            groupedIds = linkedMapOf(first to listOf(1), second to listOf(2)),
            directSubcategoryIds = emptyMap(),
            categories = listOf(first, second),
            itemsById = mapOf(1L to "one", 2L to "two"),
        )

        cache.itemsByCategoryId[1] shouldContainExactly listOf("one")
        cache.itemsByCategoryId[2] shouldContainExactly listOf("two")
    }

    private fun category(id: Long, parentId: Long) = Category(
        id = id,
        name = "category$id",
        order = id,
        flags = 0,
        hidden = false,
        parentId = parentId,
    )
}
// KMK <--
