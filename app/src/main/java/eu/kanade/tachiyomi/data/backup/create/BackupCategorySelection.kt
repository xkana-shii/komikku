package eu.kanade.tachiyomi.data.backup.create

import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree

// KNS -->
/**
 * Which categories a manual backup contains (adapted from the previous personal fork). A selection
 * is null for "every category" (a full backup, also every automatic backup) or the set of selected
 * category ids, where [Category.UNCATEGORIZED_ID] stands for Default; an empty set selects nothing.
 *
 * Selecting or clearing a category does the same to its whole subtree, so a selected parent covers
 * its subcategories unless some are cleared again, and a selected child covers its own children
 * but never its siblings. Membership is exact: a manga is backed up when one of its categories is
 * selected, and only its selected categories are written for it.
 */
object BackupCategorySelection {

    /** One row of the selector: Default first, then the user categories as a tree. */
    data class Row(val category: Category, val depth: Int)

    fun rows(categories: List<Category>): List<Row> {
        val user = categories.filterNot(Category::isSystemCategory)
        val byId = user.associateBy(Category::id)
        val default = categories.firstOrNull(Category::isSystemCategory)
            ?: Category(Category.UNCATEGORIZED_ID, "", 0, 0, false)
        return listOf(Row(default, 0)) +
            CategoryTree.flattened(user).map { Row(it, CategoryTree.depth(it.id, byId)) }
    }

    fun isSelected(selection: Set<Long>?, categoryId: Long): Boolean = selection?.contains(categoryId) ?: true

    /** [selection] with [categoryId] and its subtree selected or cleared; null again once all are. */
    fun toggle(selection: Set<Long>?, categoryId: Long, selected: Boolean, categories: List<Category>): Set<Long>? {
        val all = allIds(categories)
        val current = selection ?: all
        val affected = if (categoryId == Category.UNCATEGORIZED_ID) {
            setOf(categoryId)
        } else {
            CategoryTree.descendantIds(categoryId, categories.filterNot(Category::isSystemCategory)) + categoryId
        }
        val next = if (selected) current + affected else current - affected
        return next.takeUnless { it.containsAll(all) }
    }

    /** "All categories" on is a full backup; off starts an explicit, empty selection. */
    fun selectAll(all: Boolean): Set<Long>? = if (all) null else emptySet()

    /** Whether a manga filed in [categoryIds] (none = Default) is part of the backup. */
    fun includesManga(selection: Set<Long>?, categoryIds: Collection<Long>): Boolean {
        if (selection == null) return true
        return categoryIds.ifEmpty { listOf(Category.UNCATEGORIZED_ID) }.any { it in selection }
    }

    /**
     * The categories written to the backup: the selected ones plus their ancestors, which are needed
     * to rebuild the hierarchy on restore (no manga is backed up because of an ancestor).
     */
    fun categoriesToBackUp(selection: Set<Long>, parentOf: Map<Long, Long>): Set<Long> {
        val result = mutableSetOf<Long>()
        selection.filter { it != Category.UNCATEGORIZED_ID }.forEach { id ->
            var current: Long? = id
            while (current != null && current != Category.UNCATEGORIZED_ID && result.add(current)) {
                current = parentOf[current]
            }
        }
        return result
    }

    private fun allIds(categories: List<Category>): Set<Long> =
        categories.filterNot(Category::isSystemCategory).mapTo(mutableSetOf(Category.UNCATEGORIZED_ID), Category::id)
}
// KNS <--
