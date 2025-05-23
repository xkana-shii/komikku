package eu.kanade.tachiyomi.ui.category.smart

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.smartCategory.interactor.CreateSmartCategory
import tachiyomi.domain.smartCategory.interactor.DeleteSmartCategory
import tachiyomi.domain.smartCategory.interactor.GetSmartCategory
import tachiyomi.domain.smartCategory.interactor.SyncSmartCategory
import tachiyomi.domain.smartCategory.model.SmartCategory
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SmartCategoriesScreenModel(
    private val getSmartCategory: GetSmartCategory = Injekt.get(),
    private val createSmartCategory: CreateSmartCategory = Injekt.get(),
    private val deleteSmartCategory: DeleteSmartCategory = Injekt.get(),
    private val syncSmartCategory: SyncSmartCategory = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
) : StateScreenModel<SmartCategoriesScreenState>(SmartCategoriesScreenState.Loading) {
    private val _events: Channel<SmartCategoriesEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            combine(
                getSmartCategory.subscribe(),
                getCategories.subscribe(),
                ::Pair,
            ).collectLatest { (smartCategories, categories) ->
                mutableState.update {
                    SmartCategoriesScreenState.Success(
                        smartCategories = smartCategories.toImmutableList(),
                        categories = categories.filter {
                            !it.isSystemCategory && smartCategories.fastAll { smartCategory -> smartCategory.categoryId != it.id }
                        }.toImmutableList(),
                    )
                }
            }
        }
    }

    fun create(categoryId: Long) {
        screenModelScope.launchIO {
            when (createSmartCategory.await(categoryId)) {
                is CreateSmartCategory.Result.SmartCategoryExists -> _events.send(SmartCategoriesEvent.SmartCategoryExists)
                else -> {}
            }
        }
    }

    fun syncCategories(smartCategory: SmartCategory) {
        val job = screenModelScope.launchIO {
            showDialog(SmartCategoriesDialog.Syncing(smartCategory))
            syncSmartCategory.await(smartCategory)
            _events.send(SmartCategoriesEvent.SmartCategorySyncComplete)
            dismissDialog()
        }

        mutableState.update {
            when (it) {
                SmartCategoriesScreenState.Loading -> it
                is SmartCategoriesScreenState.Success -> it.copy(syncJob = job)
            }
        }
    }

    fun cancelSync() {
        screenModelScope.launchIO {
            mutableState.update {
                when (it) {
                    SmartCategoriesScreenState.Loading -> it
                    is SmartCategoriesScreenState.Success -> {
                        it.syncJob?.cancel()
                        it.copy(syncJob = null)
                    }
                }
            }

            _events.send(SmartCategoriesEvent.SmartCategorySyncCancel)
        }
    }

    fun delete(categoryId: Long) {
        screenModelScope.launchIO {
            deleteSmartCategory.await(categoryId)
        }
    }

    fun showDialog(dialog: SmartCategoriesDialog) {
        mutableState.update {
            when (it) {
                SmartCategoriesScreenState.Loading -> it
                is SmartCategoriesScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                SmartCategoriesScreenState.Loading -> it
                is SmartCategoriesScreenState.Success -> it.copy(dialog = null)
            }
        }
    }


}

sealed class SmartCategoriesEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : SmartCategoriesEvent()
    data object SmartCategoryExists : LocalizedMessage(SYMR.strings.error_smart_category_exists)
    data object SmartCategorySyncComplete : LocalizedMessage(SYMR.strings.smart_category_sync_complete)
    data object SmartCategorySyncCancel : LocalizedMessage(SYMR.strings.smart_category_sync_cancel)
}

sealed class SmartCategoriesDialog {
    data object Create : SmartCategoriesDialog()
    data class Delete(val smartCategory: SmartCategory) : SmartCategoriesDialog()
    data class Sync(val smartCategory: SmartCategory) : SmartCategoriesDialog()
    data class Syncing(val smartCategory: SmartCategory) : SmartCategoriesDialog()
}

sealed class SmartCategoriesScreenState {
    @Immutable
    data object Loading : SmartCategoriesScreenState()

    @Immutable
    data class Success(
        val smartCategories: List<SmartCategory>,
        val categories: List<Category>,
        val dialog: SmartCategoriesDialog? = null,
        val syncJob: Job? = null,
    ) : SmartCategoriesScreenState() {
        val isEmpty: Boolean
            get() = smartCategories.isEmpty()
    }
}
