package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class GlobalSearchViewModel(
    @Assisted initialQuery: String,
    @Assisted initialExtensionFilter: String?,
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
    preferences: SourcePreferences,
    // KNS -->
    getSourceCategories: GetSourceCategories,
    // KNS <--
) : SearchViewModel(
    initialState = State(searchQuery = initialQuery),
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
    preferences = preferences,
) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(initialQuery: String, initialExtensionFilter: String?): GlobalSearchViewModel
    }

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(SourceFilter.All)
            }
            search()
        }

        // KMK -->
        viewModelScope.launchIO {
            shouldPinnedSourcesHidden()
        }
        // KMK <--
    }

    // KNS -->
    /** The Sources-tab categories offered as a search scope. */
    val sourceCategories: StateFlow<List<String>> = getSourceCategories.subscribe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    // KNS <--

    override suspend fun getEnabledSources(): List<Source> {
        // KNS -->
        val memberIds = sourcesInCategory(state.value.sourceCategory, sourcesInCategories)
        // KNS <--
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            // KNS --> a blank category keeps the Pinned / All filter as it is
            .filter { memberIds == null || it.id in memberIds }
        // KNS <--
    }

    // KNS -->
    companion object {
        /**
         * The source ids filed under [category] in the Sources tab (`sourceId|category` entries);
         * null for no category, so every source passes.
         */
        fun sourcesInCategory(category: String, sourcesInCategories: Set<String>): Set<Long>? {
            if (category.isBlank()) return null
            return sourcesInCategories.mapNotNullTo(mutableSetOf()) { entry ->
                entry.substringBefore('|').takeIf { entry.substringAfter('|') == category }?.toLongOrNull()
            }
        }
    }
    // KNS <--
}
