package eu.kanade.tachiyomi.ui.browse.source.browse

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.asState
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
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.removeCovers
import exh.metadata.metadata.RaisedSearchMetadata
import exh.source.EH_PACKAGE
import exh.source.ExhPreferences
import exh.source.LOCAL_SOURCE_PACKAGE
import exh.source.getMainSource
import exh.source.isEhBasedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.interactor.UpdateSavedSearch
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.SourcePagingSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import tachiyomi.source.local.isLocal
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import eu.kanade.tachiyomi.source.model.Filter as SourceModelFilter

@AssistedInject
open class BrowseSourceViewModel(
    /* KMK --> */
    @Assisted protected /* KMK <-- */ val sourceId: Long,
    @Assisted listingQuery: String?,
    // SY -->
    @Assisted filtersJson: String?,
    @Assisted savedSearch: Long?,
    // SY <--
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val sourceManager: SourceManager,
    sourcePreferences: SourcePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val coverCache: CoverCache,
    private val getRemoteManga: GetRemoteManga,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val getManga: GetManga,
    private val updateManga: UpdateManga,
    private val addTracks: AddTracks,
    getIncognitoState: GetIncognitoState,
    // KMK -->
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val toggleIncognito: ToggleIncognito,
    private val extensionManager: ExtensionManager,
    // KMK <--

    // SY -->
    exhPreferences: ExhPreferences,
    uiPreferences: UiPreferences,
    private val getFlatMetadataById: GetFlatMetadataById,
    private val deleteSavedSearchById: DeleteSavedSearchById,
    private val insertSavedSearch: InsertSavedSearch,
    // KNS -->
    private val updateSavedSearch: UpdateSavedSearch,
    // KNS <--
    private val getExhSavedSearch: GetExhSavedSearch,
    // SY <--
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(
            sourceId: Long,
            listingQuery: String?,
            filtersJson: String?,
            savedSearch: Long?,
        ): BrowseSourceViewModel
    }

    val state: StateFlow<BrowseSourceViewModel.State>
        field = MutableStateFlow<BrowseSourceViewModel.State>(State(Listing.valueOf(listingQuery)))

    // KUN -->
    /** Updates [state] from subclasses, which cannot access the backing field directly. */
    protected fun updateState(transform: (State) -> State) {
        state.update(transform)
    }
    // KUN <--

    var displayMode by sourcePreferences.sourceDisplayMode.asState(viewModelScope)

    val source: Source? get() = state.value.source

    // SY -->
    val ehentaiBrowseDisplayMode by exhPreferences.enhancedEHentaiView.asState(viewModelScope)

    val startExpanded by uiPreferences.expandFilters.asState(viewModelScope)

    private val filterSerializer = FilterSerializer()

    val savedSearches: StateFlow<List<EXHSavedSearch>> = state.map { it.source }
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { source -> getExhSavedSearch.subscribe(source.id, source::getFilterList) }
        .map { it.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, EXHSavedSearch::name)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())
    // SY <--

    // KMK -->
    var incognitoMode = mutableStateOf(false)
    // KMK <--

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val source = sourceManager.getOrStub(sourceId)

            state.update {
                var query: String? = null
                var listing = it.listing

                if (listing is Listing.Search) {
                    query = listing.query
                    listing = Listing.Search(query, source.getFilterList())
                }

                it.copy(
                    source = source,
                    listing = listing,
                    filters = source.getFilterList(),
                    toolbarQuery = query,
                )
            }

            // SY -->
            val savedSearchId = savedSearch
            val jsonFilters = filtersJson
            val filters = state.value.filters
            if (savedSearchId != null) {
                val savedSearch = getExhSavedSearch.awaitOne(savedSearchId) { filters }
                if (savedSearch != null) {
                    search(
                        query = savedSearch.query,
                        filters = savedSearch.filterList,
                        // KMK -->
                        savedSearchId = savedSearchId,
                        // KMK <--
                    )
                }
            } else if (jsonFilters != null) {
                runCatching {
                    val filtersJson = Json.decodeFromString<JsonArray>(jsonFilters)
                    filterSerializer.deserialize(filters, filtersJson)
                    search(filters = filters)
                }
            }

            // SY <--
        }

        // KMK-->
        getIncognitoState.subscribe(sourceId)
            .onEach {
                if (!it) sourcePreferences.lastUsedSource.set(sourceId)
                incognitoMode.value = it
            }
            .launchIn(viewModelScope)
        // KMK <--
    }

    // KMK -->
    fun toggleIncognitoMode() {
        viewModelScope.launchIO {
            val packageName = when {
                source is StubSource -> null
                source?.isLocal() == true -> LOCAL_SOURCE_PACKAGE
                source?.isEhBasedSource() == true -> EH_PACKAGE
                else -> extensionManager.getExtensionPackage(sourceId)
            }
            packageName?.let {
                toggleIncognito.await(it, !incognitoMode.value)
            }
        }
    }
    // KMK <--

    /**
     * Flow of Pager flow tied to [State.listing]
     */
    private val hideInLibraryItems = sourcePreferences.hideInLibraryItems.get()
    val mangaPagerFlowFlow = state.map { it.source to it.listing }
        .filter { (source, _) -> source != null }
        .map { (source, listing) -> source!! to listing }
        .distinctUntilChanged()
        .map { (source, listing) ->
            Pager(PagingConfig(pageSize = 25)) {
                // SY -->
                createSourcePagingSource(source, listing.query ?: "", listing.filters)
                // SY <--
            }.flow.map { pagingData ->
                pagingData.map { (manga, metadata) ->
                    getManga.subscribe(manga.url, manga.source)
                        .map { it ?: manga }
                        // SY -->
                        .combineMetadata(metadata)
                        // SY <--
                        // KUN -->
                        // Ported from Mihon 2d1d2e4cab ("Rework database queries for the new schema"):
                        // query an entry only while it is shown. The initial value is the paging row
                        // as a Pair, because combineMetadata maps the flow to Pair<Manga, RaisedSearchMetadata?>.
                        .stateIn(
                            viewModelScope,
                            SharingStarted.WhileSubscribed(5.seconds),
                            manga to metadata,
                        )
                    // KUN <--
                }
                    .filter { !hideInLibraryItems || !it.value.first.favorite }
            }
                .cachedIn(viewModelScope)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns
        } else {
            libraryPreferences.portraitColumns
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    // SY -->
    open fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Manga, RaisedSearchMetadata?>> {
        val metadataSource = source?.getMainSource<MetadataSource<*, *>>()
        return flatMapLatest { manga ->
            if (metadataSource != null) {
                getFlatMetadataById.subscribe(manga.id)
                    .map { flatMetadata ->
                        manga to (flatMetadata?.raise(metadataSource.metaClass) ?: metadata)
                    }
            } else {
                flowOf(manga to null)
            }
        }
    }
    // SY <--

    fun resetFilters() {
        // KMK -->
        setFilters(source?.getFilterList() ?: FilterList())
        // KMK <--
    }

    fun setListing(listing: Listing) {
        state.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: FilterList) {
        state.update {
            it.copy(
                filters = filters,
            )
        }
    }

    fun search(
        query: String? = null,
        filters: FilterList? = null,
        // KMK -->
        savedSearchId: Long? = null,
        // KMK <--
    ) {
        // SY -->
        if (filters != null && filters !== state.value.filters) {
            // KMK -->
            setFilters(filters)
            // KMK <--
        }
        // SY <--
        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = source?.getFilterList() ?: FilterList())

        state.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                    // KMK -->
                    savedSearchId = savedSearchId,
                    // KMK <--
                ),
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun searchGenre(genreName: String) {
        val defaultFilters = source?.getFilterList() ?: return
        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is SourceModelFilter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is SourceModelFilter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is SourceModelFilter.TriState -> filter.state = 1
                            is SourceModelFilter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is SourceModelFilter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }

        state.update {
            val listing = if (genreExists) {
                Listing.Search(query = null, filters = defaultFilters)
            } else {
                Listing.Search(query = genreName, filters = defaultFilters)
            }
            it.copy(
                filters = defaultFilters,
                listing = listing,
                toolbarQuery = listing.query,
            )
        }
    }

    /**
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    fun changeMangaFavorite(manga: Manga) {
        appScope.launch {
            val source = source ?: return@launch
            val favorite = !manga.favorite
            val update = if (manga.favorite) {
                manga.removeCovers(coverCache)
                MangaUpdate(manga.id) {
                    this.favorite = false
                    dateAdded = null
                }
            } else {
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
                MangaUpdate(manga.id) {
                    this.favorite = true
                    dateAdded = Clock.System.now()
                }
            }

            updateManga.await(update)
            // KMK -->
            val fetchMetadataOnAdd = libraryPreferences.fetchMetadataOnAdd.get()
            val fetchChaptersOnAdd = libraryPreferences.fetchChaptersOnAdd.get()
            if (favorite && (fetchMetadataOnAdd || fetchChaptersOnAdd)) {
                try {
                    // Use `manga` instead of `new` so its title got updated with source's `getMangaDetails`
                    updateMangaFromRemote(
                        source = source,
                        manga = manga,
                        fetchDetails = fetchMetadataOnAdd,
                        fetchChapters = fetchChaptersOnAdd,
                    )
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }
            // KMK <--
        }
    }

    fun addFavorite(manga: Manga) {
        viewModelScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveMangaToCategories(manga, defaultCategory)

                    changeMangaFavorite(manga)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveMangaToCategories(manga)

                    changeMangaFavorite(manga)
                }

                // Choose a category
                else -> {
                    val preselectedIds = getCategories.await(manga.id).map { it.id }
                    setDialog(
                        Dialog.ChangeMangaCategory(
                            manga,
                            categories.mapAsCheckboxState { it.id in preselectedIds }.toList(),
                        ),
                    )
                }
            }
        }
    }

    // SY -->
    open fun createSourcePagingSource(source: Source, query: String, filters: FilterList): SourcePagingSource {
        return getRemoteManga(sourceId, query, filters)
    }
    // SY <--

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCategory }
            .orEmpty()
    }

    suspend fun getDuplicateLibraryManga(manga: Manga): List<MangaWithChapterCount> {
        return getDuplicateLibraryManga.invoke(manga)
    }

    private fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            setMangaCategories.await(
                mangaId = manga.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    fun setDialog(dialog: Dialog?) {
        state.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        state.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        data object Popular : Listing(query = GetRemoteManga.QUERY_POPULAR, filters = FilterList())
        data object Latest : Listing(query = GetRemoteManga.QUERY_LATEST, filters = FilterList())
        data class Search(
            override val query: String?,
            override val filters: FilterList,
            // KMK -->
            val savedSearchId: Long? = null,
            // KMK <--
        ) : Listing(query = query, filters = filters)

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    GetRemoteManga.QUERY_POPULAR -> Popular
                    GetRemoteManga.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = FilterList()) // filters are filled in later
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data class RemoveManga(val manga: Manga) : Dialog
        data class AddDuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState.State<Category>>,
        ) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog

        // SY -->
        data class DeleteSavedSearch(val idToDelete: Long, val name: String) : Dialog
        data class CreateSavedSearch(val currentSavedSearches: List<String>) : Dialog
        // SY <--
    }

    @Immutable
    data class State(
        val listing: Listing,
        val source: Source? = null,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        // SY -->
        val filterable: Boolean = true,
        // SY <--
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }

    // EXH -->
    /** Show a dialog to enter name for new saved search */
    fun onSaveSearch() {
        viewModelScope.launchIO {
            val names = savedSearches.value.map { it.name }.toList()
            state.update { it.copy(dialog = Dialog.CreateSavedSearch(names)) }
        }
    }

    /** Open a saved search */
    fun onSavedSearch(
        // KMK -->
        loadedSearch: EXHSavedSearch,
        // KMK <--
        onToast: (StringResource) -> Unit,
    ) {
        // KMK -->
        resetFilters()
        // KMK <--
        viewModelScope.launchIO {
            // KMK -->
            // KUN -->
            val source = source ?: return@launchIO
            // KUN <--
            val search = getExhSavedSearch.awaitOne(loadedSearch.id, source::getFilterList) ?: loadedSearch
            // KMK <--

            if (search.filterList == null && state.value.filters.isNotEmpty()) {
                withUIContext {
                    onToast(SYMR.strings.save_search_invalid)
                }
                return@launchIO
            }

            val allDefault = search.filterList != null && search.filterList == source.getFilterList()
            setDialog(null)

            val filters = search.filterList
                ?.takeUnless { allDefault }
                ?: source.getFilterList()

            state.update {
                it.copy(
                    listing = Listing.Search(
                        query = search.query,
                        filters = filters,
                        // KMK -->
                        savedSearchId = search.id,
                        // KMK <--
                    ),
                    filters = filters,
                    toolbarQuery = search.query,
                )
            }
        }
    }

    /** Show dialog to delete saved search */
    fun onSavedSearchPress(search: EXHSavedSearch) {
        state.update { it.copy(dialog = Dialog.DeleteSavedSearch(search.id, search.name)) }
    }

    /** Save a search */
    fun saveSearch(
        name: String,
    ) {
        viewModelScope.launchNonCancellable {
            val source = source ?: return@launchNonCancellable
            val query = state.value.toolbarQuery?.takeUnless {
                it.isBlank() || it == GetRemoteManga.QUERY_POPULAR || it == GetRemoteManga.QUERY_LATEST
            }?.trim()
            val filterList = state.value.filters.ifEmpty { source.getFilterList() }
            // KNS --> saving under an existing name replaces that saved search (the dialog asked first),
            // keeping its id so feeds built on it follow, instead of adding a second one
            val existing = UpdateSavedSearch.existingFor(name, savedSearches.value) { it.name }
            val savedSearch = SavedSearch(
                id = existing?.id ?: -1,
                source = source.id,
                name = name.trim(),
                query = query,
                filtersJson = runCatching {
                    filterSerializer.serialize(filterList).ifEmpty { null }?.let { Json.encodeToString(it) }
                }.getOrNull(),
            )
            if (existing != null) {
                updateSavedSearch.await(savedSearch)
            } else {
                insertSavedSearch.await(savedSearch)
            }
            // KNS <--
        }
    }

    fun deleteSearch(savedSearchId: Long) {
        viewModelScope.launchNonCancellable {
            deleteSavedSearchById.await(savedSearchId)
        }
    }

    fun onMangaDexRandom(onRandomFound: (String) -> Unit) {
        viewModelScope.launchIO {
            val source = source ?: return@launchIO
            val random = source.getMainSource<MangaDex>()?.fetchRandomMangaUrl()
                ?: return@launchIO
            onRandomFound(random)
        }
    }
    // EXH <--
}
