package tachiyomi.domain.category.interactor

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KMK -->
class SyncCategoriesWithTrackStatusTest {

    @Test
    fun `only tracker children beneath an active parent are changed`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10, 99),
            categories = categories(),
            trackStatus = 1,
        )

        result shouldContainExactly listOf(10, 99, 11)
    }

    @Test
    fun `a child membership activates its parent branch even when the parent row is absent`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(11),
            categories = categories(),
            trackStatus = 2,
        )

        result shouldContainExactly listOf(12)
    }

    @Test
    fun `multiple active parents receive their matching children without adding an unrelated branch`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10, 20),
            categories = categories(),
            trackStatus = 1,
        )

        result shouldContainExactly listOf(10, 20, 11, 21)
    }

    @Test
    fun `a parent alone receives its matching child and keeps unbound memberships`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10, 13, 99),
            categories = categories(),
            trackStatus = 2,
        )

        result shouldContainExactly listOf(10, 13, 99, 12)
    }

    @Test
    fun `no tracker status clears managed children but retains roots and manual children`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10, 11, 13, 99),
            categories = categories(),
            trackStatus = null,
        )

        result shouldContainExactly listOf(10, 13, 99)
    }

    @Test
    fun `legacy top-level bindings remain global without affecting unrelated child branches`() {
        val rootBound = category(90, "Legacy reading", 0, 1)
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10),
            categories = listOf(category(10, "Shounen", 0), category(21, "Reading", 20, 1), rootBound),
            trackStatus = 1,
        )

        result shouldContainExactly listOf(10, 90)
    }

    @Test
    fun `an inactive parent branch remains untouched and updates are idempotent`() {
        val initial = setOf(99)
        val first = SyncCategoriesWithTrackStatus.updatedMembership(initial, categories(), 1)
        val second = SyncCategoriesWithTrackStatus.updatedMembership(first, categories(), 1)

        first shouldContainExactly listOf(99)
        second shouldContainExactly first.toList()
    }

    private fun categories() = listOf(
        category(10, "Shounen", 0),
        category(11, "Reading", 10, 1),
        category(12, "Completed", 10, 2),
        category(13, "Manual", 10),
        category(20, "Josei", 0),
        category(21, "Reading", 20, 1),
        category(22, "Completed", 20, 2),
        category(30, "Seinen", 0),
        category(31, "Reading", 30, 1),
        category(32, "Completed", 30, 2),
        category(99, "Manual root", 0),
    )

    private fun category(id: Long, name: String, parentId: Long, status: Int? = null) = Category(
        id = id,
        name = name,
        order = id,
        flags = 0,
        hidden = false,
        parentId = parentId,
        trackStatus = status,
    )
}
// KMK <--
