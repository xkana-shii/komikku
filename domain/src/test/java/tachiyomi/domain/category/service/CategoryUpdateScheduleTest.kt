package tachiyomi.domain.category.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// KNS -->
class CategoryUpdateScheduleTest {

    private val categories = listOf(
        cat(1, "Shounen", interval = 7),
        cat(2, "Reading", parent = 1),
        cat(3, "Weekly", parent = 2, interval = 2),
        cat(4, "Josei"),
        cat(5, "Reading", parent = 4),
    )
    private val byId = categories.associateBy(Category::id)
    private val now = Instant.fromEpochMilliseconds(100.days.inWholeMilliseconds)

    @Test
    fun `a subcategory inherits the nearest ancestor's interval`() {
        CategoryUpdateSchedule.effectiveInterval(1, byId) shouldBe 7
        CategoryUpdateSchedule.effectiveInterval(2, byId) shouldBe 7
        CategoryUpdateSchedule.effectiveInterval(3, byId) shouldBe 2
        CategoryUpdateSchedule.effectiveInterval(5, byId) shouldBe null
    }

    @Test
    fun `interval categories are due once their interval passed since their last run`() {
        val lastRun = mapOf(1L to now - 6.days, 2L to now - 7.days, 3L to now - 1.days)
        CategoryUpdateSchedule.dueIntervalCategories(categories, lastRun, now) shouldBe setOf(2L)
        CategoryUpdateSchedule.dueIntervalCategories(categories, emptyMap(), now) shouldBe setOf(1L, 2L, 3L)
    }

    @Test
    fun `a manga is due through any of its categories, and always with a global one`() {
        CategoryUpdateSchedule.isMangaDue(listOf(3), byId, due = emptySet()) shouldBe false
        CategoryUpdateSchedule.isMangaDue(listOf(3, 2), byId, due = setOf(2)) shouldBe true
        CategoryUpdateSchedule.isMangaDue(listOf(3, 5), byId, due = emptySet()) shouldBe true
        CategoryUpdateSchedule.isMangaDue(listOf(0), byId, due = emptySet()) shouldBe true
        CategoryUpdateSchedule.isMangaDue(emptyList(), byId, due = emptySet()) shouldBe true
    }

    @Test
    fun `last run times survive the preference encoding and ignore malformed entries`() {
        val lastRun = mapOf(1L to now, 3L to now - 2.days)
        CategoryUpdateSchedule.decodeLastRun(CategoryUpdateSchedule.encodeLastRun(lastRun)) shouldBe lastRun
        CategoryUpdateSchedule.decodeLastRun(setOf("x:1", "2:", "4:abc")) shouldBe emptyMap()
    }

    @Test
    fun `a category refresh covers its subtree, never the whole library for the default category`() {
        CategoryUpdateSchedule.refreshScope(1, categories) shouldBe setOf(1L, 2L, 3L)
        CategoryUpdateSchedule.refreshScope(2, categories) shouldBe setOf(2L, 3L)
        CategoryUpdateSchedule.refreshScope(0, categories + cat(0, "")) shouldBe setOf(0L)
    }

    private fun cat(id: Long, name: String, parent: Long = 0, interval: Int? = null) = Category(
        id = id,
        name = name,
        order = id,
        flags = 0,
        hidden = false,
        parentId = parent,
        updateInterval = interval,
    )
}
// KNS <--
