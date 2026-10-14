package eu.kanade.tachiyomi.ui.browse.extension

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.extension.model.ContentWarning
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ExtensionsViewModel(
    private val preferences: SourcePreferences,
    basePreferences: BasePreferences,
    private val extensionManager: ExtensionManager,
    private val getExtensions: GetExtensionsByType,
    private val context: Context,
) : ViewModel() {

    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    // Public so BrowseTab's search bar can observe it without subscribing to the whole state.
    val searchQuery: StateFlow<String?>
        field = MutableStateFlow(null)

    // KMK -->
    // Public so the tab's NSFW toggle can observe it without subscribing to the whole state.
    val nsfwOnly: StateFlow<Boolean>
        field = MutableStateFlow(false)
    // KMK <--

    // Public so the tab badge can observe it without subscribing to the whole state.
    val updatesCount = preferences.extensionUpdatesCount.changes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), 0)

    private val isRefreshing = MutableStateFlow(false)

    private fun extensionMapper(map: Map<String, InstallStep>): (Extension) -> ExtensionUiModel.Item = {
        ExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
    }

    @Suppress("LocalVariableName")
    private val items = combine(
        searchQuery
            // No query is the common case on every open, so don't delay it
            .debounce { if (it.isNullOrEmpty()) Duration.ZERO else 0.25.seconds }
            .map { searchQueryPredicate(it ?: "") },
        // KMK -->
        nsfwOnly
            .debounce(0.25.seconds),
        // KMK <--
        currentDownloads,
        getExtensions.subscribe(),
    ) { predicate, nsfwOnly, downloads, (_updates, _loaded, _available, _notLoaded) ->
        buildMap {
            val updates = _updates.filter(predicate).map(extensionMapper(downloads))
                // KMK -->
                .filter { !nsfwOnly || it.extension.contentWarning != ContentWarning.SAFE }
            // KMK <--
            if (updates.isNotEmpty()) {
                put(ExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending), updates)
            }

            val notLoaded = _notLoaded.filter(predicate).map(extensionMapper(downloads))
                // KMK -->
                .filter { !nsfwOnly || it.extension.contentWarning != ContentWarning.SAFE }
            // KMK <--
            if (notLoaded.isNotEmpty()) {
                put(ExtensionUiModel.Header.Resource(MR.strings.ext_not_loaded), notLoaded)
            }

            val loaded = _loaded.filter(predicate).map(extensionMapper(downloads))
                // KMK -->
                .filter { !nsfwOnly || it.extension.contentWarning != ContentWarning.SAFE }
            // KMK <--
            if (loaded.isNotEmpty()) {
                put(ExtensionUiModel.Header.Resource(MR.strings.ext_installed), loaded)
            }

            val languagesWithExtensions = _available
                .filter(predicate)
                // KMK -->
                .filter { !nsfwOnly || it.contentWarning != ContentWarning.SAFE }
                // KMK <--
                .groupBy { it.lang }
                .toSortedMap(LocaleHelper.comparator)
                .map { (lang, exts) ->
                    ExtensionUiModel.Header.Text(LocaleHelper.getSourceDisplayName(lang, context)) to
                        exts.map(extensionMapper(downloads))
                }
            if (languagesWithExtensions.isNotEmpty()) {
                putAll(languagesWithExtensions)
            }

            // KMK -->
            // Show "More..." header if no available extensions
            if (_available.isEmpty()) {
                put(ExtensionUiModel.Header.Resource(KMR.strings.extensions_page_more), emptyList())
            }
            // KMK <--
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    private data class PartialState(
        val items: ItemGroups?,
        val searchQuery: String?,
        // KMK -->
        val nsfwOnly: Boolean,
        // KMK <--
        val isRefreshing: Boolean,
    )

    private val partialState = combine(
        items,
        searchQuery,
        // KMK -->
        nsfwOnly,
        // KMK <--
        isRefreshing,
    ) { items, searchQuery, nsfwOnly, isRefreshing ->
        PartialState(
            items = items,
            searchQuery = searchQuery,
            // KMK -->
            nsfwOnly = nsfwOnly,
            // KMK <--
            isRefreshing = isRefreshing,
        )
    }

    val state: StateFlow<State> = combine(
        partialState,
        preferences.extensionUpdatesCount.changes(),
        basePreferences.extensionInstaller.changes(),
    ) { partial, updates, installer ->
        State(
            isLoading = partial.items == null,
            isRefreshing = partial.isRefreshing,
            items = partial.items.orEmpty(),
            updates = updates,
            installer = installer,
            searchQuery = partial.searchQuery,
            // KMK -->
            nsfwOnly = partial.nsfwOnly,
            // KMK <--
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    init {

        viewModelScope.launch(Dispatchers.IO) { findAvailableExtensions() }
    }

    fun searchQueryPredicate(query: String): (Extension) -> Boolean {
        val subqueries = query.split(",")
            .map { it.trim() }
            .filterNot { it.isBlank() }

        if (subqueries.isEmpty()) return { true }

        return { extension ->
            subqueries.any { subquery ->
                if (extension.name.contains(subquery, ignoreCase = true)) return@any true

                when (extension) {
                    is Extension.Loaded -> extension.sources.any { source ->
                        source.name.contains(subquery, ignoreCase = true) ||
                            (source as? HttpSource)?.getHomeUrl()?.contains(subquery, ignoreCase = true) == true ||
                            source.id == subquery.toLongOrNull()
                    }

                    is Extension.Available -> extension.sources.any {
                        it.name.contains(subquery, ignoreCase = true) ||
                            it.baseUrl.contains(subquery, ignoreCase = true) ||
                            it.id == subquery.toLongOrNull()
                    }

                    else -> false
                }
            }
        }
    }

    fun search(query: String?) {
        searchQuery.update { query }
    }

    fun updateAllExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<Extension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: Extension.Available) {
        viewModelScope.launch(Dispatchers.IO) {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        viewModelScope.launch(Dispatchers.IO) {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        extensionManager.cancelInstallUpdateExtension(extension)
        removeDownloadState(extension)
    }

    private fun addDownloadState(extension: Extension, installStep: InstallStep) {
        currentDownloads.update {
            it + Pair(
                extension.pkgName,
                installStep,
            )
        }
    }

    private fun removeDownloadState(extension: Extension) {
        currentDownloads.update {
            it - extension.pkgName
        }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: Extension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .takeWhile { installStep -> installStep != InstallStep.Installed }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: Extension.Installed) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.update { true }

            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            isRefreshing.update { false }
        }
    }

    fun trustExtension(extension: Extension.NotLoaded) {
        extensionManager.trust(extension)
    }

    // KMK -->
    fun toggleNsfwOnly() {
        nsfwOnly.update { !it }
    }
    // KMK <--

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val installer: BasePreferences.ExtensionInstaller? = null,
        val searchQuery: String? = null,
        // KMK -->
        val nsfwOnly: Boolean = false,
        // KMK <--
    ) {
        val isEmpty = items.isEmpty()
    }
}

typealias ItemGroups = Map<ExtensionUiModel.Header, List<ExtensionUiModel.Item>>

object ExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }

    data class Item(
        val extension: Extension,
        val installStep: InstallStep,
    )
}
