package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTreeHandler
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
@Composable
fun CategoryParentPickerDialog(
    category: Category,
    categories: List<Category>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(KMR.strings.category_move)) },
        text = {
            Column {
                ParentChoice(
                    label = stringResource(KMR.strings.category_root),
                    selected = category.parentId == Category.UNCATEGORIZED_ID,
                    onClick = { onSelect(Category.UNCATEGORIZED_ID) },
                )
                categories.filter { parent ->
                    parent.id != category.id &&
                        parent.parentId == Category.UNCATEGORIZED_ID &&
                        CategoryTreeHandler.canMove(category.id, parent.id, categories)
                }.forEach { parent ->
                    ParentChoice(
                        label = parent.name,
                        selected = category.parentId == parent.id,
                        onClick = { onSelect(parent.id) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun ParentChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}
// KMK <--
