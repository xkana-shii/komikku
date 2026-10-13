package tachiyomi.domain.category.interactor

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KMK -->
class SyncCategoriesWithTrackStatusTest {

    @Test
    fun `only categories explicitly bound to a status are changed`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(9, 1),
            boundCategories = listOf(category(1, 3), category(2, 1), category(9, null)),
            trackStatus = 1,
        )

        result shouldContainExactlyInAnyOrder listOf(9, 2)
    }

    @Test
    fun `no tracker status removes only status-bound memberships`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(1, 2, 9),
            boundCategories = listOf(category(1, 3), category(2, 1), category(9, null)),
            trackStatus = null,
        )

        result shouldContainExactlyInAnyOrder listOf(9)
    }

    private fun category(id: Long, status: Int?) = Category(
        id = id,
        name = "category$id",
        order = id,
        flags = 0,
        hidden = false,
        trackStatus = status,
    )
}
// KMK <--
