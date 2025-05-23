package eu.kanade.tachiyomi.ui.category.smart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategorySelectDialog
import eu.kanade.presentation.components.LoadingDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.category.smart.edit.EditSmartCategoryScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class SmartCategoriesScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { SmartCategoriesScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is SmartCategoriesScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as SmartCategoriesScreenState.Success

        eu.kanade.presentation.category.SmartCategoriesScreen(
            state = successState,
            onCreate = { screenModel.showDialog(SmartCategoriesDialog.Create) },
            onSync = { screenModel.showDialog(SmartCategoriesDialog.Sync(it)) },
            onEdit = { navigator.push(EditSmartCategoryScreen(it)) },
            onDelete = { screenModel.showDialog(SmartCategoriesDialog.Delete(it)) },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            SmartCategoriesDialog.Create -> {
                CategorySelectDialog(
                    categories = successState.categories.toImmutableList(),
                    onDismissRequest = screenModel::dismissDialog,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { screenModel.create(it.id) },
                )
            }

            is SmartCategoriesDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.delete(dialog.smartCategory.categoryId) },
                    title = stringResource(SYMR.strings.delete_smart_category),
                    text = stringResource(
                        SYMR.strings.delete_smart_category_confirmation,
                        dialog.smartCategory.categoryName,
                    ),
                )
            }

            is SmartCategoriesDialog.Sync -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.syncCategories(dialog.smartCategory) },
                    title = stringResource(SYMR.strings.smart_category_sync),
                    text = stringResource(
                        SYMR.strings.smart_category_sync_confirmation,
                        dialog.smartCategory.categoryName,
                    ),
                )
            }

            is SmartCategoriesDialog.Syncing -> {
                LoadingDialog(
                    onDismissRequest = screenModel::cancelSync,
                    text = stringResource(
                        SYMR.strings.smart_category_sync_progress,
                        dialog.smartCategory.categoryName,
                    ),
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is SmartCategoriesEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
