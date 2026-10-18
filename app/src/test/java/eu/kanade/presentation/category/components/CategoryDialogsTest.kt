package eu.kanade.presentation.category.components

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category

// KMK -->
class CategoryDialogsTest {

    @Test
    fun `category dialog keeps manager order while placing children under their parent`() {
        val selection = listOf(
            checkbox(category(10, "Yaoi", 0, 0)),
            checkbox(category(20, "Normal", 0, 2)),
            checkbox(category(11, "Reading", 10, 1)),
            checkbox(category(21, "Reading", 20, 3)),
        )

        orderedCategorySelection(selection).map { it.value.id } shouldContainExactly listOf(10L, 11L, 20L, 21L)
    }

    @Test
    fun `selecting a child saves its full ancestor chain and keeps its parent included`() {
        val selection = listOf(
            CheckboxState.State.None(category(10, "Yaoi", 0, 0)),
            CheckboxState.State.Checked(category(11, "Reading", 10, 1)),
            CheckboxState.State.None(category(20, "Normal", 0, 2)),
            CheckboxState.State.None(category(21, "Reading", 20, 3)),
        )

        val (included, excluded) = categorySelectionChange(selection)

        included shouldContainExactly listOf(11L, 10L)
        excluded shouldContainExactly listOf(20L, 21L)
    }

    @Test
    fun `selecting a parent does not select its children`() {
        val selection = listOf(
            CheckboxState.State.Checked(category(10, "Yaoi", 0, 0)),
            CheckboxState.State.None(category(11, "Reading", 10, 1)),
            CheckboxState.State.None(category(20, "Normal", 0, 2)),
            CheckboxState.State.None(category(21, "Reading", 20, 3)),
        )

        val (included, _) = categorySelectionChange(selection)

        included shouldContainExactly listOf(10L)
    }

    private fun checkbox(category: Category) = CheckboxState.State.None(category)

    private fun category(id: Long, name: String, parentId: Long, order: Long) = Category(
        id = id,
        name = name,
        order = order,
        flags = 0,
        hidden = false,
        parentId = parentId,
    )
}
// KMK <--
