package mihon.feature.upcoming

import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.core.util.insertSeparatorsReversed
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.upcoming.interactor.GetUpcomingManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.seconds

class UpcomingViewModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
) : ViewModel() {

    // KMK -->
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val updatingState = MutableStateFlow(UpdatingState())
    // KMK <--

    private val selectedYearMonth = MutableStateFlow(YearMonth.now())

    private val upcoming = flow { emitAll(getUpcomingManga.subscribe()) }
        .map { it.toUpcomingUIModels() }
        .flowOn(Dispatchers.IO)
        .stateIn<ImmutableList<UpcomingUIModel>?>(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds),
            null,
        )

    val state: StateFlow<State> = combine(
        upcoming,
        selectedYearMonth,
        // KMK -->
        updatingState,
        // KMK <--
    ) { upcoming, selectedYearMonth, updating ->
        val upcomingItems = upcoming ?: persistentListOf()
        State(
            selectedYearMonth = selectedYearMonth,
            items = upcomingItems,
            events = upcomingItems.toEvents(),
            headerIndexes = upcomingItems.getHeaderIndexes(),
            // KMK -->
            isLoadingUpcoming = upcoming == null,
            isShowingUpdatingMangas = updating.isShowing,
            updatingItems = updating.items,
            updatingEvents = updating.items.toEvents(),
            updatingHeaderIndexes = updating.items.getHeaderIndexes(),
            isLoadingUpdating = updating.isLoading,
            // KMK <--
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds),
            State(selectedYearMonth = selectedYearMonth.value),
        )

    init {
        // KMK -->
        viewModelScope.launch {
            val items = getUpcomingManga.updatingMangas().toUpcomingUIModels()
            updatingState.update { UpdatingState(isLoading = false, items = items) }
        }
        // KMK <--
    }

    private fun List<Manga>.toUpcomingUIModels(): ImmutableList<UpcomingUIModel> {
        var mangaCount = 0
        return fastMap { UpcomingUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) mangaCount++

                val beforeDate = before?.manga?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.manga?.expectedNextUpdate?.toLocalDate()

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingUIModel.Header(afterDate, mangaCount).also { mangaCount = 0 }
                } else {
                    null
                }
            }
            .toImmutableList()
    }

    private fun List<UpcomingUIModel>.toEvents(): ImmutableMap<LocalDate, Int> =
        filterIsInstance<UpcomingUIModel.Header>()
            .associate { it.date to it.mangaCount }
            .toImmutableMap()

    private fun List<UpcomingUIModel>.getHeaderIndexes(): ImmutableMap<LocalDate, Int> =
        fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingUIModel.Header) upcomingUIModel.date to index else null
        }
            .toMap()
            .toImmutableMap()

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        selectedYearMonth.update { yearMonth }
    }

    // KMK -->
    val restriction by lazy { libraryPreferences.autoUpdateMangaRestrictions().get() }

    fun showUpdatingMangas() {
        updatingState.update { it.copy(isShowing = true) }
    }

    fun hideUpdatingMangas() {
        updatingState.update { it.copy(isShowing = false) }
    }

    private data class UpdatingState(
        val isShowing: Boolean = false,
        val isLoading: Boolean = true,
        val items: ImmutableList<UpcomingUIModel> = persistentListOf(),
    )
    // KMK <--

    data class State(
        val selectedYearMonth: YearMonth = YearMonth.now(),
        val items: ImmutableList<UpcomingUIModel> = persistentListOf(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        // KMK -->
        val isLoadingUpcoming: Boolean = true,
        val isShowingUpdatingMangas: Boolean = false,
        val updatingItems: ImmutableList<UpcomingUIModel> = persistentListOf(),
        val updatingEvents: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val updatingHeaderIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val isLoadingUpdating: Boolean = true,
        // KMK <--
    )
}
