package eu.kanade.presentation.category.components.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.presentation.core.components.material.padding

@Composable
fun EditSmartCategoryContent(
    tags: ImmutableList<String>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(tags, key = { it }) { tag ->
            SmartCategoryTagListItem(
                modifier = Modifier.animateItem(),
                tag = tag,
                onDelete = { onDelete(tag) },
            )
        }
    }
}
