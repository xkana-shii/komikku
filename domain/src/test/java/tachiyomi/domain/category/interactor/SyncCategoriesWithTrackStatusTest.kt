
package tachiyomi.domain.category.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

// KMK -->
class SyncCategoriesWithTrackStatusTest {

    @Test
    fun `only tracker children beneath an active parent are changed`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L, 99L),
            categories = categories(),
            trackStatus = 1,
        )

        result shouldBe setOf(10L, 99L, 11L)
    }

    @Test
    fun `a child membership activates its parent branch even when the parent row is absent`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(11L),
            categories = categories(),
            trackStatus = 2,
        )

        // Preserve the Shounen parent and replace Reading
        // with Completed.
        result shouldBe setOf(10L, 12L)
    }

    @Test
    fun `multiple active parents receive their matching children without adding an unrelated branch`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L, 20L),
            categories = categories(),
            trackStatus = 1,
        )

        result shouldBe setOf(10L, 20L, 11L, 21L)
    }

    @Test
    fun `a parent alone receives its matching child and keeps unbound memberships`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L, 13L, 99L),
            categories = categories(),
            trackStatus = 2,
        )

        result shouldBe setOf(10L, 13L, 99L, 12L)
    }

    @Test
    fun `no tracker status clears managed children but retains roots and manual children`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L, 11L, 13L, 99L),
            categories = categories(),
            trackStatus = null,
        )

        result shouldBe setOf(10L, 13L, 99L)
    }

    @Test
    fun `legacy top-level bindings remain global without affecting unrelated child branches`() {
        val rootBound = category(
            id = 90L,
            name = "Legacy reading",
            parentId = 0L,
            status = 1,
        )

        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L),
            categories = listOf(
                category(10L, "Shounen", 0L),
                category(21L, "Reading", 20L, 1),
                rootBound,
            ),
            trackStatus = 1,
        )

        result shouldBe setOf(10L, 90L)
    }

    @Test
    fun `an inactive parent branch remains untouched and updates are idempotent`() {
        val initial = setOf(99L)

        val first = SyncCategoriesWithTrackStatus.updatedMembership(
            current = initial,
            categories = categories(),
            trackStatus = 1,
        )

        val second = SyncCategoriesWithTrackStatus.updatedMembership(
            current = first,
            categories = categories(),
            trackStatus = 1,
        )

        first shouldBe setOf(99L)
        second shouldBe first
    }

    @Test
    fun `changing from reading to completed only updates the current parent`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L, 11L),
            categories = categories(),
            trackStatus = 2,
        )

        result shouldBe setOf(10L, 12L)
    }

    @Test
    fun `changing tracker status updates both active parent branches`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L, 11L, 20L, 21L),
            categories = categories(),
            trackStatus = 2,
        )

        result shouldBe setOf(10L, 12L, 20L, 22L)
    }

    @Test
    fun `a manga with no category membership is not added to unrelated parent branches`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = emptySet(),
            categories = categories(),
            trackStatus = 1,
        )

        result shouldBe emptySet<Long>()
    }

    @Test
    fun `a manga assigned only to a completed child retains its parent when changing to reading`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(12L),
            categories = categories(),
            trackStatus = 1,
        )

        result shouldBe setOf(10L, 11L)
    }

    @Test
    fun `manga moved from shounen to josei only receives josei status categories`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(20L),
            categories = categories(),
            trackStatus = 1,
        )

        result shouldBe setOf(20L, 21L)
    }

    @Test
    fun `subcategories with matching names are distinguished by ID and parent`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(20L),
            categories = categories(),
            trackStatus = 1,
        )

        // Reading exists under Shounen, Josei and Seinen.
        // Only the Josei child should be added.
        result shouldBe setOf(20L, 21L)
    }

    @Test
    fun `manual subcategories are preserved when tracker status changes`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L, 11L, 13L),
            categories = categories(),
            trackStatus = 2,
        )

        result shouldBe setOf(10L, 12L, 13L)
    }

    @Test
    fun `a parent without a matching status child is preserved`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L),
            categories = listOf(
                category(10L, "Shounen", 0L),
                category(11L, "Reading", 10L, 1),
                category(20L, "Josei", 0L),
                category(22L, "Completed", 20L, 2),
            ),
            trackStatus = 2,
        )

        result shouldBe setOf(10L)
    }

    @Test
    fun `no bound categories leaves memberships unchanged`() {
        val initial = setOf(10L, 13L, 99L)

        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = initial,
            categories = categories().map {
                it.copy(trackStatus = null)
            },
            trackStatus = 1,
        )

        result shouldBe initial
    }

    @Test
    fun `repeated status synchronization does not change membership`() {
        val initial = setOf(10L)

        val first = SyncCategoriesWithTrackStatus.updatedMembership(
            current = initial,
            categories = categories(),
            trackStatus = 1,
        )

        val second = SyncCategoriesWithTrackStatus.updatedMembership(
            current = first,
            categories = categories(),
            trackStatus = 1,
        )

        first shouldBe setOf(10L, 11L)
        second shouldBe first
    }

    @Test
    fun `null status clears both active branches without removing parents`() {
        val result = SyncCategoriesWithTrackStatus.updatedMembership(
            current = setOf(10L, 11L, 20L, 21L, 99L),
            categories = categories(),
            trackStatus = null,
        )

        result shouldBe setOf(10L, 20L, 99L)
    }

    private fun categories() = listOf(
        category(10L, "Shounen", 0L),
        category(11L, "Reading", 10L, 1),
        category(12L, "Completed", 10L, 2),
        category(13L, "Manual", 10L),
        category(20L, "Josei", 0L),
        category(21L, "Reading", 20L, 1),
        category(22L, "Completed", 20L, 2),
        category(30L, "Seinen", 0L),
        category(31L, "Reading", 30L, 1),
        category(32L, "Completed", 30L, 2),
        category(99L, "Manual root", 0L),
    )

    private fun category(
        id: Long,
        name: String,
        parentId: Long,
        status: Int? = null,
    ) = Category(
        id = id,
        name = name,
        order = id,
        flags = 0L,
        hidden = false,
        parentId = parentId,
        trackStatus = status,
    )
}
// KMK <--
