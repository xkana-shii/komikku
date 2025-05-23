package eu.kanade.tachiyomi.ui.category.smart.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.smartCategory.model.SmartCategory
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

class EditSmartCategoryScreen(
    private val smartCategory: SmartCategory,
) : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { EditSmartCategoryModel(smartCategory) }

        val state by screenModel.state.collectAsState()

        eu.kanade.presentation.category.EditSmartCategoryScreen(
            state = state,
            onCreate = { screenModel.showDialog(EditSmartCategoryDialog.Create) },
            onDelete = { screenModel.showDialog(EditSmartCategoryDialog.Delete(it)) },
            navigateUp = navigator::pop,
        )

        when (val dialog = state.dialog) {
            null -> {}
            EditSmartCategoryDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.addTag(it) },
                    categories = state.tags,
                    title = stringResource(SYMR.strings.add_tag),
                    extraMessage = stringResource(SYMR.strings.action_add_tags_message),
                    alreadyExistsError = SYMR.strings.error_tag_exists,
                )
            }

            is EditSmartCategoryDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.removeTag(dialog.tag) },
                    title = stringResource(SYMR.strings.delete_tag),
                    text = stringResource(SYMR.strings.delete_tag_confirmation, dialog.tag),
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is EditSmartCategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
