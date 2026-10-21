package tachiyomi.domain.category.interactor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.SyncCategoriesWithTrackStatus.Companion.updatedMembership
import tachiyomi.domain.category.model.Category

// KNS -->
/** features.md § 1.4 and acceptance scenarios 1–4 (plus deeper nesting). */
class SyncCategoriesWithTrackStatusTest {

    private val shounen = cat(1, "Shounen")
    private val shounenReading = cat(2, "Reading", parent = 1, status = READING)
    private val shounenCompleted = cat(3, "Completed", parent = 1, status = COMPLETED)
    private val josei = cat(4, "Josei")
    private val joseiReading = cat(5, "Reading", parent = 4, status = READING)
    private val joseiCompleted = cat(6, "Completed", parent = 4, status = COMPLETED)
    private val favourites = cat(7, "Favourites")
    private val tree = listOf(shounen, shounenReading, shounenCompleted, josei, joseiReading, joseiCompleted, favourites)

    @Test
    fun `scenario 1 - a status change does not add an unrelated branch`() {
        updatedMembership(setOf(1, 7), tree, READING) shouldBe setOf(1L, 2L, 7L)
    }

    @Test
    fun `scenario 2 - a child-only membership reaches its ancestor branch`() {
        // Filed only in Shounen / Reading, Shounen itself never saved.
        updatedMembership(setOf(2), tree, COMPLETED) shouldBe setOf(3L)
    }

    @Test
    fun `scenario 2 - the branch is kept when the last status category is removed`() {
        // No Shounen category for "plan to read": Reading is removed and Shounen keeps the manga.
        updatedMembership(setOf(2), tree, PLAN_TO_READ) shouldBe setOf(1L)
        // ...and the branch is still in scope afterwards.
        updatedMembership(setOf(1), tree, READING) shouldBe setOf(1L, 2L)
    }

    @Test
    fun `scenario 3 - every branch the manga belongs to updates independently`() {
        updatedMembership(setOf(2, 5), tree, COMPLETED) shouldBe setOf(3L, 6L)
        updatedMembership(setOf(1, 4), tree, READING) shouldBe setOf(1L, 4L, 2L, 5L)
    }

    @Test
    fun `an unrelated branch and ordinary categories are left exactly as they were`() {
        // Shounen changes; Josei's status categories stay out and Favourites stays in.
        updatedMembership(setOf(2, 7), tree, COMPLETED) shouldBe setOf(3L, 7L)
    }

    @Test
    fun `scenario 4 - a top-level status category is global`() {
        val global = cat(8, "Reading (all)", status = READING)
        val categories = tree + global
        updatedMembership(setOf(7), categories, READING) shouldBe setOf(7L, 8L)
        updatedMembership(setOf(7, 8), categories, COMPLETED) shouldBe setOf(7L)
    }

    @Test
    fun `deeper nesting uses transitive ancestry`() {
        val ongoing = cat(10, "Ongoing", parent = 1)
        val deepReading = cat(11, "Reading", parent = 10, status = READING)
        val categories = listOf(shounen, ongoing, deepReading, shounenCompleted, josei, joseiCompleted)

        // Filed three levels down: Shounen / Ongoing / Reading. Completed is reached via Shounen.
        val completed = updatedMembership(setOf(11), categories, COMPLETED)
        completed shouldBe setOf(3L, 10L)
        // Back to reading: Ongoing (kept as the anchor) makes the deep category reachable again.
        updatedMembership(completed, categories, READING) shouldBe setOf(10L, 11L)
    }

    @Test
    fun `only bound categories ever change`() {
        val current = setOf(2L, 7L)
        updatedMembership(current, listOf(shounen, josei, favourites), COMPLETED) shouldBe current
    }

    @Test
    fun `a null status removes in-scope bound categories only`() {
        updatedMembership(setOf(2, 7), tree, null) shouldBe setOf(1L, 7L)
    }

    @Test
    fun `applying the same status twice is stable`() {
        val first = updatedMembership(setOf(2), tree, COMPLETED)
        updatedMembership(first, tree, COMPLETED) shouldBe first
    }

    @Test
    fun `same-named children are matched by identity, not name`() {
        // Only Josei / Reading is a membership; Shounen / Reading (same name, same status) stays out.
        updatedMembership(setOf(5), tree, READING) shouldBe setOf(5L)
    }

    private fun cat(id: Long, name: String, parent: Long = 0, status: Int? = null) = Category(
        id = id,
        name = name,
        order = id,
        flags = 0,
        hidden = false,
        parentId = parent,
        trackStatus = status,
    )

    private companion object {
        const val READING = 1
        const val COMPLETED = 5
        const val PLAN_TO_READ = 3
    }
}
// KNS <--
