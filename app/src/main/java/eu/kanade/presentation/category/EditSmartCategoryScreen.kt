package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.smart.EditSmartCategoryContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.category.smart.edit.EditSmartCategoryState
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun EditSmartCategoryScreen(
    state: EditSmartCategoryState,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                navigateUp = navigateUp,
                title = stringResource(SYMR.strings.action_edit_tags),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                SYMR.strings.information_empty_smart_category_tags,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        EditSmartCategoryContent(
            tags = state.tags,
            lazyListState = lazyListState,
            paddingValues = paddingValues + topSmallPaddingValues + PaddingValues(horizontal = MaterialTheme.padding.medium),
            onDelete = onDelete,
        )
    }
}
