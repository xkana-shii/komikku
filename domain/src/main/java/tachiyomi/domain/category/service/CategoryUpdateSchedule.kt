package tachiyomi.domain.category.service

import tachiyomi.domain.category.model.Category
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// KNS -->
/**
 * Category update intervals for the scheduled library update. A category without an interval of its
 * own inherits the one of its nearest ancestor that has one; a category without either follows the
 * global schedule (every scheduled run). A category with an interval is due when that many days have
 * passed since the last scheduled run that updated it.
 */
object CategoryUpdateSchedule {

    /** The interval in days that applies to [categoryId]; null = the global schedule. */
    fun effectiveInterval(categoryId: Long, byId: Map<Long, Category>): Int? {
        val category = byId[categoryId] ?: return null
        category.updateInterval?.takeIf { it > 0 }?.let { return it }
        return CategoryTree.ancestorIds(categoryId, byId)
            .firstNotNullOfOrNull { id -> byId[id]?.updateInterval?.takeIf { it > 0 } }
    }

    /**
     * The categories with an interval that are due at [now], given when each was last updated by a
     * scheduled run ([lastRun]; a category never run is due).
     */
    fun dueIntervalCategories(
        categories: Iterable<Category>,
        lastRun: Map<Long, Instant>,
        now: Instant,
    ): Set<Long> {
        val byId = categories.associateBy(Category::id)
        return byId.keys.filterTo(mutableSetOf()) { id ->
            val interval = effectiveInterval(id, byId) ?: return@filterTo false
            val last = lastRun[id] ?: return@filterTo true
            now - last >= interval.days
        }
    }

    /**
     * Whether a scheduled run updates a manga filed in [memberships]: when it has no category, when one
     * of its categories follows the global schedule, or when one of its interval categories is [due].
     * A manga in several categories is therefore never updated less often than any of them asks.
     */
    fun isMangaDue(memberships: Collection<Long>, byId: Map<Long, Category>, due: Set<Long>): Boolean {
        val categories = memberships.filter { it != Category.UNCATEGORIZED_ID && it in byId }
        if (categories.isEmpty()) return true
        return categories.any { effectiveInterval(it, byId) == null || it in due }
    }

    /** The categories a refresh of [categoryId] covers: the category and all its subcategories. */
    fun refreshScope(categoryId: Long, categories: Iterable<Category>): Set<Long> =
        if (categoryId == Category.UNCATEGORIZED_ID) {
            setOf(categoryId)
        } else {
            CategoryTree.descendantIds(categoryId, categories) + categoryId
        }

    /** Encodes last-run times as `id:epochMillis` entries for a string-set preference. */
    fun encodeLastRun(lastRun: Map<Long, Instant>): Set<String> =
        lastRun.mapTo(mutableSetOf()) { (id, time) -> "$id:${time.toEpochMilliseconds()}" }

    fun decodeLastRun(raw: Set<String>): Map<Long, Instant> = raw.mapNotNull { entry ->
        val id = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val millis = entry.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
        id to Instant.fromEpochMilliseconds(millis)
    }.toMap()
}
// KNS <--
