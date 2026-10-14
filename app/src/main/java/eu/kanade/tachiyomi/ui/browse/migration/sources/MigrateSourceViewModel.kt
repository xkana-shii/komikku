package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Source
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MigrateSourceViewModel(
    private val preferences: SourcePreferences,
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount,
) : ViewModel() {

    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    // KMK -->
    private val searchQuery = MutableStateFlow<String?>(null)
    // KMK <--

    private val sourcesWithCount = combine(
        // KMK -->
        searchQuery,
        // KMK <--
        getSourcesWithFavoriteCount.subscribe()
            .catch {
                logcat(LogPriority.ERROR, it)
                _channel.send(Event.FailedFetchingSourcesWithCount)
            },
    ) { query, sources ->
        // KMK -->
        val queryFilter: (String?) -> ((Pair<Source, Long>) -> Boolean) = { searchQuery ->
            filter@{ pair ->
                val source = pair.first
                if (searchQuery.isNullOrBlank()) return@filter true
                searchQuery.split(",").any {
                    val input = it.trim()
                    if (input.isEmpty()) return@any false
                    source.installedExtension?.name?.contains(input, ignoreCase = true) == true ||
                        source.name.contains(input, ignoreCase = true) ||
                        source.id == input.toLongOrNull()
                }
            }
        }
        sources.filter(queryFilter(query))
        // KMK <--
    }

    val state: StateFlow<State> = combine(
        sourcesWithCount,
        preferences.migrationSortingMode.changes(),
        preferences.migrationSortingDirection.changes(),
        // KMK -->
        searchQuery,
        // KMK <--
    ) { sources, sortingMode, sortingDirection, query ->
        State(
            isLoading = false,
            items = sources.toList(),
            sortingMode = sortingMode,
            sortingDirection = sortingDirection,
            // KMK -->
            searchQuery = query,
            // KMK <--
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    fun toggleSortingMode() {
        preferences.migrationSortingMode.getAndSet { mode ->
            when (mode) {
                SetMigrateSorting.Mode.ALPHABETICAL -> SetMigrateSorting.Mode.TOTAL
                SetMigrateSorting.Mode.TOTAL -> SetMigrateSorting.Mode.ALPHABETICAL
            }
        }
    }

    fun toggleSortingDirection() {
        preferences.migrationSortingDirection.getAndSet { direction ->
            when (direction) {
                SetMigrateSorting.Direction.ASCENDING -> SetMigrateSorting.Direction.DESCENDING
                SetMigrateSorting.Direction.DESCENDING -> SetMigrateSorting.Direction.ASCENDING
            }
        }
    }

    // KMK -->
    fun search(query: String?) {
        searchQuery.value = query
    }
    // KMK <--

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: List<Pair<Source, Long>> = listOf(),
        val sortingMode: SetMigrateSorting.Mode = SetMigrateSorting.Mode.ALPHABETICAL,
        val sortingDirection: SetMigrateSorting.Direction = SetMigrateSorting.Direction.ASCENDING,
        // KMK -->
        val searchQuery: String? = null,
        // KMK <--
    ) {
        val isEmpty = items.isEmpty()
    }

    sealed interface Event {
        data object FailedFetchingSourcesWithCount : Event
    }
}
