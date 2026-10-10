package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.kanade.tachiyomi.source.Source

class GlobalSearchViewModel(
    initialQuery: String,
    initialExtensionFilter: String?,
) : SearchViewModel(State(searchQuery = initialQuery)) {

    companion object {
        val INITIAL_QUERY_KEY = CreationExtras.Key<String>()
        val INITIAL_EXTENSION_FILTER_KEY = CreationExtras.Key<String?>()

        val Factory = viewModelFactory {
            initializer {
                GlobalSearchViewModel(
                    initialQuery = get(INITIAL_QUERY_KEY)!!,
                    initialExtensionFilter = get(INITIAL_EXTENSION_FILTER_KEY),
                )
            }
        }
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
        shouldPinnedSourcesHidden()
        // KMK <--
    }

    override fun getEnabledSources(): List<Source> {
        // KMK --> The category scope uses the Sources tab's existing sourceId|category entries.
        val category = state.value.sourceCategory
        val memberIds = if (category.isBlank()) {
            null
        } else {
            sourcesInCategories
                .mapNotNull { entry ->
                    entry.substringBefore('|')
                        .takeIf { entry.substringAfter('|') == category }
                        ?.toLongOrNull()
                }
                .toSet()
        }
        // KMK <--
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            // KMK --> A blank category keeps the normal pinned/all filter intact.
            .filter { memberIds == null || it.id in memberIds }
        // KMK <--
    }
}
