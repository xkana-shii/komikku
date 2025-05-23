package eu.kanade.presentation.category.components.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.domain.smartCategory.model.SmartCategory
import tachiyomi.presentation.core.components.material.padding

@Composable
fun SmartCategoriesContent(
    categories: List<SmartCategory>,
    onSync: (SmartCategory) -> Unit,
    onEdit: (SmartCategory) -> Unit,
    onDelete: (SmartCategory) -> Unit,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(categories, key = { it.categoryId }) { smartCategory ->
            SmartCategoriesListItem(
                smartCategory = smartCategory,
                onSync = { onSync(smartCategory) },
                onEdit = { onEdit(smartCategory) },
                onDelete = { onDelete(smartCategory) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}
