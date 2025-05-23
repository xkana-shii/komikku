package eu.kanade.tachiyomi.ui.category.smart.edit

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.smartCategory.interactor.GetSmartCategory
import tachiyomi.domain.smartCategory.interactor.UpdateSmartCategory
import tachiyomi.domain.smartCategory.model.SmartCategory
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditSmartCategoryModel(
    private val smartCategory: SmartCategory,
    private val getSmartCategory: GetSmartCategory = Injekt.get(),
    private val updateSmartCategory: UpdateSmartCategory = Injekt.get(),
) : StateScreenModel<EditSmartCategoryState>(EditSmartCategoryState(smartCategory)) {
    private val _events: Channel<EditSmartCategoryEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getSmartCategory.subscribe(smartCategory.categoryId).collectLatest { smartCategory ->
                mutableState.update { state ->
                    state.copy(
                        tags = smartCategory?.tags.orEmpty().toImmutableList(),
                    )
                }
            }
        }
    }

    fun addTag(tag: String) {
        screenModelScope.launchIO {
            when (updateSmartCategory.awaitAddTag(smartCategory.categoryId, tag)) {
                UpdateSmartCategory.Result.TagExists -> _events.send(EditSmartCategoryEvent.TagExists)
                UpdateSmartCategory.Result.InternalError -> _events.send(EditSmartCategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun removeTag(tag: String) {
        screenModelScope.launchIO {
            when (updateSmartCategory.awaitRemoveTag(smartCategory.categoryId, tag)) {
                UpdateSmartCategory.Result.TagExists -> _events.send(EditSmartCategoryEvent.TagExists)
                UpdateSmartCategory.Result.InternalError -> _events.send(EditSmartCategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun showDialog(dialog: EditSmartCategoryDialog) {
        mutableState.update {
            it.copy(
                dialog = dialog,
            )
        }
    }

    fun dismissDialog() {
        mutableState.update {
            it.copy(
                dialog = null,
            )
        }
    }
}

sealed class EditSmartCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : EditSmartCategoryEvent()
    data object TagExists : LocalizedMessage(SYMR.strings.error_tag_exists)
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed class EditSmartCategoryDialog {
    data object Create : EditSmartCategoryDialog()
    data class Delete(val tag: String) : EditSmartCategoryDialog()
}

data class EditSmartCategoryState(
    val smartCategory: SmartCategory,
    val tags: ImmutableList<String> = emptyList<String>().toImmutableList(),
    val dialog: EditSmartCategoryDialog? = null,
) {
    val isEmpty: Boolean
        get() = tags.isEmpty()
}
