package eu.kanade.presentation.category

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.smart.SmartCategoriesContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.category.smart.SmartCategoriesScreenState
import tachiyomi.domain.smartCategory.model.SmartCategory
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun SmartCategoriesScreen(
    state: SmartCategoriesScreenState.Success,
    onCreate: () -> Unit,
    onSync: (SmartCategory) -> Unit,
    onEdit: (SmartCategory) -> Unit,
    onDelete: (SmartCategory) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                navigateUp = navigateUp,
                title = stringResource(SYMR.strings.smart_categories),
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
                SYMR.strings.information_empty_smart_categories,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        SmartCategoriesContent(
            categories = state.smartCategories,
            onSync = onSync,
            onEdit = onEdit,
            onDelete = onDelete,
            lazyListState = lazyListState,
            paddingValues = paddingValues + topSmallPaddingValues + PaddingValues(horizontal = MaterialTheme.padding.medium),
        )
    }
}
