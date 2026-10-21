package eu.kanade.presentation.category

import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree

// KNS -->
/** A checkbox of a category assignment dialog with its depth in the category tree. */
data class CategorySelectionRow(val checkbox: CheckboxState<Category>, val depth: Int)

/**
 * The selection in tree order (every parent followed by its subtree) with each row's depth, so
 * same-named subcategories are shown under their own parents. The system category stays first.
 */
fun orderedCategorySelection(selection: List<CheckboxState<Category>>): List<CategorySelectionRow> {
    val byId = selection.associate { it.value.id to it.value }
    val checkboxById = selection.associateBy { it.value.id }
    val system = selection.filter { it.value.isSystemCategory }
    val ordered = CategoryTree.flattened(selection.map { it.value })
    return system.map { CategorySelectionRow(it, 0) } +
        ordered.mapNotNull { category ->
            checkboxById[category.id]?.let { CategorySelectionRow(it, CategoryTree.depth(category.id, byId)) }
        }
}

/**
 * The (included, excluded) category ids of a confirmed assignment dialog. A selected subcategory is
 * stored together with its ancestors, so the manga belongs to the whole branch it was filed in and
 * no selection leaves a child membership without its parent.
 */
fun categorySelectionChange(selection: List<CheckboxState<Category>>): Pair<List<Long>, List<Long>> {
    val byId = selection.associate { it.value.id to it.value }
    val included = linkedSetOf<Long>()
    selection
        .filter { it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }
        .forEach { checkbox ->
            included += checkbox.value.id
            included += CategoryTree.ancestorIds(checkbox.value.id, byId)
        }
    val excluded = selection
        .filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }
        .mapTo(linkedSetOf()) { it.value.id }
        .apply { removeAll(included) }
    return included.toList() to excluded.toList()
}
// KNS <--
