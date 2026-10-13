package tachiyomi.domain.category.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KMK -->
class GetCategoryUpdateIntervalTest {

    @Test
    fun `child inherits its parent's interval`() {
        val categories = listOf(category(1, interval = 14), category(2, parentId = 1))

        GetCategoryUpdateInterval.intervalDaysFor(categories, setOf(2)) shouldBe 14
    }

    @Test
    fun `child interval overrides its parent`() {
        val categories = listOf(category(1, interval = 14), category(2, parentId = 1, interval = 2))

        GetCategoryUpdateInterval.intervalDaysFor(categories, setOf(2)) shouldBe 2
    }

    @Test
    fun `shortest interval wins across memberships`() {
        val categories = listOf(category(1, interval = 30), category(2, interval = 1))

        GetCategoryUpdateInterval.intervalDaysFor(categories, setOf(1, 2)) shouldBe 1
    }

    private fun category(id: Long, parentId: Long = 0L, interval: Int? = null) = Category(
        id = id,
        name = "category$id",
        order = id,
        flags = 0,
        hidden = false,
        parentId = parentId,
        updateInterval = interval,
    )
}
// KMK <--
