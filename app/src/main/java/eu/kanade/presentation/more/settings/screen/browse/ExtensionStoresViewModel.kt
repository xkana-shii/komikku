package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.extension.interactor.AddExtensionStore
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.interactor.RemoveExtensionStore
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.model.ExtensionStore
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ExtensionStoresViewModel(
    private val getExtensionStores: GetExtensionStores,
    private val addExtensionStore: AddExtensionStore,
    private val removeExtensionStore: RemoveExtensionStore,
    private val updateExtensionStores: UpdateExtensionStores,
    private val extensionManager: ExtensionManager,
    // KUN -->
    private val sourcePreferences: SourcePreferences,
    // KUN <--
) : ViewModel() {

    private val dialog = MutableStateFlow<ExtensionStoreDialog?>(null)

    val state: StateFlow<ExtensionStoreScreenState> = combine(
        getExtensionStores.subscribe(),
        dialog,
        // KUN -->
        sourcePreferences.disabledRepos.changes(),
        // KUN <--
    ) { stores, dialog, disabledRepos ->
        ExtensionStoreScreenState.Success(
            stores = stores,
            dialog = dialog,
            // KUN -->
            disabledRepos = disabledRepos,
            // KUN <--
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), ExtensionStoreScreenState.Loading)

    /**
     * Creates and adds a new repo to the database.
     *
     * @param indexUrl The baseUrl of the repo to create.
     */
    fun createRepo(indexUrl: String) {
        // KUN -->
        viewModelScope.launch(Dispatchers.IO) {
            // KUN <--
            dialog.update {
                when (it) {
                    is ExtensionStoreDialog.Create -> it.copy(processing = true)
                    is ExtensionStoreDialog.Confirm -> it.copy(processing = true)
                    else -> it
                }
            }
            addExtensionStore(indexUrl)
                .onSuccess {
                    extensionManager.findAvailableExtensions()
                    dismissDialog()
                }
                .onFailure { throwable ->
                    dialog.update {
                        when (it) {
                            is ExtensionStoreDialog.Create -> it.copy(
                                processing = false,
                                errorMessage = throwable.message ?: "unknown error",
                            )
                            is ExtensionStoreDialog.Confirm -> it.copy(
                                processing = false,
                                errorMessage = throwable.message ?: "unknown error",
                            )
                            else -> it
                        }
                    }
                }
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        viewModelScope.launch(Dispatchers.IO) {
            updateExtensionStores()
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(indexUrl: String) {
        // KUN -->
        // Remove repo from disabled list
        enableStore(indexUrl)
        // KUN <--
        viewModelScope.launch(Dispatchers.IO) {
            removeExtensionStore(indexUrl)
            extensionManager.findAvailableExtensions()
        }
    }

    // KUN -->
    fun enableStore(indexUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos.get()
        if (indexUrl in disabledRepos) {
            sourcePreferences.disabledRepos.set(
                disabledRepos.filterNot { it == indexUrl }.toSet(),
            )
        }
    }

    fun disableStore(indexUrl: String) {
        val disabledRepos = sourcePreferences.disabledRepos.get()
        if (indexUrl !in disabledRepos) {
            sourcePreferences.disabledRepos.set(
                disabledRepos + indexUrl,
            )
        }
    }

    fun refreshExtensionList() {
        viewModelScope.launch(Dispatchers.IO) {
            extensionManager.findAvailableExtensions()
        }
    }
    // KUN <--

    fun addFromDeeplink(storeIndexUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val alreadyExists = getExtensionStores.get().any { it.indexUrl == storeIndexUrl }
            dialog.update { ExtensionStoreDialog.Confirm(url = storeIndexUrl, alreadyExists = alreadyExists) }
        }
    }

    fun showDialog(dialog: ExtensionStoreDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

sealed class ExtensionStoreDialog {
    data class Create(val processing: Boolean = false, val errorMessage: String? = null) : ExtensionStoreDialog()
    data class Delete(val store: ExtensionStore) : ExtensionStoreDialog()
    data class Confirm(
        val url: String,
        val alreadyExists: Boolean = false,
        val processing: Boolean = false,
        val errorMessage: String? = null,
    ) : ExtensionStoreDialog()
}

sealed class ExtensionStoreScreenState {

    @Immutable
    data object Loading : ExtensionStoreScreenState()

    @Immutable
    data class Success(
        val stores: List<ExtensionStore>,
        val dialog: ExtensionStoreDialog? = null,
        // KUN -->
        val disabledRepos: Set<String> = emptySet(),
        // KUN <--
    ) : ExtensionStoreScreenState() {

        val isEmpty: Boolean
            get() = stores.isEmpty()
    }
}
