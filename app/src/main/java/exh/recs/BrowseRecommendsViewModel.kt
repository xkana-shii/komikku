package exh.recs

import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel
import exh.metadata.metadata.RaisedSearchMetadata
import exh.recs.sources.RECOMMENDS_SOURCE
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.RecommendationSource
import exh.recs.sources.StaticResultPagingSource
import exh.source.ExhPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import mihon.core.metro.AppCoroutineScope
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.interactor.UpdateSavedSearch
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class BrowseRecommendsViewModel(
    @Assisted private val args: BrowseRecommendsScreen.Args,
    private val getManga: GetManga,
    sourceManager: SourceManager,
    sourcePreferences: SourcePreferences,
    libraryPreferences: LibraryPreferences,
    coverCache: CoverCache,
    getRemoteManga: GetRemoteManga,
    getDuplicateLibraryManga: GetDuplicateLibraryManga,
    getCategories: GetCategories,
    setMangaCategories: SetMangaCategories,
    setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    updateManga: UpdateManga,
    addTracks: AddTracks,
    getIncognitoState: GetIncognitoState,
    updateMangaFromRemote: UpdateMangaFromRemote,
    toggleIncognito: ToggleIncognito,
    extensionManager: ExtensionManager,
    exhPreferences: ExhPreferences,
    uiPreferences: UiPreferences,
    getFlatMetadataById: GetFlatMetadataById,
    deleteSavedSearchById: DeleteSavedSearchById,
    insertSavedSearch: InsertSavedSearch,
    // KNS -->
    updateSavedSearch: UpdateSavedSearch,
    // KNS <--
    getExhSavedSearch: GetExhSavedSearch,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : BrowseSourceViewModel(
    sourceId = when (args) {
        is BrowseRecommendsScreen.Args.SingleSourceManga -> args.sourceId
        is BrowseRecommendsScreen.Args.MergedSourceMangas -> args.results.recAssociatedSourceId ?: RECOMMENDS_SOURCE
    },
    listingQuery = null,
    filtersJson = null,
    savedSearch = null,
    appScope = appScope,
    sourceManager = sourceManager,
    sourcePreferences = sourcePreferences,
    libraryPreferences = libraryPreferences,
    coverCache = coverCache,
    getRemoteManga = getRemoteManga,
    getDuplicateLibraryManga = getDuplicateLibraryManga,
    getCategories = getCategories,
    setMangaCategories = setMangaCategories,
    setMangaDefaultChapterFlags = setMangaDefaultChapterFlags,
    getManga = getManga,
    updateManga = updateManga,
    addTracks = addTracks,
    getIncognitoState = getIncognitoState,
    updateMangaFromRemote = updateMangaFromRemote,
    toggleIncognito = toggleIncognito,
    extensionManager = extensionManager,
    exhPreferences = exhPreferences,
    uiPreferences = uiPreferences,
    getFlatMetadataById = getFlatMetadataById,
    deleteSavedSearchById = deleteSavedSearchById,
    insertSavedSearch = insertSavedSearch,
    // KNS -->
    updateSavedSearch = updateSavedSearch,
    // KNS <--
    getExhSavedSearch = getExhSavedSearch,
) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(args: BrowseRecommendsScreen.Args): BrowseRecommendsViewModel
    }

    private var manga: Manga? = null

    init {
        viewModelScope.launchIO {
            manga = when (args) {
                is BrowseRecommendsScreen.Args.SingleSourceManga -> getManga.await(args.mangaId)
                is BrowseRecommendsScreen.Args.MergedSourceMangas -> null
            }
        }
    }

    val recommendationPagingSource: RecommendationPagingSource
        get() = when (args) {
            is BrowseRecommendsScreen.Args.MergedSourceMangas -> StaticResultPagingSource(args.results)
            is BrowseRecommendsScreen.Args.SingleSourceManga -> RecommendationPagingSource.createSources(
                manga ?: runBlocking(Dispatchers.IO) { getManga.await(args.mangaId)!! },
                // KMK -->
                RecommendationSource(sourceId),
                // KMK <--
            ).first {
                it::class.qualifiedName == args.recommendationSourceName
            }
        }

    override fun createSourcePagingSource(source: Source, query: String, filters: FilterList) = recommendationPagingSource

    override fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        // Overridden to prevent our custom metadata from being replaced from a cache
        return flatMapLatest { manga -> flowOf(manga to metadata) }
    }

    init {
        updateState { it.copy(filterable = false) }
    }
}
