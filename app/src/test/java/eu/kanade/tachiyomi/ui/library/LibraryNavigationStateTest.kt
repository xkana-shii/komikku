package eu.kanade.tachiyomi.ui.library

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KMK -->
class LibraryNavigationStateTest {
    private val parent = Category(1, "Parent", 0, 0, false)
    private val other = Category(2, "Other", 1, 0, false)
    private val direct = mockk<LibraryItem>()
    private val childItem = mockk<LibraryItem>()
    private val prepared = listOf(direct, childItem)

    private fun state() = LibraryViewModel.State(
        displayedCategories = listOf(parent, other),
        activeCategoryId = parent.id,
        itemsByCategoryId = mapOf(parent.id to prepared, other.id to emptyList()),
        itemsBySubcategoryId = mapOf(11L to listOf(childItem), 12L to emptyList()),
        showMangaCount = true,
    )

    @Test
    fun `All keeps direct parent items and selecting a child uses prepared membership`() {
        val all = state()
        (all.getItemsForCategory(parent) === prepared) shouldBe true
        val child = all.copy(activeSubCategoryId = 11L)
        child.getItemsForCategory(parent) shouldBe listOf(childItem)
        child.getItemCountForCategory(parent) shouldBe 1
        child.getItemsForCategory(other) shouldBe emptyList()
        (all.getItemsForCategory(parent) === prepared) shouldBe true
    }

    @Test
    fun `empty child does not fall back to All and navigation clears filtering`() {
        val child = state().copy(activeSubCategoryId = 12L)
        child.getItemsForCategory(parent) shouldBe emptyList()
        child.getItemCountForCategory(parent) shouldBe 0
        val next = child.copy(activeCategoryId = other.id, activeCategoryIndex = 1, activeSubCategoryId = null)
        next.activeCategory shouldBe other
        (next.getItemsForCategory(parent) === prepared) shouldBe true
        next.getItemsForCategory(other) shouldBe emptyList()
    }

    @Test
    fun `category ordering and removal bound the saved selection`() {
        val reordered = state().copy(displayedCategories = listOf(other, parent))
        reordered.coercedActiveCategoryIndex shouldBe 1
        reordered.activeCategory shouldBe parent
        val removed = reordered.copy(displayedCategories = listOf(other), activeCategoryIndex = 20)
        removed.coercedActiveCategoryIndex shouldBe 0
        removed.activeCategory shouldBe other
        removed.copy(displayedCategories = emptyList()).activeCategory shouldBe null
    }
}
// KMK <--
