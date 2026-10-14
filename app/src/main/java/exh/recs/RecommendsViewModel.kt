package exh.recs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import exh.recs.sources.RECOMMENDS_SOURCE
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.RecommendationSource
import exh.recs.sources.StaticResultPagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga

@AssistedInject
class RecommendsViewModel(
    @Assisted private val args: RecommendsScreen.Args,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(args: RecommendsScreen.Args): RecommendsViewModel
    }

    val state: StateFlow<RecommendsViewModel.State>
        field = MutableStateFlow<RecommendsViewModel.State>(State())

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)

    private val sortComparator = { map: Map<RecommendationPagingSource, RecommendationItemResult> ->
        compareBy<RecommendationPagingSource>(
            { (map[it] as? RecommendationItemResult.Success)?.isEmpty ?: true },
            { it.name },
            { it.category.resourceId },
        )
    }

    init {
        viewModelScope.launch {
            val recommendationSources = when (args) {
                is RecommendsScreen.Args.SingleSourceManga -> {
                    val manga = getManga.await(args.mangaId) ?: return@launch
                    state.update { it.copy(title = manga.title) }

                    RecommendationPagingSource.createSources(
                        manga,
                        // KMK -->
                        RecommendationSource(args.sourceId),
                        // KMK <--
                    )
                }
                is RecommendsScreen.Args.MergedSourceMangas -> {
                    args.mergedResults.map(::StaticResultPagingSource)
                }
            }

            updateItems(
                recommendationSources
                    .associateWith { RecommendationItemResult.Loading }
                    .toMap(),
            )

            recommendationSources.map { recSource ->
                async {
                    if (state.value.items[recSource] !is RecommendationItemResult.Loading) {
                        return@async
                    }

                    try {
                        val page = withContext(coroutineDispatcher) {
                            recSource.requestNextPage(1)
                        }

                        val recSourceId = recSource.associatedSourceId
                        val titles = if (recSourceId != null) {
                            // If the recommendation is associated with a source, resolve it
                            page.mangas.map { it.toDomainManga(recSourceId) }
                                .let { networkToLocalManga(it) }
                        } else {
                            // Otherwise, skip this step. The user will be prompted to choose a source via SmartSearch
                            page.mangas.map { it.toDomainManga(RECOMMENDS_SOURCE) }
                        }
                            .distinctBy { it.url }

                        if (isActive) {
                            updateItem(recSource, RecommendationItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(recSource, RecommendationItemResult.Error(e))
                        }
                    }
                }
            }.awaitAll()
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    private fun updateItems(items: Map<RecommendationPagingSource, RecommendationItemResult>) {
        state.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toMap(),
            )
        }
    }

    private fun updateItem(source: RecommendationPagingSource, result: RecommendationItemResult) {
        val newItems = state.value.items.toMutableMap().also {
            it[source] = result
        }
        updateItems(newItems)
    }

    @Immutable
    data class State(
        val title: String? = null,
        val items: Map<RecommendationPagingSource, RecommendationItemResult> = mapOf(),
    ) {
        val progress: Int = items.count { it.value !is RecommendationItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(false) }
            .toMap()
    }
}

sealed interface RecommendationItemResult {
    data object Loading : RecommendationItemResult

    data class Error(
        val throwable: Throwable,
    ) : RecommendationItemResult

    data class Success(
        val result: List<Manga>,
    ) : RecommendationItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
